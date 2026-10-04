import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Multi-threaded file download client.
 *
 * Client นี้ใช้คำสั่ง LIST/INFO เพื่อดูไฟล์ แล้วดาวน์โหลดไฟล์เป็นหลายช่วง byte
 * ผ่าน connections แยกกัน พร้อมรายงานเวลา ความเร็ว และ SHA-256 ของไฟล์ที่ได้
 *
 * Usage:
 *   java FileClient <host> <port> list
 *   java FileClient <host> <port> info <filename>
 *   java FileClient <host> <port> download <filename> <destPath> <workers> <traditional|zerocopy>
 *
 * Compile: javac -d out src/FileClient.java
 */
public class FileClient {

    // เก็บผลการดาวน์โหลดที่ใช้แสดงสรุปและเปรียบเทียบการทดสอบ
    public record DownloadResult(long sizeBytes, double elapsedMs, double mbPerSec,
                                 String sha256, String expectedSha256, boolean sha256Matches) {}

    // แปล argument ของ command line แล้วเรียกการทำงานตามคำสั่งที่เลือก
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String command = args[2].toUpperCase();

        switch (command) {
            case "LIST" -> doList(host, port);
            case "INFO" -> {
                if (args.length != 4) { printUsage(); return; }
                doInfo(host, port, args[3]);
            }
            case "HASH" -> {
                if (args.length != 4) { printUsage(); return; }
                System.out.println("SHA-256: " + getRemoteSha256(host, port, args[3]));
            }
            case "DOWNLOAD" -> {
                if (args.length != 7) { printUsage(); return; }
                String remoteFile = args[3];
                Path dest = Path.of(args[4]);
                int workers = Integer.parseInt(args[5]);
                String mode = args[6].toUpperCase();
                DownloadResult r = download(host, port, remoteFile, dest, workers, mode);
                System.out.printf("Downloaded %d bytes in %.2f ms (%.2f MiB/s) mode=%s workers=%d%n",
                        r.sizeBytes(), r.elapsedMs(), r.mbPerSec(), mode, workers);
                System.out.println("SHA-256:          " + r.sha256());
                System.out.println("Expected SHA-256: " + r.expectedSha256());
                System.out.println("SHA-256 match:    " + (r.sha256Matches() ? "YES" : "NO"));
            }
            default -> printUsage();
        }
    }

    // ขอค่า SHA-256 ของไฟล์ต้นฉบับจาก Server เพื่อใช้เทียบกับไฟล์ที่ดาวน์โหลด
    private static String getRemoteSha256(String host, int port, String filename) throws IOException {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             InputStream in = Channels.newInputStream(ch);
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write(("HASH " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String response = readLine(in);
            if (response == null || !response.startsWith("SHA256 ")) {
                throw new IOException("HASH failed: " + response);
            }
            return response.substring("SHA256 ".length()).trim();
        }
    }

    // แสดงรูปแบบ argument ที่โปรแกรมรองรับ
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java FileClient <host> <port> list");
        System.out.println("  java FileClient <host> <port> info <filename>");
        System.out.println("  java FileClient <host> <port> hash <filename>");
        System.out.println("  java FileClient <host> <port> download <filename> <destPath> <workers> <traditional|zerocopy>");
    }

    // ส่ง LIST ไปยัง Server แล้วอ่าน header และบรรทัดข้อมูลไฟล์ตามจำนวนที่แจ้ง
    private static void doList(String host, int port) throws IOException {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write("LIST\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String header = in.readLine();
            System.out.println(header);
            if (header != null && header.startsWith("OK")) {
                int count = Integer.parseInt(header.split("\\s+")[1]);
                for (int i = 0; i < count; i++) {
                    System.out.println(in.readLine());
                }
            }
        }
    }

    // ขอขนาดไฟล์จาก Server แล้วแสดงผลตอบกลับ
    private static void doInfo(String host, int port, String filename) throws IOException {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println(in.readLine());
        }
    }

    // ขอขนาดไฟล์และตรวจรูปแบบ response ก่อนนำไปใช้คำนวณช่วงดาวน์โหลด
    private static long getSize(String host, int port, String filename) throws IOException {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp = in.readLine();
            if (resp == null || !resp.startsWith("SIZE")) {
                throw new IOException("INFO failed: " + resp);
            }
            return Long.parseLong(resp.split("\\s+")[1]);
        }
    }

    /**
     * ดาวน์โหลดไฟล์โดยแบ่งเป็นช่วง byte ไม่ซ้อนกัน ส่งแต่ละช่วงให้ worker
     * คนละ connection จากนั้นรอทุก worker และคำนวณสถิติของไฟล์ปลายทาง
     */
    public static DownloadResult download(String host, int port, String remoteFile, Path dest,
                                           int workers, String mode) throws Exception {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be greater than zero");
        }
        long size = getSize(host, port, remoteFile);
        String expectedSha256 = getRemoteSha256(host, port, remoteFile);

        if (dest.getParent() != null) {
            java.nio.file.Files.createDirectories(dest.getParent());
        }
        try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {
            raf.setLength(size);
        }

        long chunk = workers > 0 ? size / workers : size;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, workers));
        List<Future<?>> futures = new ArrayList<>();

        // จับเวลาเฉพาะช่วงทำงานดาวน์โหลด ไม่รวมการขอขนาดและเตรียมไฟล์ปลายทาง
        long start = System.nanoTime();

        // แบ่งช่วงเท่า ๆ กัน โดยให้ worker สุดท้ายรับ byte ที่เหลือจากการหาร
        try {
            for (int i = 0; i < workers; i++) {
                long offset = i * chunk;
                long length = (i == workers - 1) ? (size - offset) : chunk;
                final long off = offset, len = length;
                futures.add(pool.submit(() -> {
                    try {
                        downloadRange(host, port, remoteFile, dest, off, len, mode);
                    } catch (IOException e) {
                        throw new RuntimeException("Worker failed at offset " + off, e);
                    }
                }));
            }

            for (Future<?> f : futures) f.get();
        } catch (Exception e) {
            boolean wasInterrupted = e instanceof InterruptedException;
            for (Future<?> future : futures) {
                future.cancel(true);
            }
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    e.addSuppressed(new IOException("Download workers did not stop after cancellation"));
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                e.addSuppressed(interruption);
                wasInterrupted = true;
            }
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
        pool.shutdown();

        double ms = (System.nanoTime() - start) / 1_000_000.0;
        double mbps = (size / 1024.0 / 1024.0) / (ms / 1000.0);

        // คำนวณ SHA-256 ของไฟล์ปลายทางเพื่อให้ใช้ตรวจสอบ/เปรียบเทียบผลได้
        String actualSha256 = sha256(dest);
        return new DownloadResult(size, ms, mbps, actualSha256, expectedSha256,
                actualSha256.equalsIgnoreCase(expectedSha256));
    }

    // ดาวน์โหลดช่วงเดียว: ส่ง GET, ตรวจ header แล้วเขียนข้อมูลตรงตำแหน่ง offset
    private static void downloadRange(String host, int port, String remoteFile, Path dest,
                                       long offset, long length, String mode) throws IOException {
        if (length == 0) return;
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port))) {
            String request = "GET " + remoteFile + " " + offset + " " + length + " " + mode + "\n";
            OutputStream reqOut = Channels.newOutputStream(ch);
            reqOut.write(request.getBytes(StandardCharsets.UTF_8));
            reqOut.flush();

            InputStream rawIn = Channels.newInputStream(ch);
            String header = readLine(rawIn);
            if (header == null || !header.startsWith("OK")) {
                throw new IOException("GET failed for offset " + offset + ": " + header);
            }
            long declaredLength = Long.parseLong(header.split("\\s+")[1]);
            if (declaredLength != length) {
                throw new IOException("Length mismatch: expected " + length + ", got " + declaredLength);
            }

            if ("ZEROCOPY".equalsIgnoreCase(mode)) {
                try (FileChannel destChannel = FileChannel.open(dest, StandardOpenOption.WRITE)) {
                    long position = offset;
                    long remaining = length;
                    while (remaining > 0) {
                        long n = destChannel.transferFrom(ch, position, remaining);
                        if (n <= 0) throw new IOException("transferFrom made no progress");
                        position += n;
                        remaining -= n;
                    }
                }
            } else {
                try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {
                    raf.seek(offset);
                    byte[] buffer = new byte[64 * 1024];
                    long remaining = length;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int n = rawIn.read(buffer, 0, toRead);
                        if (n < 0) throw new EOFException("Unexpected EOF while downloading " + remoteFile);
                        raf.write(buffer, 0, n);
                        remaining -= n;
                    }
                }
            }
        }
    }

    /**
     * อ่าน header จนถึง newline ทีละ byte เพื่อไม่ให้ BufferedReader อ่านล่วงหน้า
     * และกลืน byte แรก ๆ ของ binary payload ที่ตามมา
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (buf.size() == 0 && b == -1) return null;
        return buf.toString(StandardCharsets.UTF_8);
    }

    // อ่านไฟล์ปลายทางเป็น block แล้วแปลง digest เป็นเลขฐานสิบหก
    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) != -1) md.update(buffer, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
