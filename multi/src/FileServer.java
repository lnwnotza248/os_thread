import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded file download server.
 *
 * Server นี้เปิด TCP port เพื่อให้ Client ขอรายชื่อไฟล์ ขนาดไฟล์ หรืออ่านข้อมูล
 * ตามช่วง byte ที่กำหนด แต่ละ connection ถูกจัดการแยกกันด้วย thread pool
 *
 * Protocol (text header + optional binary payload, one request per line):
 *   LIST                                   -> OK <n>\n  then n lines: FILE <name> <size>\n
 *   INFO <filename>                        -> SIZE <bytes>\n  or  ERROR <code> <message>\n
 *   GET <filename> <offset> <length> [mode]-> OK <length>\n followed by <length> raw bytes
 *                                              or ERROR <code> <message>\n
 *   mode (optional, default TRADITIONAL) = TRADITIONAL | ZEROCOPY (selects the transfer implementation used by the server)
 *
 * Compile: javac -d src src\*.java
 * Run:     java -cp src FileServer [port] [rootDir] [io|nio]
 * 
 * java -cp src FileClient localhost 9000 download sample.bin downloads\nio-trad-1.bin 1 traditional
java -cp src FileClient localhost 9000 download sample.bin downloads\nio-zero-1.bin 1 zerocopy
java -cp src FileClient localhost 9000 download sample.bin downloads\nio-trad-10.bin 10 traditional
java -cp src FileClient localhost 9000 download sample.bin downloads\nio-zero-10.bin 10 zerocopy

java -cp src FileClient localhost 9000 download sample.bin downloads\io-trad-1.bin 1 traditional
java -cp src FileClient localhost 9000 download sample.bin downloads\io-zero-1.bin 1 zerocopy
java -cp src FileClient localhost 9000 download sample.bin downloads\io-trad-10.bin 10 traditional
java -cp src FileClient localhost 9000 download sample.bin downloads\io-zero-10.bin 10 zerocopy
Terminal: Clear
 */
public class FileServer {

    // โฟลเดอร์รากที่อนุญาตให้ Client เข้าถึงได้
    private static Path rootDir;

    // อ่านค่าพอร์ตและโฟลเดอร์ที่จะแชร์ จากนั้นเปิด socket และรับ Client ไปเรื่อย ๆ
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        rootDir = Path.of(args.length > 1 ? args[1] : "shared_files").toAbsolutePath().normalize(); // absolute เพื่อให้เข้าถึงโฟลเดอร์ได้แม้เปลี่ยน working directory
        String serverMode = args.length > 2 ? args[2].toUpperCase() : "NIO"; // เลือกโหมด NIO เป็นค่าเริ่มต้น
        if (!serverMode.equals("IO") && !serverMode.equals("NIO")) {
            throw new IllegalArgumentException("Server mode must be io or nio");
        }
        Files.createDirectories(rootDir); // สร้างโฟลเดอร์รากถ้ายังไม่มี

        // Swap for Executors.newVirtualThreadPerTaskExecutor() on Java 21+ if desired.
        ExecutorService pool = Executors.newFixedThreadPool(64); // thread pool สำหรับจัดการ connection ของ Client ที่ใช้64เพราะ เป็นจำนวนที่เหมาะสมสำหรับการทดสอบและไม่ทำให้ระบบล่มง่าย ถ้าvitual จะเหมาะกับงานที่มีจำนวน connection มาก ๆ แต่ในกรณีนี้ใช้ fixed thread pool เพื่อความเรียบง่ายและควบคุมจำนวน thread ได้
        AtomicInteger activeConnections = new AtomicInteger();

