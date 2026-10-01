package th.ac.example.download;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Runs the required benchmark while controlling the real server mode in the same JVM. */
public final class BenchmarkDriverMain {
    private BenchmarkDriverMain() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of("server_files");
        Path outputDir = Path.of("output/benchmark");
        String file = "sample.bin";
        int runs = 3;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir" -> root = Path.of(requireValue(args, ++i, "--dir"));
                case "--output-dir" -> outputDir = Path.of(requireValue(args, ++i, "--output-dir"));
                case "--file" -> file = requireValue(args, ++i, "--file");
                case "--runs" -> runs = Integer.parseInt(requireValue(args, ++i, "--runs"));
                case "--help" -> { printHelp(); return; }
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        if (runs < 3) throw new IllegalArgumentException("Requirement: each case must run at least 3 times");
        Files.createDirectories(outputDir);
        Path source = FileUtil.safeResolve(root, file);
        long expectedSize = Files.size(source);
        String expectedHash = FileUtil.sha256(source);
        Path csv = outputDir.resolve("results.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            writer.write("MODE,WORKERS,RUN,BYTES,SECONDS,MBPS,SHA256,SIZE_OK,HASH_OK");
            writer.newLine();
            for (int run = 1; run <= runs; run++) {
                DownloadMode first = (run % 2 == 1) ? DownloadMode.TRADITIONAL : DownloadMode.NIO;
                DownloadMode second = (run % 2 == 1) ? DownloadMode.NIO : DownloadMode.TRADITIONAL;
                runOneServerRun(first, file, expectedSize, expectedHash, outputDir, run, writer, root);
                runOneServerRun(second, file, expectedSize, expectedHash, outputDir, run, writer, root);
            }
        }
        System.out.println("Benchmark finished: " + csv.toAbsolutePath());
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("missing value for " + option);
        return args[index];
    }

    private static void runOneServerRun(DownloadMode mode, String file, long expectedSize,
                                        String expectedHash, Path outputDir, int run,
                                        BufferedWriter writer, Path root) throws Exception {
        try (FileDownloadServer server = new FileDownloadServer(root, 0, mode, 50)) {
            server.start();
            FileDownloadClient client = new FileDownloadClient("127.0.0.1", server.port(), 5000);
            if (client.info(file) != expectedSize) throw new IllegalStateException("server INFO size mismatch");
            for (int workers : new int[]{1, 10}) {
                Path out = outputDir.resolve(mode.name().toLowerCase() + "-w" + workers + "-run" + run + ".bin");
                DownloadResult result = client.download(file, out, workers);
                long size = Files.size(out);
                boolean sizeOk = size == expectedSize;
                boolean hashOk = result.sha256().equalsIgnoreCase(expectedHash);
                writer.write(String.format(Locale.ROOT, "%s,%d,%d,%d,%.6f,%.3f,%s,%s,%s%n",
                        mode.name().toLowerCase(), workers, run, result.bytes(), result.seconds(),
                        result.megabytesPerSecond(), result.sha256(), sizeOk, hashOk));
                writer.flush();
                System.out.printf(Locale.ROOT, "%s workers=%d run=%d seconds=%.6f MB/s=%.3f sizeOK=%s hashOK=%s%n",
                        mode, workers, run, result.seconds(), result.megabytesPerSecond(), sizeOk, hashOk);
                if (!sizeOk || !hashOk) throw new IllegalStateException("benchmark verification failed: " + out);
            }
        }
    }

    private static void printHelp() {
        System.out.println("java -cp bin th.ac.example.download.BenchmarkDriverMain [--dir server_files] [--file sample.bin] [--runs 3] [--output-dir output/benchmark]");
    }
}
