import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Ready Queue แบบ Priority ที่ลด priority ลงตามเวลาที่รอ */
public class AgingReadyQueue {
    public static final class Entry {
        final Job job;
        final long readySinceMs;

        Entry(Job job, long readySinceMs) {
            this.job = job;
            this.readySinceMs = readySinceMs;
        }
    }

    private final long agingIntervalMs;
    private final List<Entry> entries = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private boolean closed;

    public AgingReadyQueue(long agingIntervalMs) {
        if (agingIntervalMs <= 0) throw new IllegalArgumentException("agingIntervalMs ต้องมากกว่า 0");
        this.agingIntervalMs = agingIntervalMs;
    }

    public void add(Job job, long nowMs) {
        lock.lock();
        try {
            if (closed) throw new IllegalStateException("AgingReadyQueue ถูกปิดแล้ว");
            entries.add(new Entry(job, nowMs));
            job.setState(JobState.READY);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public Job take(long nowMs) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (entries.isEmpty() && !closed) notEmpty.await();
            if (entries.isEmpty()) return null;

            int selected = 0;
            for (int i = 1; i < entries.size(); i++) {
                if (compare(entries.get(i), entries.get(selected), nowMs) < 0) selected = i;
            }
            return entries.remove(selected).job;
        } finally {
            lock.unlock();
        }
    }

    private int compare(Entry a, Entry b, long nowMs) {
        long waitedA = Math.max(0, nowMs - a.readySinceMs);
        long waitedB = Math.max(0, nowMs - b.readySinceMs);
        int effectiveA = Math.max(1, a.job.priority - (int) (waitedA / agingIntervalMs));
        int effectiveB = Math.max(1, b.job.priority - (int) (waitedB / agingIntervalMs));

        return Comparator.comparingInt((Entry e) -> effectivePriority(e, nowMs))
                .thenComparingLong(e -> e.readySinceMs)
                .thenComparingInt(e -> e.job.sequence)
                .thenComparing(e -> e.job.id)
                .compare(a, b);
    }

    private int effectivePriority(Entry e, long nowMs) {
        long waited = Math.max(0, nowMs - e.readySinceMs);
        return Math.max(1, e.job.priority - (int) (waited / agingIntervalMs));
    }

    public int size() {
        lock.lock();
        try { return entries.size(); } finally { lock.unlock(); }
    }

    public void close() {
        lock.lock();
        try { closed = true; notEmpty.signalAll(); } finally { lock.unlock(); }
    }
}
