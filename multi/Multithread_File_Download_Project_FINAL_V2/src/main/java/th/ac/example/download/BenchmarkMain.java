package th.ac.example.download;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class BenchmarkMain {
    private BenchmarkMain() {}
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5000;
        String file = "sample.bin";
        int runs = 3;
        String mode = "traditional";
        Path outputDir = Path.of("output/benchmark-traditional");
        String expectedHash = null;
        Path sourceFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = requireValue(args, ++i, "--host");
                case "--port" -> port = Integer.parseInt(requireValue(args, ++i, "--port"));
                case "--file" -> file = requireValue(args, ++i, "--file");
                case "--runs" -> runs = Integer.parseInt(requireValue(args, ++i, "--runs"));
                case "--mode" -> mode = requireValue(args, ++i, "--mode").toLowerCase();
                case "--output-dir" -> outputDir = Path.of(requireValue(args, ++i, "--output-dir"));
                case "--expected-sha256" -> expectedHash = requireValue(args, ++i, "--expected-sha256");
                case "--source-file" -> sourceFile = Path.of(requireValue(args, ++i, "--source-file"));
                case "--help" -> { printHelp(); return; }
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        DownloadMode.parse(mode);
        if (runs < 3) throw new IllegalArgumentException("Requirement: each case must run at least 3 times");
        Files.createDirectories(outputDir);
        FileDownloadClient client = new FileDownloadClient(host, port, 5000);
        long remoteSize = client.info(file);
        if (sourceFile != null) {
            if (!Files.isRegularFile(sourceFile)) throw new IllegalArgumentException("source-file is not a regular file: " + sourceFile);
            expectedHash = FileUtil.sha256(sourceFile);
            if (Files.size(sourceFile) != remoteSize) throw new IllegalStateException("source and remote file sizes differ");
        }
        if (expectedHash == null || !expectedHash.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("BenchmarkMain needs --source-file or a 64-hex --expected-sha256 for real hash verification");
        }
        long expectedSize = remoteSize;
        System.out.println("MODE,WORKERS,RUN,BYTES,SECONDS,MBPS,SHA256,SIZE_OK,HASH_OK");
        for (int workers : new int[]{1, 10}) {
            for (int run = 1; run <= runs; run++) {
                Path out = outputDir.resolve(mode + "-w" + workers + "-run" + run + ".bin");
                DownloadResult result = client.download(file, out, workers);
                long size = Files.size(out);
                boolean sizeOk = size == expectedSize;
                boolean hashOk = result.sha256().equalsIgnoreCase(expectedHash);
                System.out.printf(Locale.ROOT, "%s,%d,%d,%d,%.6f,%.3f,%s,%s,%s%n",
                        mode, workers, run, result.bytes(), result.seconds(), result.megabytesPerSecond(), result.sha256(), sizeOk, hashOk);
                if (!sizeOk || !hashOk) throw new IllegalStateException("benchmark verification failed: " + out);
            }
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("missing value for " + option);
        return args[index];
    }

    private static void printHelp() {
        System.out.println("java -cp bin th.ac.example.download.BenchmarkMain [--host 127.0.0.1] [--port 5000] [--file sample.bin] [--mode traditional|nio] [--runs 3] [--output-dir output/benchmark] (--source-file path | --expected-sha256 hex)");
    }
}
