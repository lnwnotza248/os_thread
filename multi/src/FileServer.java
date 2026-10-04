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
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * Compile: javac -d out src/FileServer.java
 * Run:     java -cp out FileServer [port] [rootDir]
 */
public class FileServer {

    // โฟลเดอร์รากที่อนุญาตให้ Client เข้าถึงได้
    private static Path rootDir;

    // อ่านค่าพอร์ตและโฟลเดอร์ที่จะแชร์ จากนั้นเปิด socket และรับ Client ไปเรื่อย ๆ
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        rootDir = Path.of(args.length > 1 ? args[1] : "shared_files").toAbsolutePath().normalize();
        Files.createDirectories(rootDir);

        // Swap for Executors.newVirtualThreadPerTaskExecutor() on Java 21+ if desired.
        ExecutorService pool = Executors.newFixedThreadPool(64);

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            System.out.println("FileServer listening on port " + port + ", serving " + rootDir);

            while (true) {
                SocketChannel client = serverChannel.accept();
                pool.submit(() -> handleClient(client));
            }
        } finally {
            pool.shutdown();
        }
    }

    // อ่านคำสั่งที่ Client ส่งมาต่อเนื่องจาก connection นี้ จนกว่าจะตัดการเชื่อมต่อ
    private static void handleClient(SocketChannel channel) {
        try (SocketChannel ch = channel;
             InputStream rawIn = Channels.newInputStream(ch);
             OutputStream out = Channels.newOutputStream(ch)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                handleCommand(line, out, ch);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected/error: " + e.getMessage());
        }
    }

    // แยกคำสั่งจากบรรทัดที่ได้รับ แล้วส่งต่อไปยังตัวจัดการคำสั่งที่ตรงกัน
    private static void handleCommand(String line, OutputStream out, SocketChannel ch) throws IOException {
        String trimmed = line.trim();
        int sp = trimmed.indexOf(' ');
        String cmd = (sp < 0 ? trimmed : trimmed.substring(0, sp)).toUpperCase();
        // ส่วนที่เหลือของบรรทัด (ชื่อไฟล์อาจมีช่องว่าง)
        String rest = sp < 0 ? "" : trimmed.substring(sp + 1).trim();
        switch (cmd) {
            case "LIST" -> handleList(out);
            case "INFO" -> handleInfo(rest, out);
            case "HASH" -> handleHash(rest, out);
            case "GET" -> handleGet(rest, out, ch);
            default -> sendError(out, 400, "Unknown command: " + cmd);
        }
    }

    // ส่งรายชื่อไฟล์ปกติในโฟลเดอร์ราก พร้อมจำนวน byte ของแต่ละไฟล์
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

    // ตรวจรูปแบบคำขอและตอบกลับขนาดไฟล์ โดยไม่ส่งเนื้อหาไฟล์
    private static void handleInfo(String name, OutputStream out) throws IOException {
        if (name.isEmpty()) {
            sendError(out, 400, "Usage: INFO <filename>");
            return;
        }
        Path file = resolveSafe(name);
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            out.write(("SHA256 " + hash + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    // ตรวจช่วง byte ที่ร้องขอ แล้วส่งผ่าน transferTo หรือ buffer ตามโหมดที่ Client เลือก
    private static void handleGet(String args, OutputStream out, SocketChannel ch) throws IOException {
        // รูปแบบ: <filename> <offset> <length> [mode] แยกจากท้ายบรรทัด เพื่อรองรับชื่อไฟล์ที่มีช่องว่าง
        String[] t = args.split("\\s+");
        int cnt = t.length;
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
        long offset, length;
        try {
            offset = Long.parseLong(t[cnt - 2]);
            length = Long.parseLong(t[cnt - 1]);
        } catch (NumberFormatException e) {
            sendError(out, 400, "Invalid offset/length");
            return;
        }
        Path file = resolveSafe(String.join(" ", java.util.Arrays.copyOfRange(t, 0, cnt - 2)));
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
                // transferTo อาจส่งได้ไม่ครบในครั้งเดียว จึงวนจนกว่าจะครบช่วงที่ร้องขอ
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
                // อ่านและส่งทีละ buffer เพื่อไม่ต้องเก็บข้อมูลทั้งไฟล์ไว้ในหน่วยความจำ
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