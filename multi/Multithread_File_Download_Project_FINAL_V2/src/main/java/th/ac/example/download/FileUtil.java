package th.ac.example.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtil {
    private FileUtil() {}

    public static String sha256(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static Path safeResolve(Path root, String filename) throws IOException {
        if (filename == null || filename.isBlank()) throw new IOException("filename is empty");
        if (filename.indexOf('\u0000') >= 0) throw new IOException("invalid filename");
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (Character.isISOControl(ch)) throw new IOException("control characters are not allowed");
        }
        if (filename.contains("/") || filename.contains("\\")) throw new IOException("path separators are not allowed");
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.getParent().equals(base)) throw new IOException("invalid filename");
        if (Files.isSymbolicLink(resolved)) throw new IOException("symbolic links are not allowed");
        return resolved;
    }
}
