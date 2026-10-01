package th.ac.example.download;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileDownloadServer implements AutoCloseable {
    private final Path rootDirectory;
    private final int requestedPort;
    private final DownloadMode mode;
    private final int maxConnections;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocketChannel serverChannel;
    private Thread acceptThread;

    public FileDownloadServer(Path rootDirectory, int port, DownloadMode mode, int maxConnections) {
        if (rootDirectory == null) throw new IllegalArgumentException("rootDirectory is required");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be 0..65535");
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (maxConnections < 1 || maxConnections > 1000) throw new IllegalArgumentException("maxConnections must be 1..1000");
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.requestedPort = port;
        this.mode = mode;
        this.maxConnections = maxConnections;
        this.workers = Executors.newFixedThreadPool(maxConnections);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        try {
            Files.createDirectories(rootDirectory);
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(requestedPort));
            acceptThread = new Thread(this::acceptLoop, "server-acceptor");
            acceptThread.start();
        } catch (IOException | RuntimeException e) {
            running.set(false);
            if (serverChannel != null) {
                try { serverChannel.close(); } catch (IOException ignored) {}
                serverChannel = null;
            }
            throw e;
        }
    }

    public int port() {
        if (serverChannel == null) throw new IllegalStateException("server is not started");
        try {
            return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel channel = serverChannel.accept();
                try {
                    workers.submit(() -> handle(channel));
                } catch (RejectedExecutionException e) {
                    try { channel.close(); } catch (IOException ignored) {}
                    if (running.get()) System.err.println("[server] worker pool rejected connection");
                    break;
                }
            } catch (IOException e) {
                if (running.get()) System.err.println("[server] accept error: " + e.getMessage());
                break;
            }
        }
    }

    private void handle(SocketChannel channel) {
        try (SocketChannel sc = channel;
             Protocol.LineReader in = Protocol.lineReader(sc.socket().getInputStream());
             PrintWriter out = Protocol.writer(sc.socket().getOutputStream())) {
            sc.socket().setTcpNoDelay(true);
            final String request;
            try {
                request = in.readLine();
            } catch (IOException e) {
                Protocol.sendError(out, "BAD_REQUEST", e.getMessage());
                return;
            }
            if (request == null) return;
            String trimmed = request.trim();
            if (trimmed.equals("LIST")) {
                handleList(out);
            } else if (trimmed.matches("INFO\\s+.+")) {
                handleInfo(trimmed.substring(5).trim(), out);
            } else if (trimmed.matches("GET\\s+.+\\s+-?\\d+\\s+-?\\d+")) {
                handleGet(trimmed, sc, out);
            } else {
                Protocol.sendError(out, "BAD_REQUEST", "invalid command");
            }
        } catch (Exception e) {
            System.err.println("[server] handler error: " + e.getMessage());
        }
    }

    private void handleList(PrintWriter out) {
        try (var stream = Files.list(rootDirectory)) {
            List<String> entries = new java.util.ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        try { FileUtil.safeResolve(rootDirectory, p.getFileName().toString()); return true; }
                        catch (IOException e) { return false; }
                    })
                    .sorted()
                    .forEach(p -> {
                        try { entries.add("FILE " + p.getFileName() + " " + Files.size(p)); }
                        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                    });
            out.printf("OK LIST %d%n", entries.size());
            for (String entry : entries) out.println(entry);
            out.println("END");
            out.flush();
        } catch (java.io.UncheckedIOException e) {
            Protocol.sendError(out, "SERVER_ERROR", "cannot list files");
        } catch (IOException e) {
            Protocol.sendError(out, "SERVER_ERROR", "cannot list files");
        }
    }

    private void handleInfo(String filename, PrintWriter out) {
        try {
            Path file = FileUtil.safeResolve(rootDirectory, filename);
            if (!Files.isRegularFile(file)) {
                Protocol.sendError(out, "NOT_FOUND", "file not found");
                return;
            }
            try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
                out.printf("OK SIZE %d%n", fileChannel.size());
                out.flush();
            }
        } catch (java.nio.file.NoSuchFileException e) {
            Protocol.sendError(out, "NOT_FOUND", "file not found");
        } catch (IOException e) {
            Protocol.sendError(out, "BAD_REQUEST", e.getMessage());
        }
    }

    private void handleGet(String request, SocketChannel channel, PrintWriter out) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^GET\\s+(.+?)\\s+(-?\\d+)\\s+(-?\\d+)$")
                .matcher(request);
        if (!m.matches()) {
            Protocol.sendError(out, "BAD_REQUEST", "invalid GET syntax");
            return;
        }
        String filename = m.group(1).trim();
        long offset;
        long length;
        try {
            offset = Long.parseLong(m.group(2));
            length = Long.parseLong(m.group(3));
        } catch (NumberFormatException e) {
            Protocol.sendError(out, "BAD_REQUEST", "invalid number");
            return;
        }
        final Path file;
        try {
            file = FileUtil.safeResolve(rootDirectory, filename);
        } catch (IOException e) {
            Protocol.sendError(out, "BAD_REQUEST", e.getMessage());
            return;
        }

        boolean headerSent = false;
        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = fileChannel.size();
            if (offset < 0 || length < 0 || offset > size || length > size - offset) {
                Protocol.sendError(out, "RANGE_INVALID", "offset/length outside file");
                return;
            }
            out.printf("OK DATA %d%n", length);
            out.flush();
            headerSent = true;
            if (mode == DownloadMode.NIO) {
                sendUsingTransfer(fileChannel, channel, offset, length);
            } else {
                sendUsingTraditional(fileChannel, channel.socket(), offset, length);
            }
        } catch (java.nio.file.NoSuchFileException | java.nio.file.NotDirectoryException e) {
            if (!headerSent) Protocol.sendError(out, "NOT_FOUND", "file not found");
        } catch (Exception e) {
            if (!headerSent) Protocol.sendError(out, "SERVER_ERROR", "download failed");
            else System.err.println("[server] transfer error: " + e.getMessage());
        }
    }

    private static void sendUsingTraditional(FileChannel fileChannel, Socket socket, long offset, long length) throws IOException {
        try (var input = Channels.newInputStream(fileChannel.position(offset));
             OutputStream output = socket.getOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int n = input.read(buffer, 0, want);
                if (n < 0) throw new IOException("unexpected EOF");
                output.write(buffer, 0, n);
                remaining -= n;
            }
            output.flush();
        }
    }

    private static void sendUsingTransfer(FileChannel fileChannel, SocketChannel channel, long offset, long length) throws IOException {
        long remaining = length;
        long position = offset;
        int zeroProgress = 0;
        while (remaining > 0) {
            long sent = fileChannel.transferTo(position, remaining, channel);
            if (sent < 0) throw new IOException("transferTo returned negative value");
            if (sent == 0) {
                zeroProgress++;
                if (zeroProgress >= 20) throw new IOException("transferTo made no progress");
                Thread.yield();
                continue;
            }
            zeroProgress = 0;
            position += sent;
            remaining -= sent;
        }
    }

    @Override
    public void close() {
        running.set(false);
        try { if (serverChannel != null) serverChannel.close(); } catch (IOException ignored) {}
        workers.shutdownNow();
        try { workers.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
