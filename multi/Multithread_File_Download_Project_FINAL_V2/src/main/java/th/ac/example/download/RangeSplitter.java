package th.ac.example.download;

import java.util.ArrayList;
import java.util.List;

public final class RangeSplitter {
    private RangeSplitter() {}

    public static List<DownloadRange> split(long fileSize, int workers) {
        if (fileSize < 0) throw new IllegalArgumentException("fileSize must be >= 0");
        if (workers < 1 || workers > 10) throw new IllegalArgumentException("workers must be 1..10");
        List<DownloadRange> ranges = new ArrayList<>(workers);
        long base = fileSize / workers;
        long remainder = fileSize % workers;
        long offset = 0;
        for (int i = 0; i < workers; i++) {
            long length = (i == workers - 1) ? base + remainder : base;
            ranges.add(new DownloadRange(offset, length));
            offset += length;
        }
        return ranges;
    }
}
