package th.ac.example.download;

public record DownloadRange(long offset, long length) {
    public long endExclusive() { return offset + length; }
}
