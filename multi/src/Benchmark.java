import java.nio.file.Path;
import java.nio.file.Files;

/**
 * Runs the experiment required by the assignment:
 *   - Traditional I/O vs NIO zero-copy transfer
 *   - 1 worker vs 10 workers
 *   - 3 repetitions per case
 * and prints elapsed time / throughput / SHA-256 for each run so results can be
 * compared and discussed (why 10 workers isn't a 10x speedup, why zero-copy
 * doesn't always win, especially over loopback with page cache warm).
 *
 * Usage: java Benchmark <host> <port> <remoteFilename> <localWorkDir> [keep|delete]
 * (optional last argument decides whether to keep the downloaded test files; omitted = ask)
 */
public class Benchmark {
    // จำนวนครั้งที่รันทดสอบต่อชุดค่าการทดลอง
    private static final int REPEATS = 3;
    // เปรียบเทียบการดาวน์โหลดแบบ worker เดียวกับ worker หลายตัว
    private static final int[] WORKER_COUNTS = {1, 10};
    // เปรียบเทียบการส่งข้อมูลผ่าน buffer กับ NIO transfer
    private static final String[] MODES = {"traditional", "zerocopy"};

    // วนทดสอบทุก mode/จำนวน worker ซ้ำ และแสดงเวลา ความเร็ว และ hash แต่ละรอบ
    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            System.out.println("Usage: java Benchmark <host> <port> <remoteFilename> <localWorkDir> [keep|delete]");
            return;
        }
        // keep/delete ข้ามคำถามตอนจบ ถ้าไม่ระบุจะถามผู้ใช้
        String cleanup = args.length == 5 ? args[4].toLowerCase() : "ask";
        if (!cleanup.equals("keep") && !cleanup.equals("delete") && !cleanup.equals("ask")) {
            System.out.println("Last argument must be keep or delete");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String remoteFile = args[2];
        Path workDir = Path.of(args[3]);
        java.nio.file.Files.createDirectories(workDir);

        System.out.printf("%-12s %-8s %-4s %10s %12s %-9s %s%n",
                "mode", "workers", "run", "ms", "MiB/s", "sha-match", "sha256");

        java.util.List<Path> downloaded = new java.util.ArrayList<>();
        boolean completed = false;
        try {
            // ดาวน์โหลดแต่ละกรณีไปยังไฟล์แยก เพื่อเก็บผลและตรวจ hash ได้
            for (String mode : MODES) {
                for (int workers : WORKER_COUNTS) {
                    for (int run = 1; run <= REPEATS; run++) {
                        Path dest = Files.createTempFile(workDir, "benchmark-", ".download");
                        downloaded.add(dest);
                        FileClient.DownloadResult r = FileClient.download(host, port, remoteFile, dest, workers, mode);
                        String match = r.sha256Matches() ? "MATCH" : "MISMATCH";
                        System.out.printf("%-12s %-8d %-4d %10.2f %12.2f %-9s %s%n",
                                mode, workers, run, r.elapsedMs(), r.mbPerSec(), match, r.sha256());
                        if (!r.sha256Matches()) {
                            throw new IllegalStateException("SHA-256 mismatch for " + remoteFile
                                    + " (" + mode + ", workers=" + workers + ", run=" + run + ")");
                        }
                    }
                }
            }
            completed = true;
        } finally {
            // เมื่อเกิดข้อผิดพลาดให้ลบไฟล์ชั่วคราวเสมอ แต่ถ้าสำเร็จจึงให้เลือกได้
            if (completed && !shouldDelete(cleanup, downloaded.size(), workDir)) {
                System.out.println("Kept " + downloaded.size() + " downloaded file(s) in " + workDir.toAbsolutePath());
            } else {
                for (Path p : downloaded) Files.deleteIfExists(p);
            }
        }
    }

    // ถามผู้ใช้ว่าจะลบไฟล์ทดสอบที่ดาวน์โหลดมาหรือไม่ ค่าเริ่มต้นคือลบ (รวมกรณีไม่มี input)
    private static boolean shouldDelete(String cleanup, int count, Path workDir) {
        if (cleanup.equals("delete")) return true;
        if (cleanup.equals("keep")) return false;
        System.out.printf("Delete %d downloaded test file(s) in %s? [Y/n] ", count, workDir.toAbsolutePath());
        java.util.Scanner in = new java.util.Scanner(System.in);
        String answer = in.hasNextLine() ? in.nextLine().trim().toLowerCase() : "";
        return !(answer.equals("n") || answer.equals("no"));
    }
}
