import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-threaded file download server.
 *
 * Protocol (text header + optional binary payload, one request per line):
 *   LIST                                   -> OK <n>\n  then n lines: FILE <name> <size>\n
 *   INFO <filename>                        -> SIZE <bytes>\n  or  ERROR <code> <message>\n
 *   GET <filename> <offset> <length> <mode>-> OK <length>\n followed by <length> raw bytes
 *                                              or ERROR <code> <message>\n
 *   mode = TRADITIONAL | ZEROCOPY (selects the transfer implementation used by the server)
 *
 * Compile: javac -d out src/FileServer.java
 * Run:     java -cp out FileServer [port] [rootDir]
 */
public class FileServer {

    private static Path rootDir;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        rootDir = Path.of(args.length > 1 ? args[1] : "shared_files").toAbsolutePath().normalize(); //ใช้ argument ที่สอง หรือใช้ "shared_files" เป็นค่าเริ่มต้น toAbsolutePath() เปลี่ยนเป็น path เต็ม โดยอิงจากโฟลเดอร์ที่รันโปรแกรม normalize() จัดรูป path เช่น folder/../shared_files ให้กระชับ
        Files.createDirectories(rootDir);// สร้างโฟลเดอร์ rootDir หากยังไม่มี

        // Swap for Executors.newVirtualThreadPerTaskExecutor() on Java 21+ if desired.
        ExecutorService pool = Executors.newFixedThreadPool(64);// สร้าง thread pool ขนาด 64 เธรดสำหรับจัดการ client connections

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {// เปิด server socket channel สำหรับรอ client connections
            serverChannel.bind(new InetSocketAddress(port));// ผูก server socket channel กับพอร์ตที่ระบุ
            System.out.println("FileServer listening on port " + port + ", serving " + rootDir);// แสดงข้อความว่า server เริ่มทำงานแล้ว

            while (true) {
                SocketChannel client = serverChannel.accept();// รอ client connection ใหม่
                pool.submit(() -> handleClient(client));// ส่ง client connection ไปให้ thread pool จัดการ
            }
        } finally {
            pool.shutdown();// ปิด thread pool เมื่อ server ถูกปิด
        }
    }

    private static void handleClient(SocketChannel channel) {// channel คือ connection ที่ได้จาก serverChannel.accept() ใช้รับส่งข้อมูลกับ Client คนนั้นโดยเฉพาะ
        try (SocketChannel ch = channel;// ch อ้างถึง connection เดิม ไม่ได้เปิด connection ใหม่
             InputStream rawIn = Channels.newInputStream(ch);// rawIn ใช้อ่าน bytes ที่ Client ส่งมา
             OutputStream out = Channels.newOutputStream(ch)) {// out ใช้ส่ง bytes กลับไปหา Client

            BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));//
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                handleCommand(line, out, ch);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected/error: " + e.getMessage());
        }
    }

    private static void handleCommand(String line, OutputStream out, SocketChannel ch) throws IOException {
        String[] parts = line.trim().split("\\s+");
        String cmd = parts[0].toUpperCase();
        switch (cmd) {
            case "LIST" -> handleList(out);
            case "INFO" -> handleInfo(parts, out);
            case "GET" -> handleGet(parts, out, ch);
            default -> sendError(out, 400, "Unknown command: " + cmd);
        }
    }

    private static void handleList(OutputStream out) throws IOException {
        List<Path> files;
        try (var stream = Files.list(rootDir)) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("OK ").append(files.size()).append('\n');
        for (Path p : files) {
            sb.append("FILE ").append(p.getFileName()).append(' ').append(Files.size(p)).append('\n');
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void handleInfo(String[] parts, OutputStream out) throws IOException {
        if (parts.length != 2) {
            sendError(out, 400, "Usage: INFO <filename>");
            return;
        }
        Path file = resolveSafe(parts[1]);
        if (file == null || !Files.isRegularFile(file)) {
            sendError(out, 404, "File not found");
            return;
        }
        out.write(("SIZE " + Files.size(file) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void handleGet(String[] parts, OutputStream out, SocketChannel ch) throws IOException {
        if (parts.length != 5) {
            sendError(out, 400, "Usage: GET <filename> <offset> <length> <mode>");
            return;
        }
        Path file = resolveSafe(parts[1]);
        long offset, length;
        try {
            offset = Long.parseLong(parts[2]);
            length = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            sendError(out, 400, "Invalid offset/length");
            return;
        }
        String mode = parts[4].toUpperCase();

        if (file == null || !Files.isRegularFile(file)) {
            sendError(out, 404, "File not found");
            return;
        }
        long size = Files.size(file);
        if (offset < 0 || length < 0 || offset + length > size) {
            sendError(out, 416, "Range not satisfiable");
            return;
        }

        out.write(("OK " + length + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             FileChannel fc = raf.getChannel()) {

            if ("ZEROCOPY".equals(mode)) {
                long position = offset;
                long remaining = length;
                while (remaining > 0) {
                    long n = fc.transferTo(position, remaining, ch);
                    if (n <= 0) throw new IOException("transferTo made no progress");
                    position += n;
                    remaining -= n;
                }
            } else {
                fc.position(offset);
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                long remaining = length;
                while (remaining > 0) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), remaining));
                    int n = fc.read(buffer);
                    if (n < 0) throw new EOFException("Unexpected EOF while reading " + file);
                    out.write(buffer.array(), 0, n);
                    remaining -= n;
                }
                out.flush();
            }
        }
    }

    private static void sendError(OutputStream out, int code, String message) throws IOException {
        out.write(("ERROR " + code + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Resolves a client-supplied filename inside rootDir, rejecting path traversal. */
    private static Path resolveSafe(String name) {
        Path candidate = rootDir.resolve(name).normalize();
        if (!candidate.startsWith(rootDir)) return null;
        return candidate;
    }
}
/*Server ทำหน้าที่ **รอรับคำขอและส่งไฟล์ให้ Client ผ่าน TCP** โดยทำงานคร่าว ๆ ดังนี้ครับ:

1. **เตรียมระบบ** — กำหนด port และโฟลเดอร์แชร์ไฟล์ แล้วสร้าง thread pool สูงสุด 64 threads
2. **รอ Client เชื่อมต่อ** — เมื่อมี connection เข้ามา จะมอบให้ thread ใน pool จัดการ ส่วน thread หลักกลับไปรอรับ connection ใหม่
3. **อ่านคำสั่งจาก Client**
   - `LIST` → ส่งรายชื่อและขนาดไฟล์
   - `INFO` → ส่งขนาดไฟล์ที่ต้องการ
   - `GET` → ส่งข้อมูลเฉพาะช่วง `offset` และ `length` ที่ร้องขอ
4. **ส่งข้อมูลตามโหมด** — Traditional อ่านผ่าน buffer แล้วส่ง หรือ Zero-copy ใช้ `transferTo()`
5. **จัดการข้อผิดพลาดและปิด connection** — ส่ง `ERROR` เมื่อคำขอไม่ถูกต้อง และปิดทรัพยากรเมื่อ Client จบการเชื่อมต่อ

**Client เป็นคนแบ่งไฟล์เป็น 10 ช่วง** ส่วน Server รับคำขอแต่ละช่วงแล้วส่งให้พร้อมกันผ่านหลาย connections */