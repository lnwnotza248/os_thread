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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Multi-threaded file download client.
 *
 * Usage:
 *   java FileClient <host> <port> list
 *   java FileClient <host> <port> info <filename>
 *   java FileClient <host> <port> download <filename> <destPath> <workers> <traditional|zerocopy>
 *
 * Compile: javac -d out src/FileClient.java
 */
public class FileClient {

    // สรุปผลดาวน์โหลด: ขนาด เวลา ความเร็ว และการตรวจสอบ SHA-256
    public record DownloadResult(long sizeBytes, double elapsedMs, double mbPerSec,
                                 String sha256, String sourceSha256, boolean hashMatches) {}

    // เก็บขนาดและ SHA-256 ที่ได้จากคำสั่ง INFO ของ Server
    private record FileInfo(long sizeBytes, String sha256) {}

    public static void main(String[] args) throws Exception {
        // ตรวจว่ามี host, port และชื่อคำสั่งอย่างน้อย
        if (args.length < 3) {
            printUsage();
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String command = args[2].toUpperCase();

        // เลือกคำสั่งที่ต้องทำ และตรวจจำนวน argument ให้ตรงกับคำสั่งนั้น
        switch (command) {
            case "LIST" -> doList(host, port);
            case "INFO" -> {
                if (args.length != 4) { printUsage(); return; }
                doInfo(host, port, args[3]);
            }
            case "DOWNLOAD" -> {
                if (args.length != 7) { printUsage(); return; }
                String remoteFile = args[3];
                Path dest = Path.of(args[4]);
                int workers = Integer.parseInt(args[5]);
                String mode = args[6].toUpperCase();
                DownloadResult r = download(host, port, remoteFile, dest, workers, mode);
                // แสดงสถิติและผลเปรียบเทียบ hash เมื่อดาวน์โหลดเสร็จ
                System.out.printf("Downloaded %d bytes in %.2f ms (%.2f MB/s) mode=%s workers=%d%n",
                        r.sizeBytes(), r.elapsedMs(), r.mbPerSec(), mode, workers);
                System.out.println("Source SHA-256:     " + r.sourceSha256());
                System.out.println("Downloaded SHA-256: " + r.sha256());
                System.out.println("SHA-256 check: " + (r.hashMatches() ? "PASS" : "FAIL"));
            }
            default -> printUsage();
        }
    }

    private static void printUsage() {
        // แสดงรูปแบบคำสั่งที่รองรับ
        System.out.println("Usage:");
        System.out.println("  java FileClient <host> <port> list");
        System.out.println("  java FileClient <host> <port> info <filename>");
        System.out.println("  java FileClient <host> <port> download <filename> <destPath> <workers> <traditional|zerocopy>");
    }

    private static void doList(String host, int port) throws IOException {
        // ส่ง LIST แล้วอ่านจำนวนรายการและรายละเอียดไฟล์ที่ Server ตอบกลับ
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

    private static void doInfo(String host, int port, String filename) throws IOException {
        // ขอและแสดงข้อมูลของไฟล์หนึ่งรายการจาก Server
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println(in.readLine());
        }
    }

    private static FileInfo getFileInfo(String host, int port, String filename) throws IOException {
        // ขอขนาดและ hash ซึ่งต้องใช้เตรียมไฟล์และตรวจผลดาวน์โหลด
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(ch)) {
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String resp = in.readLine();
            if (resp == null || !resp.startsWith("SIZE")) {
                throw new IOException("INFO failed: " + resp);
            }
            // แยกคำตอบรูปแบบ SIZE <bytes> SHA256 <hash>
            String[] fields = resp.split("\\s+");
            if (fields.length != 4 || !"SHA256".equals(fields[2])) {
                throw new IOException("Invalid INFO response: " + resp);
            }
            return new FileInfo(Long.parseLong(fields[1]), fields[3]);
        }
    }

