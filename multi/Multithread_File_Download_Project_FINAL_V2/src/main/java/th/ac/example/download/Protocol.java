package th.ac.example.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    private static final int MAX_LINE_LENGTH = 64 * 1024;
    private Protocol() {}

    // Reads only the command/header bytes up to '\n' and never buffers binary payload bytes.
    public static LineReader lineReader(InputStream in) {
        return new LineReader(in);
    }

    public static PrintWriter writer(OutputStream out) {
        return new PrintWriter(out, false, StandardCharsets.UTF_8);
    }

    public static void sendError(PrintWriter out, String code, String message) {
        out.printf("ERROR %s %s%n", code, sanitizeMessage(message));
        out.flush();
    }

    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) return "request_failed";
        return message.replace('\r', ' ').replace('\n', ' ');
    }

    public static final class LineReader implements AutoCloseable {
        private final InputStream in;
        public LineReader(InputStream in) { this.in = in; }

        public String readLine() throws IOException {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b == -1) {
                    if (buffer.size() == 0) return null;
                    break;
                }
                if (b == '\n') break;
                if (b != '\r') buffer.write(b);
                if (buffer.size() > MAX_LINE_LENGTH) throw new IOException("request line too long");
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws IOException { in.close(); }
    }
}
