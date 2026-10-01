package th.ac.example.download;

import java.nio.file.Path;

public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        FileDownloadServer server = new FileDownloadServer(config.directory, config.port, config.mode, config.maxConnections);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
        server.start();
        System.out.printf("SERVER_READY port=%d mode=%s dir=%s workers=%d%n", server.port(), config.mode, config.directory.toAbsolutePath(), config.maxConnections);
        Thread.currentThread().join();
    }

    static final class Config {
        final int port;
        final Path directory;
        final DownloadMode mode;
        final int maxConnections;

        Config(int port, Path directory, DownloadMode mode, int maxConnections) {
            this.port = port; this.directory = directory; this.mode = mode; this.maxConnections = maxConnections;
        }

        static Config parse(String[] args) {
            int port = 5000; Path dir = Path.of("server_files"); DownloadMode mode = DownloadMode.TRADITIONAL; int max = 50;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--port" -> port = Integer.parseInt(requireValue(args, ++i, "--port"));
                    case "--dir" -> dir = Path.of(requireValue(args, ++i, "--dir"));
                    case "--mode" -> mode = DownloadMode.parse(requireValue(args, ++i, "--mode"));
                    case "--max-connections" -> max = Integer.parseInt(requireValue(args, ++i, "--max-connections"));
                    case "--help" -> { printHelp(); System.exit(0); }
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be 0..65535");
            if (max < 1) throw new IllegalArgumentException("max-connections must be >= 1");
            return new Config(port, dir, mode, max);
        }

        static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("missing value for " + option);
            return args[index];
        }

        static void printHelp() {
            System.out.println("java th.ac.example.download.ServerMain [--port 5000] [--dir server_files] [--mode traditional|nio] [--max-connections 50]");
        }
    }
}
