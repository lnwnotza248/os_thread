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
    // รับ path และขนาดไฟล์ จากนั้นสร้างไฟล์ pseudo-random สำหรับ benchmark
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java GenerateTestFile <path> <sizeBytes>");
            return;
        }
        Path path = Path.of(args[0]);
        long size = Long.parseLong(args[1]);
        if (path.getParent() != null) Files.createDirectories(path.getParent());

        Random random = new Random(42);
        byte[] buffer = new byte[1 << 20];
        long remaining = size;
        // เขียนทีละ 1 MiB จนได้ขนาดที่ต้องการ โดย block สุดท้ายเขียนเท่าที่เหลือ
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (remaining > 0) {
                random.nextBytes(buffer);
                int toWrite = (int) Math.min(buffer.length, remaining);
                out.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
        }
        System.out.println("Created " + path.toAbsolutePath() + " (" + size + " bytes)");
    }
}
