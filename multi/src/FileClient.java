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

    private static final Object PROGRESS_LOCK = new Object();

    // เก็บผลการดาวน์โหลดที่ใช้แสดงสรุปและเปรียบเทียบการทดสอบ
    public record DownloadResult(long sizeBytes, double elapsedMs, double mbPerSec,
                                 String sha256, String expectedSha256, boolean sha256Matches) {}

    // แปล argument ของ command line แล้วเรียกการทำงานตามคำสั่งที่เลือก
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        String host = args[0]; // localhost คือ ip ของเครื่องตัวเอง
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
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port)); // เปิด connection ไปยัง server
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));// in ใช้สำหรับอ่านข้อมูลจาก server ซึ่งคือ รายการไฟล์ที่ server มี
             OutputStream out = Channels.newOutputStream(ch)) {// out ใช้สำหรับส่งคำสั่งไปยัง server 
            out.write("LIST\n".getBytes(StandardCharsets.UTF_8));// ส่งคำสั่ง LIST ไปยัง server
            out.flush(); // flush() เพื่อให้แน่ใจว่าข้อมูลถูกส่งออกไปยัง server ทันที
            String header = in.readLine();// อ่านบรรทัดแรกจาก server ซึ่งเป็น header ที่บอกจำนวนไฟล์ที่ server มี
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
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));// เปิด connection ไปยัง server
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));// in ใช้สำหรับอ่านข้อมูลจาก server ซึ่งคือ ข้อมูลเกี่ยวกับไฟล์ที่ server มี
             OutputStream out = Channels.newOutputStream(ch)) {// out ใช้สำหรับส่งคำสั่งไปยัง server
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println(in.readLine());
        }
    }

    // ขอขนาดไฟล์และตรวจรูปแบบ response ก่อนนำไปใช้คำนวณช่วงดาวน์โหลด
    private static long getSize(String host, int port, String filename) throws IOException {
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));// เปิด connection ไปยัง server
             BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));// in ใช้สำหรับอ่านข้อมูลจาก server ซึ่งคือ ข้อมูลเกี่ยวกับไฟล์ที่ server มี
             OutputStream out = Channels.newOutputStream(ch)) {// out ใช้สำหรับส่งคำสั่งไปยัง server
            out.write(("INFO " + filename + "\n").getBytes(StandardCharsets.UTF_8));// ส่งคำสั่ง INFO ไปยัง server เพื่อขอข้อมูลเกี่ยวกับไฟล์
            out.flush();
            String resp = in.readLine();// อ่านบรรทัดแรกจาก server ซึ่งเป็น response ที่บอกขนาดไฟล์
            if (resp == null || !resp.startsWith("SIZE")) {
                throw new IOException("INFO failed: " + resp);
            }
            return Long.parseLong(resp.split("\\s+")[1]);// แยกขนาดไฟล์จาก response และแปลงเป็น long
        }
    }

    /**
     * ดาวน์โหลดไฟล์โดยแบ่งเป็นช่วง byte ไม่ซ้อนกัน ส่งแต่ละช่วงให้ worker
     * คนละ connection จากนั้นรอทุก worker และคำนวณสถิติของไฟล์ปลายทาง
     */
    // host: ชื่อหรือ ip ของ server, port: พอร์ตของ server, remoteFile: ชื่อไฟล์บน server, dest: path ของไฟล์ปลายทาง, workers: จำนวน thread ที่ใช้ดาวน์โหลด, mode: traditional หรือ zerocopy
    public static DownloadResult download(String host, int port, String remoteFile, Path dest,
                                           int workers, String mode) throws Exception {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be greater than zero");
        }
        long size = getSize(host, port, remoteFile);// ขอขนาดไฟล์จาก server เพื่อใช้ในการแบ่งช่วงดาวน์โหลด
        String expectedSha256 = getRemoteSha256(host, port, remoteFile);// ขอค่า SHA-256 ของไฟล์ต้นฉบับจาก server เพื่อใช้เทียบกับไฟล์ที่ดาวน์โหลด

        if (dest.getParent() != null) {
            java.nio.file.Files.createDirectories(dest.getParent());// สร้าง directory ของไฟล์ปลายทางถ้ายังไม่มี
        }
        try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {// raf ใช้สำหรับเขียนข้อมูลลงในไฟล์ปลายทาง โดยสามารถเขียนได้จากตำแหน่ง offset ที่กำหนด
            raf.setLength(size);// กำหนดขนาดไฟล์ปลายทางให้ตรงกับไฟล์ต้นฉบับ เพื่อให้สามารถเขียนข้อมูลลงในตำแหน่ง offset ได้
        }

        long chunk = workers > 0 ? size / workers : size;// แบ่งขนาดไฟล์เป็นช่วง ๆ สำหรับแต่ละ worker โดย worker สุดท้ายจะรับ byte ที่เหลือจากการหาร
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, workers)); // สร้าง thread pool สำหรับ worker แต่ละตัว
        List<Future<?>> futures = new ArrayList<>();// futures ใช้สำหรับเก็บผลลัพธ์ของแต่ละ worker เพื่อรอให้ทุก worker ทำงานเสร็จ

        // จับเวลาเฉพาะช่วงทำงานดาวน์โหลด ไม่รวมการขอขนาดและเตรียมไฟล์ปลายทาง
        long start = System.nanoTime();

        // แบ่งช่วงเท่า ๆ กัน โดยให้ worker สุดท้ายรับ byte ที่เหลือจากการหาร
        try {
            for (int i = 0; i < workers; i++) {
                long offset = i * chunk;
                long length = (i == workers - 1) ? (size - offset) : chunk;// กำหนด offset และ length สำหรับแต่ละ worker โดย worker สุดท้ายจะรับ byte ที่เหลือจากการหาร
                final long off = offset, len = length;
                final int workerNumber = i + 1;
                futures.add(pool.submit(() -> {// ส่งแต่ละช่วงให้ worker คนละ connection
                    try {
                        downloadRange(host, port, remoteFile, dest, off, len, mode, workerNumber, workers);// เรียกฟังก์ชัน downloadRange เพื่อดาวน์โหลดช่วง byte ที่กำหนด
                    } catch (IOException e) {
                        printWorkerStatus(workerNumber, workers, "failed: " + e.getMessage());
                        throw new RuntimeException("Worker failed at offset " + off, e);
                    }
                }));
            }

            for (Future<?> f : futures) f.get();// รอให้ทุก worker ทำงานเสร็จ
        } catch (Exception e) {
            boolean wasInterrupted = e instanceof InterruptedException;// ตรวจสอบว่าเกิด InterruptedException หรือไม่ เพื่อจัดการกับ thread interruption
            for (Future<?> future : futures) {
                future.cancel(true);// ยกเลิกงานของ worker ที่ยังไม่เสร็จ
            }
            pool.shutdownNow();// ปิด thread pool ทันที
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {// รอให้ thread pool ปิดตัวเองภายใน 30 วินาที
                    e.addSuppressed(new IOException("Download workers did not stop after cancellation"));// ถ้า thread pool ไม่ปิดตัวเองภายใน 30 วินาที ให้เพิ่ม suppressed exception เพื่อแจ้งว่าเกิดปัญหาในการยกเลิกงาน
                }
            } catch (InterruptedException interruption) {// จัดการกับ InterruptedException ที่เกิดขึ้นระหว่างรอให้ thread pool ปิดตัวเอง
                Thread.currentThread().interrupt();// ตั้งสถานะ interrupted ของ thread ปัจจุบันเพื่อให้ thread อื่น ๆ รู้ว่าเกิด interruption
                e.addSuppressed(interruption);// เพิ่ม suppressed exception เพื่อแจ้งว่าเกิดปัญหาในการรอให้ thread pool ปิดตัวเอง
                wasInterrupted = true;
            }
            if (wasInterrupted) {
                Thread.currentThread().interrupt();// ตั้งสถานะ interrupted ของ thread ปัจจุบันเพื่อให้ thread อื่น ๆ รู้ว่าเกิด interruption
            }
            throw e;
        }
        pool.shutdown();

        double ms = (System.nanoTime() - start) / 1_000_000.0;// คำนวณเวลาที่ใช้ในการดาวน์โหลดเป็นมิลลิวินาที
        double mbps = (size / 1024.0 / 1024.0) / (ms / 1000.0);// คำนวณความเร็วในการดาวน์โหลดเป็น MiB/s

        // คำนวณ SHA-256 ของไฟล์ปลายทางเพื่อให้ใช้ตรวจสอบ/เปรียบเทียบผลได้
        String actualSha256 = sha256(dest);// เรียกฟังก์ชัน sha256 เพื่อคำนวณค่า SHA-256 ของไฟล์ปลายทาง
        return new DownloadResult(size, ms, mbps, actualSha256, expectedSha256, // เปรียบเทียบค่า SHA-256 ของไฟล์ปลายทางกับค่า SHA-256 ของไฟล์ต้นฉบับเพื่อให้ทราบว่าตรงกันหรือไม่
                actualSha256.equalsIgnoreCase(expectedSha256));
    }

    // ดาวน์โหลดช่วงเดียว: ส่ง GET, ตรวจ header แล้วเขียนข้อมูลตรงตำแหน่ง offset
    private static void downloadRange(String host, int port, String remoteFile, Path dest,
                                       long offset, long length, String mode,
                                       int workerNumber, int workerCount) throws IOException {
        if (length == 0) {
            printWorkerStatus(workerNumber, workerCount, "skipped (empty range)");
            return;
        }
        printWorkerProgress(workerNumber, workerCount, 0, length, 0);
        int lastReportedPercent = 0;
        try (SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port))) {// เปิด connection ไปยัง server สำหรับดาวน์โหลดช่วง byte ที่กำหนด
            String request = "GET " + remoteFile + " " + offset + " " + length + " " + mode + "\n";// สร้างคำสั่ง GET สำหรับดาวน์โหลดช่วง byte ที่กำหนด โดยระบุชื่อไฟล์, offset, length และ mode (traditional หรือ zerocopy)
            OutputStream reqOut = Channels.newOutputStream(ch);// reqOut ใช้สำหรับส่งคำสั่ง GET ไปยัง server
            reqOut.write(request.getBytes(StandardCharsets.UTF_8));
            reqOut.flush();

            InputStream rawIn = Channels.newInputStream(ch);// rawIn ใช้สำหรับอ่านข้อมูลจาก server
            String header = readLine(rawIn);// อ่าน header จาก server เพื่อดูว่าการดาวน์โหลดสำเร็จหรือไม่ และตรวจสอบความยาวของข้อมูลที่ server ส่งมา
            if (header == null || !header.startsWith("OK")) {// ตรวจสอบว่า header เป็น null หรือไม่ และตรวจสอบว่า header เริ่มต้นด้วย "OK" หรือไม่
                throw new IOException("GET failed for offset " + offset + ": " + header);
            }
            long declaredLength = Long.parseLong(header.split("\\s+")[1]);// แยกความยาวของข้อมูลที่ server ส่งมาจาก header และแปลงเป็น long
            if (declaredLength != length) {
                throw new IOException("Length mismatch: expected " + length + ", got " + declaredLength);
            }

            if ("ZEROCOPY".equalsIgnoreCase(mode)) {
                try (FileChannel destChannel = FileChannel.open(dest, StandardOpenOption.WRITE)) {// เปิด FileChannel สำหรับเขียนข้อมูลลงในไฟล์ปลายทาง
                    long position = offset;// กำหนดตำแหน่งเริ่มต้นในการเขียนข้อมูลลงในไฟล์ปลายทาง
                    long remaining = length;// กำหนดจำนวน byte ที่เหลือในการดาวน์โหลด
                    while (remaining > 0) {
                        long n = destChannel.transferFrom(ch, position, remaining);// ใช้ transferFrom เพื่อคัดลอกข้อมูลจาก SocketChannel ไปยัง FileChannel โดยระบุ position และ remaining
                        if (n <= 0) throw new IOException("transferFrom made no progress");
                        position += n;
                        remaining -= n;
                        lastReportedPercent = printWorkerProgress(workerNumber, workerCount,
                                length - remaining, length, lastReportedPercent);
                    }
                }
            } else {
                try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {//raf ใช้ RandomAccessFile สำหรับเขียนข้อมูลลงในไฟล์ปลายทาง โดยสามารถเขียนได้จากตำแหน่ง offset ที่กำหนด
                    raf.seek(offset);// กำหนดตำแหน่งเริ่มต้นในการเขียนข้อมูลลงในไฟล์ปลายทาง
                    byte[] buffer = new byte[64 * 1024];// สร้าง buffer ขนาด 64 KiB สำหรับอ่านข้อมูลจาก server
                    long remaining = length;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);// กำหนดจำนวน byte ที่จะอ่านในแต่ละครั้ง โดยไม่เกินขนาด buffer และไม่เกินจำนวน byte ที่เหลือ
                        int n = rawIn.read(buffer, 0, toRead);// อ่านข้อมูลจาก server แล้วเก็บลงใน buffer
                        if (n < 0) throw new EOFException("Unexpected EOF while downloading " + remoteFile);
                        raf.write(buffer, 0, n);// เขียนข้อมูลจาก buffer ลงในไฟล์ปลายทาง
                        remaining -= n;
                        lastReportedPercent = printWorkerProgress(workerNumber, workerCount,
                                length - remaining, length, lastReportedPercent);
                    }
                }
            }
        }
    }

    private static int printWorkerProgress(int workerNumber, int workerCount, long completed,
                                           long total, int lastReportedPercent) {
        int percent = (int) (completed * 100.0 / total);
        if (percent == 100 || percent >= lastReportedPercent + 10) {
            synchronized (PROGRESS_LOCK) {
                System.out.printf("Downloading: worker %d/%d - %d%% (%d/%d bytes)%n",
                        workerNumber, workerCount, percent, completed, total);
            }
            return percent;
        }
        return lastReportedPercent;
    }

    private static void printWorkerStatus(int workerNumber, int workerCount, String status) {
        synchronized (PROGRESS_LOCK) {
            System.out.printf("Downloading: worker %d/%d - %s%n", workerNumber, workerCount, status);
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
