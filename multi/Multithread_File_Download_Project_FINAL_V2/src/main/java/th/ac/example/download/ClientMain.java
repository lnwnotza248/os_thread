package th.ac.example.download;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientMain {
    private ClientMain() {}

    public static void main(String[] args) throws Exception {
        Config c = Config.parse(args);
        FileDownloadClient client = new FileDownloadClient(c.host, c.port, c.timeout);
        if (c.list) {
            for (String file : client.listFiles()) System.out.println(file);
            return;
        }
        long start = System.nanoTime();
        DownloadResult result = client.download(c.file, c.output, c.workers);
        long totalElapsed = System.nanoTime() - start;
        if (result.bytes() != Files.size(c.output)) throw new IllegalStateException("size verification failed");
        System.out.printf(Locale.ROOT, "DOWNLOAD_OK file=%s workers=%d mode=%s bytes=%d seconds=%.6f throughput_MBps=%.3f sha256=%s total_seconds=%.6f%n",
                c.file, c.workers, c.modeLabel, result.bytes(), result.seconds(), result.megabytesPerSecond(), result.sha256(), totalElapsed / 1_000_000_000.0);
    }

    static final class Config {
        String host = "127.0.0.1"; int port = 5000; String file = null; Path output = Path.of("output/downloaded.bin");
        int workers = 10; int timeout = 5000; boolean list = false; String modeLabel = "server-selected";
        static Config parse(String[] args) {
            Config c = new Config();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--host" -> c.host = val(args, ++i, "--host");
                    case "--port" -> c.port = Integer.parseInt(val(args, ++i, "--port"));
                    case "--file" -> c.file = val(args, ++i, "--file");
                    case "--output" -> c.output = Path.of(val(args, ++i, "--output"));
                    case "--workers" -> c.workers = Integer.parseInt(val(args, ++i, "--workers"));
                    case "--timeout-ms" -> c.timeout = Integer.parseInt(val(args, ++i, "--timeout-ms"));
                    case "--list" -> c.list = true;
                    case "--mode-label" -> c.modeLabel = val(args, ++i, "--mode-label");
                    case "--help" -> { help(); System.exit(0); }
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            if (!c.list && (c.file == null || c.file.isBlank())) throw new IllegalArgumentException("--file is required unless --list is used");
            if (c.workers < 1 || c.workers > 10) throw new IllegalArgumentException("workers must be 1..10");
            return c;
        }
        static String val(String[] a, int i, String opt) { if (i >= a.length) throw new IllegalArgumentException("missing value for " + opt); return a[i]; }
        static void help() { System.out.println("java th.ac.example.download.ClientMain --file <name> [--host 127.0.0.1] [--port 5000] [--output output/file] [--workers 10] [--mode-label traditional|nio]\njava th.ac.example.download.ClientMain --list"); }
    }
}
