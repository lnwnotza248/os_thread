import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** 3 queue สำหรับ MLFQ: q0 สูงสุด -> q2 ต่ำสุด */
public final class MlfqReadyQueue {
    private final Deque<MlfqJob> q0 = new ArrayDeque<>();
    private final Deque<MlfqJob> q1 = new ArrayDeque<>();
    private final Deque<MlfqJob> q2 = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private boolean closed;

    public void add(MlfqJob job) {
        lock.lock();
        try {
            if (closed) throw new IllegalStateException("MLFQ queue ปิดแล้ว");
            queue(job.level).addLast(job);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public MlfqJob take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (sizeUnsafe() == 0 && !closed) notEmpty.await();
            if (sizeUnsafe() == 0) return null;
            if (!q0.isEmpty()) return q0.removeFirst();
            if (!q1.isEmpty()) return q1.removeFirst();
            return q2.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    private Deque<MlfqJob> queue(int level) {
        return level <= 0 ? q0 : (level == 1 ? q1 : q2);
    }

    private int sizeUnsafe() { return q0.size() + q1.size() + q2.size(); }
    public int size() { lock.lock(); try { return sizeUnsafe(); } finally { lock.unlock(); } }
    public void close() { lock.lock(); try { closed = true; notEmpty.signalAll(); } finally { lock.unlock(); } }
}