    /** Downloads remoteFile into dest using `workers` concurrent connections, each with its own byte range. */
    public static DownloadResult download(String host, int port, String remoteFile, Path dest,
                                           int workers, String mode) throws Exception {
        // อ่านขนาดและ hash ต้นฉบับจาก Server ก่อนเริ่มแบ่งช่วงดาวน์โหลด
        FileInfo source = getFileInfo(host, port, remoteFile);
        long size = source.sizeBytes();

        // สร้างโฟลเดอร์ปลายทางถ้าจำเป็น แล้วจองขนาดไฟล์ให้เท่ากับต้นฉบับ
        if (dest.getParent() != null) {
            java.nio.file.Files.createDirectories(dest.getParent());
        }
        try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {
            raf.setLength(size);
        }

        // แบ่งไฟล์เป็นช่วงใกล้เคียงกัน โดย worker สุดท้ายรับ bytes ที่เหลือ
        long chunk = workers > 0 ? size / workers : size;
        // เตรียม thread pool และรายการ Future สำหรับรอผลของ worker แต่ละตัว
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, workers));
        List<Future<?>> futures = new ArrayList<>();

        // เริ่มจับเวลาสำหรับช่วงดาวน์โหลด
        long start = System.nanoTime();

        for (int i = 0; i < workers; i++) {
            // คำนวณตำแหน่งเริ่มและความยาวของช่วงที่ worker นี้รับผิดชอบ
            long offset = i * chunk;
            long length = (i == workers - 1) ? (size - offset) : chunk;
            final long off = offset, len = length;
            futures.add(pool.submit(() -> {
                try {
                    // แต่ละ worker เรียกดาวน์โหลดช่วงของตัวเองผ่าน connection แยก
                    downloadRange(host, port, remoteFile, dest, off, len, mode);
                } catch (IOException e) {
                    throw new RuntimeException("Worker failed at offset " + off, e);
                }
            }));
        }

        // รอ worker ทุกตัว; get() จะส่ง error ต่อหากงานใดทำไม่สำเร็จ
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        // คำนวณเวลาที่ใช้และ throughput หน่วย MiB/s
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        double mbps = (size / 1024.0 / 1024.0) / (ms / 1000.0);

        // ตรวจความถูกต้องด้วยการเทียบ hash ของไฟล์ปลายทางกับค่าจาก Server
        String downloadedHash = sha256(dest);
        boolean hashMatches = downloadedHash.equalsIgnoreCase(source.sha256());
        return new DownloadResult(size, ms, mbps, downloadedHash,
                source.sha256(), hashMatches);
    }

    private static void downloadRange(String host, int port, String remoteFile, Path dest,
                                       long offset, long length, String mode) throws IOException {
        // ข้ามช่วงที่ไม่มีข้อมูล
        if (length == 0) return;
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port))) {
            // ขอช่วงข้อมูลที่ระบุด้วย GET <file> <offset> <length> <mode>
            String request = "GET " + remoteFile + " " + offset + " " + length + " " + mode + "\n";
            OutputStream reqOut = Channels.newOutputStream(ch);
            reqOut.write(request.getBytes(StandardCharsets.UTF_8));
            reqOut.flush();

            InputStream rawIn = Channels.newInputStream(ch);
            // อ่าน header และตรวจสอบว่าขนาดที่ Server จะส่งตรงกับคำขอ
            String header = readLine(rawIn);
            if (header == null || !header.startsWith("OK")) {
                throw new IOException("GET failed for offset " + offset + ": " + header);
            }
            long declaredLength = Long.parseLong(header.split("\\s+")[1]);
            if (declaredLength != length) {
                throw new IOException("Length mismatch: expected " + length + ", got " + declaredLength);
            }

            // zerocopy รับข้อมูลจาก socket ลงไฟล์ที่ offset โดยใช้ FileChannel
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
                // traditional อ่านผ่าน byte buffer แล้วเขียนลงไฟล์ตาม offset
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

    /** Reads a single '\n'-terminated line of raw bytes without over-buffering the following binary payload. */
    private static String readLine(InputStream in) throws IOException {
        // อ่านทีละ byte เพื่อไม่ให้ buffer อ่าน binary payload ถัดจาก header ล่วงหน้า
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (buf.size() == 0 && b == -1) return null;
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String sha256(Path file) throws Exception {
        // อ่านไฟล์ทีละ block เพื่อคำนวณ hash โดยไม่โหลดทั้งไฟล์เข้า memory
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = java.nio.file.Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) != -1) md.update(buffer, 0, n);
        }
        // แปลงผลลัพธ์ hash เป็นเลขฐานสิบหกสำหรับแสดงผลและเปรียบเทียบ
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
/*Client ทำหน้าที่ **ขอไฟล์จาก Server แล้วรับข้อมูลมาเขียนลงเครื่องเรา** โดยทำงานคร่าว ๆ ดังนี้ครับ:

1. **เชื่อมต่อ Server** — ใช้ host และ port ที่ระบุ สามารถขอรายการไฟล์ด้วย `LIST` หรือขอขนาดด้วย `INFO`
2. **เตรียมดาวน์โหลด** — ขอขนาดไฟล์จาก Server แล้วสร้างไฟล์ปลายทางให้มีขนาดเท่ากัน
3. **แบ่งงานให้ workers** — แบ่งไฟล์เป็นช่วงไม่ซ้อนกันตามจำนวน workers โดย worker สุดท้ายรับส่วนที่เหลือ
4. **ดาวน์โหลดพร้อมกัน** — แต่ละ worker เปิด connection ของตัวเอง ส่ง `GET` พร้อมชื่อไฟล์, offset, length และโหมด
5. **เขียนตามตำแหน่ง** — Traditional ใช้ buffer กับ `RandomAccessFile` ส่วน Zero-copy ใช้ `transferFrom()` เขียนลงไฟล์เดียวกันตาม offset จึงไม่ต้องรวมไฟล์ภายหลัง
6. **รอทุก worker เสร็จ** — แสดงเวลาดาวน์โหลด, throughput และ SHA-256 ของไฟล์ปลายทาง

ในโค้ดชุดนี้ SHA-256 **ยังแค่คำนวณและแสดงค่า ไม่ได้เปรียบเทียบกับไฟล์ต้นฉบับอัตโนมัติ */
