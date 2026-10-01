package th.ac.example.download;

public record DownloadResult(long bytes, long elapsedNanos, String sha256) {
    public double seconds() { return elapsedNanos / 1_000_000_000.0; }
    public double megabytesPerSecond() {
        double sec = seconds();
        return sec <= 0 ? Double.POSITIVE_INFINITY : (bytes / 1_048_576.0) / sec;
    }
}
