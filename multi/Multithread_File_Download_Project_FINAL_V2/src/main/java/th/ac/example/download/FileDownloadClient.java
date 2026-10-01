package th.ac.example.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FileDownloadClient {
    private final String host;
    private final int port;
    private final int connectTimeoutMs;

    public FileDownloadClient(String host, int port, int connectTimeoutMs) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be 1..65535");
        if (connectTimeoutMs < 1) throw new IllegalArgumentException("timeout must be > 0");
        this.host = host; this.port = port; this.connectTimeoutMs = connectTimeoutMs;
    }

    public List<String> listFiles() throws IOException {
        try (Socket socket = connect()) {
            PrintWriter out = Protocol.writer(socket.getOutputStream());
            Protocol.LineReader in = Protocol.lineReader(socket.getInputStream());
            out.println("LIST"); out.flush();
            String first = in.readLine();
            if (first == null) throw new IOException("server closed connection");
            if (first.startsWith("ERROR ")) throw new IOException(first);
            if (!first.startsWith("OK LIST ")) throw new IOException("unexpected LIST response: " + first);
            int expectedCount;
            try { expectedCount = Integer.parseInt(first.substring("OK LIST ".length()).trim()); }
            catch (NumberFormatException e) { throw new IOException("bad LIST count"); }
            if (expectedCount < 0) throw new IOException("bad LIST count");
            List<String> files = new ArrayList<>();
            boolean ended = false;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("END")) { ended = true; break; }
                if (!line.startsWith("FILE ")) throw new IOException("bad LIST item: " + line);
                String rest = line.substring(5);
                int split = rest.lastIndexOf(' ');
                if (split <= 0) throw new IOException("bad LIST item: " + line);
                long size;
                try { size = Long.parseLong(rest.substring(split + 1)); } catch (NumberFormatException e) { throw new IOException("bad LIST size", e); }
                if (size < 0) throw new IOException("bad LIST size");
                files.add(rest.substring(0, split));
            }
            if (!ended || files.size() != expectedCount) throw new IOException("incomplete LIST response");
            return files;
        }
    }

    public long info(String filename) throws IOException {
        try (Socket socket = connect()) {
            PrintWriter out = Protocol.writer(socket.getOutputStream());
            Protocol.LineReader in = Protocol.lineReader(socket.getInputStream());
            out.println("INFO " + filename); out.flush();
            String line = in.readLine();
            if (line == null) throw new IOException("server closed connection");
            if (line.startsWith("ERROR ")) throw new IOException(line);
            String[] p = line.split(" ");
            if (p.length != 3 || !p[0].equals("OK") || !p[1].equals("SIZE")) throw new IOException("unexpected INFO response: " + line);
            try {
                long size = Long.parseLong(p[2]);
                if (size < 0) throw new IOException("bad INFO size");
                return size;
            } catch (NumberFormatException e) {
                throw new IOException("bad INFO size", e);
            }
        }
    }

    public DownloadResult download(String filename, Path output, int workers) throws Exception {
        if (output == null) throw new IllegalArgumentException("output is required");
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null) throw new IOException("output must be a file path");
        Files.createDirectories(parent);

        long start = System.nanoTime();
        long size = info(filename);
        List<DownloadRange> ranges = RangeSplitter.split(size, workers);
        Path temp = Files.createTempFile(parent, "." + absoluteOutput.getFileName() + ".", ".part");
        boolean success = false;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            try (FileChannel init = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if (size > 0) init.position(size - 1).write(ByteBuffer.wrap(new byte[]{0}));
            }
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                int workerId = i;
                DownloadRange range = ranges.get(i);
                futures.add(pool.submit(() -> downloadRange(filename, temp, range, workerId)));
            }
            long total = 0;
            try {
                for (Future<Long> f : futures) total += f.get();
            } catch (ExecutionException e) {
                for (Future<Long> f : futures) f.cancel(true);
                throw unwrap(e);
            }
            if (Files.size(temp) != size) throw new IOException("output size mismatch");
            String hash = FileUtil.sha256(temp);
            moveIntoPlace(temp, absoluteOutput);
            success = true;
            long elapsed = System.nanoTime() - start;
            return new DownloadResult(total, elapsed, hash);
        } finally {
            pool.shutdownNow();
            try { pool.awaitTermination(Math.max(1, connectTimeoutMs / 1000L + 1), TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (!success) Files.deleteIfExists(temp);
        }
    }

    private static void moveIntoPlace(Path temp, Path output) throws IOException {
        try {
            Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private long downloadRange(String filename, Path output, DownloadRange range, int workerId) throws Exception {
        try (Socket socket = connect();
             InputStream rawIn = socket.getInputStream();
             Protocol.LineReader in = Protocol.lineReader(rawIn);
             FileChannel out = FileChannel.open(output, StandardOpenOption.WRITE)) {
            socket.setTcpNoDelay(true);
            PrintWriter writer = Protocol.writer(socket.getOutputStream());
            writer.printf("GET %s %d %d%n", filename, range.offset(), range.length());
            writer.flush();
            String header = in.readLine();
            if (header == null) throw new IOException("worker " + workerId + ": server closed connection");
            if (header.startsWith("ERROR ")) throw new IOException("worker " + workerId + ": " + header);
            String prefix = "OK DATA ";
            if (!header.startsWith(prefix)) throw new IOException("worker " + workerId + ": bad GET response " + header);
            long expected = Long.parseLong(header.substring(prefix.length()).trim());
            if (expected != range.length()) throw new IOException("worker " + workerId + ": server returned unexpected length");
            byte[] buffer = new byte[64 * 1024];
            long remaining = expected;
            long position = range.offset();
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int n = rawIn.read(buffer, 0, want);
                if (n < 0) throw new IOException("worker " + workerId + ": unexpected EOF");
                ByteBuffer bb = ByteBuffer.wrap(buffer, 0, n);
                while (bb.hasRemaining()) out.write(bb, position);
                position += n;
                remaining -= n;
            }
            return expected;
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(connectTimeoutMs);
        return socket;
    }

    private static Exception unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause instanceof Exception ex ? ex : new Exception(cause);
    }
}
