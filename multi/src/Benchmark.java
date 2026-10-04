import java.nio.file.Path;

/**
 * Runs the experiment required by the assignment:
 * - Traditional I/O vs NIO zero-copy transfer
 * - 1 worker vs 10 workers
 * - 3 repetitions per case
 * and prints elapsed time / throughput / SHA-256 for each run so results can be
 * compared and discussed (why 10 workers isn't a 10x speedup, why zero-copy
 * doesn't always win, especially over loopback with page cache warm).
 *
 * Usage: java Benchmark <host> <port> <remoteFilename> <localWorkDir>
 */
public class Benchmark {
    // จำนวนรอบที่ทดสอบในแต่ละชุด
    private static final int REPEATS = 3;
    // เปรียบเทียบ worker เดียวกับ worker สิบตัว
    private static final int[] WORKER_COUNTS = { 1, 10 };
    // โหมดการส่งข้อมูลที่จะนำมาเปรียบเทียบ
    private static final String[] MODES = { "traditional", "zerocopy" };

    public static void main(String[] args) throws Exception {
        // รับ host, port, ชื่อไฟล์บน Server และโฟลเดอร์ทำงานในเครื่อง
        if (args.length != 4) {
            System.out.println("Usage: java Benchmark <host> <port> <remoteFilename> <localWorkDir>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String remoteFile = args[2];
        Path workDir = Path.of(args[3]);
        java.nio.file.Files.createDirectories(workDir);

        // แสดงหัวตารางก่อนพิมพ์ผลการทดสอบแต่ละรอบ
        System.out.printf("%-12s %-8s %-4s %10s %12s %s%n", "mode", "workers", "run", "ms", "MB/s", "sha256");

        // วนทดสอบทุกโหมด ทุกจำนวน worker และทำซ้ำตาม REPEATS
        for (String mode : MODES) {
            for (int workers : WORKER_COUNTS) {
                for (int run = 1; run <= REPEATS; run++) {
                    Path dest = workDir.resolve("benchmark-temp.bin");

                    try {
                        // เรียก logic ดาวน์โหลดจริง แล้วพิมพ์เวลา ความเร็ว และ hash
                        FileClient.DownloadResult r = FileClient.download(
                                host,
                                port,
                                remoteFile,
                                dest,
                                workers,
                                mode);

                        System.out.printf(
                                "%-12s %-8d %-4d %10.2f %12.2f %s%n",
                                mode,
                                workers,
                                run,
                                r.elapsedMs(),
                                r.mbPerSec(),
                                r.sha256());
                    } finally {
                        // ลบไฟล์ชั่วคราวหลังแต่ละรอบ ไม่ให้ไฟล์เดิมปะปนกับรอบถัดไป
                        java.nio.file.Files.deleteIfExists(dest);
                    }
                }
            }
        }
    }
}
