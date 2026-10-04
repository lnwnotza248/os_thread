import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

/**
 * Generates a test file of a given size filled with pseudo-random bytes,
 * used as fixed input for the traditional vs zero-copy benchmark.
 *
 * Usage: java GenerateTestFile <path> <sizeBytes>
 * Example: java GenerateTestFile shared_files/testfile.bin 52428800
 */
public class GenerateTestFile {
    public static void main(String[] args) throws Exception {
        // รับ path ปลายทางและขนาดไฟล์ที่ต้องการสร้างเป็น bytes
        if (args.length != 2) {
            System.out.println("Usage: java GenerateTestFile <path> <sizeBytes>");
            return;
        }
        Path path = Path.of(args[0]);
        long size = Long.parseLong(args[1]);
        // สร้างโฟลเดอร์ปลายทางถ้ายังไม่มี
        if (path.getParent() != null) Files.createDirectories(path.getParent());

        // ใช้ seed คงที่เพื่อให้ข้อมูลที่สร้างซ้ำได้เหมือนเดิมเมื่อใช้ขนาดเท่ากัน
        Random random = new Random(42);
        // สร้างข้อมูลทีละ 1 MiB เพื่อจำกัดการใช้หน่วยความจำ
        byte[] buffer = new byte[1 << 20];
        long remaining = size;
        // สร้างหรือเขียนทับไฟล์ แล้วเติมข้อมูลจนได้ขนาดตามที่กำหนด
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (remaining > 0) {
                random.nextBytes(buffer);
            // block สุดท้ายอาจเขียนเพียงบางส่วนเพื่อให้ได้ขนาดพอดี
                int toWrite = (int) Math.min(buffer.length, remaining);
                out.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
        }
        // แสดง path เต็มและขนาดของไฟล์ที่สร้างเสร็จ
        System.out.println("Created " + path.toAbsolutePath() + " (" + size + " bytes)");
    }
}