        try {
            if (serverMode.equals("IO")) {
                try (ServerSocket serverSocket = new ServerSocket()) { // เปิด ServerSocket สำหรับโหมด IO แบบ blocking
                    serverSocket.bind(new InetSocketAddress(port)); // เชื่อมต่อกับพอร์ตที่กำหนด
                    printServerStarted(port, rootDir, serverMode);  // แสดงข้อความว่า server เริ่มทำงานแล้ว
                    while (true) {
                        Socket client = serverSocket.accept(); // รอรับการเชื่อมต่อจาก Client (blocking)
                        pool.submit(() -> handleClient(client, null, activeConnections)); // ส่งต่อไปยัง thread pool เพื่อจัดการ connection ของ Client
                    }
                }
            } else {
                try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) { // เปิด ServerSocketChannel สำหรับโหมด NIO แบบ non-blocking
                    serverChannel.bind(new InetSocketAddress(port));
                    printServerStarted(port, rootDir, serverMode);
                    while (true) {
                        SocketChannel client = serverChannel.accept();
                        pool.submit(() -> handleClient(client.socket(), client, activeConnections));// handleClient ต้องการทั้ง Socket และ SocketChannel เพื่อให้สามารถใช้ transferTo ในโหมด ZEROCOPY ได้
                    }
                }
            }
        } finally {
            pool.shutdown();// ปิด thread pool เมื่อ server หยุดทำงาน
        }
    }

    private static void printServerStarted(int port, Path rootDir, String serverMode) { // แสดงข้อความว่า server เริ่มทำงานแล้ว
        System.out.println("FileServer listening on port " + port + ", serving " + rootDir + ", mode=" + serverMode);
        System.out.println("Active client connections: 0");
    }

    // อ่านคำสั่งที่ Client ส่งมาต่อเนื่องจาก connection นี้ จนกว่าจะตัดการเชื่อมต่อ
    private static void handleClient(Socket socket, SocketChannel channel, AtomicInteger activeConnections) { // socketChannel ถูกส่งเข้ามาเพื่อให้สามารถใช้ transferTo ในโหมด ZEROCOPY ได้, channel คือ ช่องทางการสื่อสารระหว่าง Client และ Server อาจเป็น null ถ้า server ทำงานในโหมด IO
        updateActiveConnections(activeConnections, 1);
        try (Socket clientSocket = socket; // socket ของ Client ที่เชื่อมต่อเข้ามา, rawIn คือ InputStream สำหรับอ่านข้อมูลจาก Client, out คือ OutputStream สำหรับส่งข้อมูลไปยัง Client
             InputStream rawIn = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));// ใช้ BufferedReader เพื่ออ่านบรรทัดจาก Client โดยใช้ UTF-8 encoding
            String line;
            while ((line = reader.readLine()) != null) {// อ่านคำสั่งจาก Client ทีละบรรทัด จนกว่าจะเจอ EOF (Client ปิด connection)
                if (line.isBlank()) continue;
                handleCommand(line, out, channel);// ส่งคำสั่งไปยัง handleCommand เพื่อจัดการตามคำสั่งที่ Client ส่งมา
            }
        } catch (IOException e) {
            System.out.println("Client disconnected/error: " + e.getMessage());
        } finally {
            updateActiveConnections(activeConnections, -1);
        }
    }

    private static void updateActiveConnections(AtomicInteger activeConnections, int change) {
        synchronized (activeConnections) {
            int active = activeConnections.addAndGet(change);
            System.out.println("Active client connections: " + active);
        }
    }

    // แยกคำสั่งจากบรรทัดที่ได้รับ แล้วส่งต่อไปยังตัวจัดการคำสั่งที่ตรงกัน
    private static void handleCommand(String line, OutputStream out, SocketChannel ch) throws IOException {
        String trimmed = line.trim();// ตัดช่องว่างด้านหน้าและด้านหลังของบรรทัด
        int sp = trimmed.indexOf(' ');// หาตำแหน่งช่องว่างแรกเพื่อแยกคำสั่งออกจากส่วนที่เหลือ
        String cmd = (sp < 0 ? trimmed : trimmed.substring(0, sp)).toUpperCase();// แปลงคำสั่งเป็นตัวพิมพ์ใหญ่เพื่อให้ไม่ sensitive ต่อ case
        // ส่วนที่เหลือของบรรทัด (ชื่อไฟล์อาจมีช่องว่าง)
        String rest = sp < 0 ? "" : trimmed.substring(sp + 1).trim();// ตัดช่องว่างด้านหน้าและด้านหลังของส่วนที่เหลือ
        switch (cmd) {
            case "LIST" -> handleList(out);// out คือ OutputStream สำหรับส่งข้อมูลไปยัง Client
            case "INFO" -> handleInfo(rest, out); // rest คือส่วนที่เหลือของบรรทัด (ชื่อไฟล์)
            case "HASH" -> handleHash(rest, out);
            case "GET" -> handleGet(rest, out, ch);// ch คือ SocketChannel สำหรับใช้ transferTo ในโหมด ZEROCOPY
            default -> sendError(out, 400, "Unknown command: " + cmd);
        }
    }

    // ส่งรายชื่อไฟล์ปกติในโฟลเดอร์ราก พร้อมจำนวน byte ของแต่ละไฟล์
    private static void handleList(OutputStream out) throws IOException {
        List<Path> files;// เก็บรายชื่อไฟล์ปกติในโฟลเดอร์ราก
        try (var stream = Files.list(rootDir)) {// ใช้ Files.list เพื่อสร้าง Stream ของ Path ในโฟลเดอร์ราก
            files = stream.filter(Files::isRegularFile).toList();// กรองเฉพาะไฟล์ปกติ (ไม่รวมโฟลเดอร์หรือ symbolic link) และเก็บเป็น List
        }
        StringBuilder sb = new StringBuilder();// ใช้ StringBuilder เพื่อสร้างข้อความตอบกลับให้ Client
        sb.append("OK ").append(files.size()).append('\n');// ส่งจำนวนไฟล์ที่พบให้ Client
        for (Path p : files) {
            sb.append("FILE ").append(p.getFileName()).append(' ').append(Files.size(p)).append('\n');// ส่งชื่อไฟล์และขนาดไฟล์เป็น byte ให้ Client
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));// ส่งข้อความทั้งหมดไปยัง Client โดยใช้ UTF-8 encoding
        out.flush();// flush เพื่อให้แน่ใจว่าข้อมูลทั้งหมดถูกส่งออกไปยัง Client
    }

    // ตรวจรูปแบบคำขอและตอบกลับขนาดไฟล์ โดยไม่ส่งเนื้อหาไฟล์
    private static void handleInfo(String name, OutputStream out) throws IOException {
        if (name.isEmpty()) {
            sendError(out, 400, "Usage: INFO <filename>");
            return;
        }
        Path file = resolveSafe(name);// ตรวจสอบว่าไฟล์อยู่ภายในโฟลเดอร์รากและไม่ใช่ path traversal
        if (file == null || !Files.isRegularFile(file)) {
            sendError(out, 404, "File not found");
            return;
        }
        out.write(("SIZE " + Files.size(file) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // คำนวณและส่ง SHA-256 ของไฟล์ต้นฉบับ เพื่อให้ Client ตรวจสอบไฟล์ที่ดาวน์โหลดได้
    private static void handleHash(String name, OutputStream out) throws IOException {
        if (name.isEmpty()) {
            sendError(out, 400, "Usage: HASH <filename>");
            return;
        }
        Path file = resolveSafe(name);
        if (file == null || !Files.isRegularFile(file)) {
            sendError(out, 404, "File not found");
            return;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");// MessageDigest คือคลาสที่ใช้คำนวณ hash ของข้อมูล โดยในที่นี้ใช้ SHA-256
            try (InputStream in = Files.newInputStream(file)) {// in คือ InputStream สำหรับอ่านข้อมูลจากไฟล์ต้นฉบับ
                byte[] buffer = new byte[64 * 1024];// buffer ขนาด 64KB เพื่ออ่านข้อมูลทีละ chunk และไม่ต้องโหลดไฟล์ทั้งหมดเข้าหน่วยความจำ
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);// อัปเดต hash ด้วยข้อมูลที่อ่านมาใน buffer
                }
            }
            String hash = HexFormat.of().formatHex(digest.digest());// แปลง hash ที่คำนวณได้เป็น string ในรูปแบบ hexadecimal
            out.write(("SHA256 " + hash + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    // ตรวจช่วง byte ที่ร้องขอ แล้วส่งผ่าน transferTo หรือ buffer ตามโหมดที่ Client เลือก
    private static void handleGet(String args, OutputStream out, SocketChannel ch) throws IOException {
        // รูปแบบ: <filename> <offset> <length> [mode] แยกจากท้ายบรรทัด เพื่อรองรับชื่อไฟล์ที่มีช่องว่าง
        String[] t = args.split("\\s+");// แยกคำสั่งออกเป็นส่วน ๆ โดยใช้ช่องว่างเป็นตัวแบ่ง
        int cnt = t.length; // จำนวนส่วนของคำสั่งทั้งหมด (รวมชื่อไฟล์, offset, length, และ mode ถ้ามี)
        String mode = "TRADITIONAL";
        if (cnt >= 4 && !t[cnt - 1].matches("\\d+")) {
            mode = t[cnt - 1].toUpperCase();
            cnt--;
        }
        if (!mode.equals("TRADITIONAL") && !mode.equals("ZEROCOPY")) {
            sendError(out, 400, "Invalid mode (use traditional or zerocopy)");
            return;
        }
        if (cnt < 3) {
            sendError(out, 400, "Usage: GET <filename> <offset> <length> [mode]");
            return;
        }
        long offset, length;// offset คือจุดเริ่มต้นของ byte ที่ Client ต้องการดาวน์โหลด, length คือจำนวน byte ที่ Client ต้องการดาวน์โหลด
        try {
            offset = Long.parseLong(t[cnt - 2]);
            length = Long.parseLong(t[cnt - 1]);
        } catch (NumberFormatException e) {
            sendError(out, 400, "Invalid offset/length");
            return;
        }
        Path file = resolveSafe(String.join(" ", java.util.Arrays.copyOfRange(t, 0, cnt - 2)));// รวมชื่อไฟล์ที่อาจมีช่องว่างเข้าด้วยกัน และตรวจสอบว่าไฟล์อยู่ภายในโฟลเดอร์รากและไม่ใช่ path traversal
        if (file == null || !Files.isRegularFile(file)) {
            sendError(out, 404, "File not found");
            return;
        }
        long size = Files.size(file);// size คือขนาดของไฟล์ต้นฉบับ
        if (offset < 0 || length < 0 || offset + length > size) {
            sendError(out, 416, "Range not satisfiable");
            return;
        }

        out.write(("OK " + length + "\n").getBytes(StandardCharsets.UTF_8));// ส่งข้อความตอบกลับว่า OK พร้อมจำนวน byte ที่ Client จะได้รับ
        out.flush();

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");// RandomAccessFile ใช้เลื่อนตำแหน่งอ่านไฟล์ได้ตาม offset ที่กำหนด, "r" คือโหมดอ่านอย่างเดียว
             FileChannel fc = raf.getChannel()) {// fc คือ FileChannel สำหรับอ่านข้อมูลจากไฟล์ต้นฉบับ

            if ("ZEROCOPY".equals(mode)) {
                long position = offset;
                long remaining = length;
                WritableByteChannel target = ch == null ? Channels.newChannel(out) : ch; // WritableByteChannel สำหรับส่งข้อมูลไปยัง Client โดยใช้ transferTo ในโหมด ZEROCOPY (ถ้า ch เป็น null จะสร้าง channel ใหม่จาก OutputStream)
                // transferTo อาจส่งได้ไม่ครบในครั้งเดียว จึงวนจนกว่าจะครบช่วงที่ร้องขอ
                while (remaining > 0) {
                    long n = fc.transferTo(position, remaining, target);
                    if (n <= 0) throw new IOException("transferTo made no progress");
                    position += n;
                    remaining -= n;
                }
            } else {
                fc.position(offset);
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024); // buffer ขนาด 64KB เพื่ออ่านข้อมูลทีละ chunk และไม่ต้องโหลดไฟล์ทั้งหมดเข้าหน่วยความจำ
                long remaining = length;
                // อ่านและส่งทีละ buffer เพื่อไม่ต้องเก็บข้อมูลทั้งไฟล์ไว้ในหน่วยความจำ
                while (remaining > 0) {
                    buffer.clear();// ล้าง buffer ก่อนใช้งาน
                    buffer.limit((int) Math.min(buffer.capacity(), remaining));// จำกัดขนาด buffer ให้ไม่เกินจำนวน byte ที่เหลือ
                    int n = fc.read(buffer); // จำนวนไบต์ที่อ่านได้ในรอบนี้
                    if (n < 0) throw new EOFException("Unexpected EOF while reading " + file);
                    out.write(buffer.array(), 0, n);
                    remaining -= n;
                }
                out.flush();
            }
        }
    }

    // ส่ง error response ด้วยรูปแบบเดียวกันสำหรับคำสั่งที่ไม่ถูกต้องหรือทำไม่ได้
    private static void sendError(OutputStream out, int code, String message) throws IOException {
        out.write(("ERROR " + code + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** แปลงชื่อไฟล์เป็น path ภายใน rootDir และปฏิเสธ path traversal ออกนอกโฟลเดอร์ */
    private static Path resolveSafe(String name) {
        Path candidate = rootDir.resolve(name).normalize();
        if (!candidate.startsWith(rootDir)) return null;
        return candidate;
    }
}