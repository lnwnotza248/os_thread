package th.ac.example.download;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public final class IntegrationTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("file-download-server-");
        Path out = Files.createTempDirectory("file-download-output-");
        byte[] data = new byte[3 * 1024 * 1024 + 123];
        new Random(123456789L).nextBytes(data);
        Files.write(root.resolve("sample.bin"), data);
        Files.write(root.resolve("small.txt"), "hello\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("hello world.txt"), "space-name\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("empty.bin"), new byte[0]);
        Files.write(root.resolve("one.bin"), new byte[]{42});
        testRangeSplitter();
        testValidationAndProtocolReader(root);
        testOverlongRequestResponse(root);
        testFailedDownloadDoesNotPublishPartialFile(out);
        runMode(DownloadMode.TRADITIONAL, root, out, data);
        runMode(DownloadMode.NIO, root, out, data);
        runConcurrentClients(DownloadMode.TRADITIONAL, root, out, data.length, 10);
        runConcurrentClients(DownloadMode.NIO, root, out, data.length, 20);
        System.out.printf("\nTEST SUMMARY: passed=%d failed=%d%n", passed, failed);
        if (failed > 0) throw new AssertionError("integration tests failed");
    }


    private static void testValidationAndProtocolReader(Path root) throws Exception {
        expectThrows("range negative size", () -> RangeSplitter.split(-1, 10));
        expectThrows("range zero workers", () -> RangeSplitter.split(10, 0));
        expectThrows("range more than 10 workers", () -> RangeSplitter.split(10, 11));
        expectThrows("client invalid port", () -> new FileDownloadClient("127.0.0.1", 0, 1000));
        expectThrows("client invalid timeout", () -> new FileDownloadClient("127.0.0.1", 5000, 0));
        expectThrows("server invalid connections", () -> new FileDownloadServer(root, 0, DownloadMode.NIO, 0));
        expectThrows("unsafe slash filename", () -> FileUtil.safeResolve(root, "a/b.bin"));
        expectThrows("unsafe backslash filename", () -> FileUtil.safeResolve(root, "a\\b.bin"));
        expectThrows("unsafe newline filename", () -> FileUtil.safeResolve(root, "bad\nbin"));

        byte[] crlf = "hello\r\nworld".getBytes(StandardCharsets.UTF_8);
        try (Protocol.LineReader reader = Protocol.lineReader(new ByteArrayInputStream(crlf))) {
            check("line reader CRLF", "hello".equals(reader.readLine()) && "world".equals(reader.readLine()));
        }
        byte[] tooLong = new byte[64 * 1024 + 1];
        java.util.Arrays.fill(tooLong, (byte) 'x');
        expectThrows("line reader max length", () -> {
            try (Protocol.LineReader reader = Protocol.lineReader(new ByteArrayInputStream(tooLong))) {
                reader.readLine();
            }
        });

        Path outside = Files.createTempFile("outside-download-", ".bin");
        Files.write(outside, new byte[]{1, 2, 3});
        Path link = root.resolve("outside-link.bin");
        try {
            Files.createSymbolicLink(link, outside);
            expectThrows("symbolic link filename", () -> FileUtil.safeResolve(root, "outside-link.bin"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            System.out.println("SKIP symbolic link test: platform does not allow symlink creation");
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    private static void testOverlongRequestResponse(Path root) throws Exception {
        try (FileDownloadServer server = new FileDownloadServer(root, 0, DownloadMode.TRADITIONAL, 5)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                socket.getOutputStream().write(new byte[0]);
                byte[] huge = new byte[70 * 1024];
                java.util.Arrays.fill(huge, (byte) 'x');
                socket.getOutputStream().write(huge);
                socket.getOutputStream().write('\n');
                socket.getOutputStream().flush();
                Protocol.LineReader reader = Protocol.lineReader(socket.getInputStream());
                String response = reader.readLine();
                check("server rejects overlong request", response != null && response.startsWith("ERROR BAD_REQUEST"));
            }
        }
    }

    private static void testFailedDownloadDoesNotPublishPartialFile(Path out) throws Exception {
        Path target = out.resolve("failure-should-stay-old.bin");
        Files.write(target, "OLD".getBytes(StandardCharsets.UTF_8));
        try (ServerSocket listener = new ServerSocket(0)) {
            Thread server = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = listener.accept()) {
                            Protocol.LineReader in = Protocol.lineReader(socket.getInputStream());
                            PrintWriter writer = Protocol.writer(socket.getOutputStream());
                            String request = in.readLine();
                            if (request != null && request.startsWith("INFO ")) {
                                writer.println("OK SIZE 4"); writer.flush();
                            } else if (request != null && request.startsWith("GET ")) {
                                writer.println("OK DATA 4"); writer.flush();
                                socket.getOutputStream().write(new byte[]{9, 8});
                                socket.getOutputStream().flush();
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Test server ends when the client closes the connection after failure.
                }
            }, "failing-test-server");
            server.start();
            FileDownloadClient client = new FileDownloadClient("127.0.0.1", listener.getLocalPort(), 1000);
            expectThrows("failed download", () -> client.download("broken.bin", target, 1));
            server.join(2000);
        }
        check("failed download keeps previous file", new String(Files.readAllBytes(target), StandardCharsets.UTF_8).equals("OLD"));
    }

    private static void expectThrows(String name, ThrowingAction action) {
        try { action.run(); fail(name + " should throw"); }
        catch (Exception e) { check(name, true); }
    }

    private static void runMode(DownloadMode mode, Path root, Path out, byte[] data) throws Exception {
        try (FileDownloadServer server = new FileDownloadServer(root, 0, mode, 20)) {
            server.start();
            FileDownloadClient client = new FileDownloadClient("127.0.0.1", server.port(), 2000);
            List<String> listed = client.listFiles();
            check("LIST", listed.containsAll(List.of("sample.bin", "small.txt", "hello world.txt", "empty.bin", "one.bin")));
            check("INFO", client.info("sample.bin") == data.length);
            check("INFO filename with spaces", client.info("hello world.txt") == 11);
            Path oneWorkerTiny = out.resolve(mode.name().toLowerCase() + "-empty-w10.bin");
            DownloadResult tinyResult = client.download("empty.bin", oneWorkerTiny, 10);
            check(mode + " empty file w=10 size", Files.size(oneWorkerTiny) == 0);
            check(mode + " empty file w=10 hash", tinyResult.sha256().equals(FileUtil.sha256(root.resolve("empty.bin"))));
            Path oneByte = out.resolve(mode.name().toLowerCase() + "-one-w10.bin");
            DownloadResult oneResult = client.download("one.bin", oneByte, 10);
            check(mode + " one-byte w=10 size", Files.size(oneByte) == 1);
            check(mode + " one-byte w=10 hash", oneResult.sha256().equals(FileUtil.sha256(root.resolve("one.bin"))));
            expectError("INFO missing", () -> client.info("missing.bin"), "NOT_FOUND");
            expectError("INFO traversal", () -> client.info("../sample.bin"), "BAD_REQUEST");
            protocolCase(server.port(), "GET sample.bin 0 16", "OK DATA 16", 16);
            protocolCase(server.port(), "GET sample.bin 10 0", "OK DATA 0", 0);
            protocolCase(server.port(), "GET sample.bin 0 " + (data.length + 1L), "ERROR RANGE_INVALID", -1);
            protocolCase(server.port(), "GET sample.bin " + data.length + " 0", "OK DATA 0", 0);
            protocolCase(server.port(), "GET sample.bin " + (data.length + 1L) + " 0", "ERROR RANGE_INVALID", -1);
            protocolCase(server.port(), "GET missing.bin 0 1", "ERROR NOT_FOUND", -1);
            protocolCase(server.port(), "GET ../sample.bin 0 1", "ERROR BAD_REQUEST", -1);
            protocolCase(server.port(), "NOPE", "ERROR BAD_REQUEST", -1);

            String expectedHash = FileUtil.sha256(root.resolve("sample.bin"));
            for (int workers : new int[]{1, 2, 3, 10}) {
                Path target = out.resolve(mode.name().toLowerCase() + "-w" + workers + ".bin");
                DownloadResult result = client.download("sample.bin", target, workers);
                check(mode + " download w=" + workers + " size", Files.size(target) == data.length);
                check(mode + " download w=" + workers + " hash", result.sha256().equals(expectedHash));
                check(mode + " download w=" + workers + " bytes", result.bytes() == data.length);
            }
        }
    }

    private static void protocolCase(int port, String request, String expectedPrefix, long payloadBytes) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            Protocol.LineReader r = Protocol.lineReader(socket.getInputStream());
            String header = r.readLine();
            check("protocol " + request, header != null && header.startsWith(expectedPrefix));
            if (payloadBytes >= 0) {
                long remaining = payloadBytes;
                byte[] buf = new byte[8192];
                while (remaining > 0) {
                    int n = socket.getInputStream().read(buf, 0, (int)Math.min(buf.length, remaining));
                    if (n < 0) throw new AssertionError("unexpected EOF");
                    remaining -= n;
                }
                check("protocol payload " + request, remaining == 0);
            }
        }
    }

    private static void expectError(String name, ThrowingAction action, String code) throws Exception {
        try { action.run(); fail(name + " should fail"); }
        catch (Exception e) { check(name, e.getMessage() != null && e.getMessage().contains("ERROR " + code)); }
    }

    private static void testRangeSplitter() {
        for (long size = 0; size <= 64; size++) {
            for (int workers = 1; workers <= 10; workers++) {
                List<DownloadRange> ranges = RangeSplitter.split(size, workers);
                long cursor = 0;
                boolean ok = ranges.size() == workers;
                for (DownloadRange r : ranges) {
                    ok &= r.offset() == cursor && r.length() >= 0 && r.endExclusive() == r.offset() + r.length();
                    cursor = r.endExclusive();
                }
                ok &= cursor == size;
                check("range size=" + size + " workers=" + workers, ok);
            }
        }
    }

    private static void runConcurrentClients(DownloadMode mode, Path root, Path out, int dataLength, int clientCount) throws Exception {
        try (FileDownloadServer server = new FileDownloadServer(root, 0, mode, 50)) {
            server.start();
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
            List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
            String expectedHash = FileUtil.sha256(root.resolve("sample.bin"));
            for (int i = 0; i < clientCount; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    Path target = out.resolve("concurrent-" + id + ".bin");
                    FileDownloadClient c = new FileDownloadClient("127.0.0.1", server.port(), 3000);
                    DownloadResult r = c.download("sample.bin", target, 10);
                    if (Files.size(target) != dataLength || !r.sha256().equals(expectedHash)) throw new AssertionError("concurrent client " + id + " mismatch");
                    return r.sha256();
                }));
            }
            for (var f : futures) f.get();
            pool.shutdown();
            check(clientCount + " concurrent 10-worker clients on " + mode, true);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("PASS " + name); }
        else fail(name);
    }
    private static void fail(String name) { failed++; System.out.println("FAIL " + name); }
    @FunctionalInterface interface ThrowingAction { void run() throws Exception; }
}
