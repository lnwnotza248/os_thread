import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ready Queue ที่รองรับหลาย Worker อย่างปลอดภัย
 *
 */
public class ReadyQueue {

    private final Config.Policy policy;
    private final List<Job> jobs = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private boolean closed = false;

    private final Comparator<Job> priorityComparator =
            Comparator.comparingInt((Job job) -> job.priority)
                    .thenComparingInt(job -> job.sequence)
                    .thenComparing(job -> job.id);

    public ReadyQueue(Config.Policy policy) {
        this.policy = policy;
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("job ห้ามเป็น null");
        }
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("ReadyQueue ถูกปิดแล้ว");
            }
            jobs.add(job);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * หยิบงานถัดไปตามนโยบาย
     * ถ้ายังไม่มีงานจะรอด้วย Condition ไม่ busy wait
     * ถ้าคิวถูกปิดและไม่มีงานเหลือ จะคืน null
     */
    public Job take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (jobs.isEmpty() && !closed) {
                notEmpty.await();
            }

            if (jobs.isEmpty()) {
                return null;
            }

            int selectedIndex = 0;
            if (policy == Config.Policy.PRIORITY) {
                for (int i = 1; i < jobs.size(); i++) {
                    if (priorityComparator.compare(jobs.get(i), jobs.get(selectedIndex)) < 0) {
                        selectedIndex = i;
                    }
                }
            }
            return jobs.remove(selectedIndex);
        } finally {
            lock.unlock();
        }
    }

    /** จำนวนงานที่รออยู่ตอนนี้ */
    public int size() {
        lock.lock();
        try {
            return jobs.size();
        } finally {
            lock.unlock();
        }
    }

    /** ปิดคิวและปลุก Worker ที่กำลังรออยู่ */
    public void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }
}
