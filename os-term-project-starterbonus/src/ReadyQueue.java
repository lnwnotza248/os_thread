
/**
 * คิวงานที่พร้อมถูกหยิบไปทำ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * สิ่งที่คลาสนี้ต้องทำได้:
 *   - เก็บงานที่รอ Worker อยู่
 *   - หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority)
 *   - ถูกเรียกจากหลาย Thread พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 4: priority = 1 สูงสุด เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break)
 *     ที่ตัดสินจากข้อมูลของ Job ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน
 *   - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU (busy waiting) — Worker ที่ไม่มีงานทำ
 *     ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReadyQueue {

    private static final long AGING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long[] MLFQ_QUANTA_MS = {100, 200, 400};

    private final Config.Policy policy;
    private final boolean agingEnabled;
    private final List<QueuedJob> queue = new ArrayList<>();
    private final List<List<Job>> mlfqQueues = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public ReadyQueue(Config.Policy policy) {
        this(policy, false);
    }

    public ReadyQueue(Config.Policy policy, boolean agingEnabled) {
        this.policy = policy;
        this.agingEnabled = agingEnabled;
        for (int i = 0; i < MLFQ_QUANTA_MS.length; i++) {
            mlfqQueues.add(new ArrayList<>());
        }
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        lock.lock();
        try {
            if (policy == Config.Policy.MLFQ) {
                job.remainingWorkMs = job.workMs;
                job.mlfqLevel = 0;
                mlfqQueues.get(0).add(job);
            } else {
                queue.add(new QueuedJob(job, System.nanoTime()));
            }
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * ถ้ายังไม่มีงาน ต้องรอโดยไม่กิน CPU
     * ต้องคิดด้วยว่าจะบอก Worker อย่างไรเมื่อไม่มีงานเหลือแล้วและควรหยุดทำงาน
     */
    public Job take() throws InterruptedException {
        return take(0);
    }

    /** Timed take used by the dynamic worker pool to check retirement requests. */
    public Job take(long timeoutMs) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (isEmpty()) {
                if (timeoutMs <= 0) {
                    notEmpty.await();
                } else if (notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS) == false) {
                    return null;
                }
            }

            if (policy == Config.Policy.MLFQ) {
                for (List<Job> levelQueue : mlfqQueues) {
                    if (!levelQueue.isEmpty()) {
                        return levelQueue.remove(0);
                    }
                }
            }
            int nextIndex = policy == Config.Policy.FCFS ? 0 : findHighestPriorityJob();
            return queue.remove(nextIndex).job;
        } finally {
            lock.unlock();
        }
    }

    /** จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor — ต้องอ่านได้อย่างปลอดภัย */
    public int size() {
        lock.lock();
        try {
            if (policy != Config.Policy.MLFQ) {
                return queue.size();
            }
            int count = 0;
            for (List<Job> levelQueue : mlfqQueues) {
                count += levelQueue.size();
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isMlfq() {
        return policy == Config.Policy.MLFQ;
    }

    public long timeQuantumMs(Job job) {
        if (!isMlfq()) {
            throw new IllegalStateException("Time quantum is only defined for MLFQ");
        }
        return MLFQ_QUANTA_MS[job.mlfqLevel];
    }

    /** Requeues unfinished work at the tail of its demoted MLFQ level. */
    public int requeue(Job job, long remainingWorkMs, ProjectLogger logger) {
        if (!isMlfq()) {
            throw new IllegalStateException("Only MLFQ jobs can be requeued");
        }
        lock.lock();
        try {
            job.remainingWorkMs = remainingWorkMs;
            job.mlfqLevel = Math.min(job.mlfqLevel + 1, MLFQ_QUANTA_MS.length - 1);
            logger.jobRequeued(job, job.mlfqLevel, remainingWorkMs);
            mlfqQueues.get(job.mlfqLevel).add(job);
            notEmpty.signal();
            return job.mlfqLevel;
        } finally {
            lock.unlock();
        }
    }

    private boolean isEmpty() {
        if (policy != Config.Policy.MLFQ) {
            return queue.isEmpty();
        }
        for (List<Job> levelQueue : mlfqQueues) {
            if (!levelQueue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int findHighestPriorityJob() {
        long now = System.nanoTime();
        int bestIndex = 0;
        QueuedJob best = queue.get(0);
        long bestPriority = effectivePriority(best, now);

        for (int i = 1; i < queue.size(); i++) {
            QueuedJob candidate = queue.get(i);
            long candidatePriority = effectivePriority(candidate, now);
            if (candidatePriority < bestPriority
                    || (candidatePriority == bestPriority
                    && candidate.job.sequence < best.job.sequence)) {
                bestIndex = i;
                best = candidate;
                bestPriority = candidatePriority;
            }
        }
        return bestIndex;
    }

    private long effectivePriority(QueuedJob queuedJob, long now) {
        if (!agingEnabled) {
            return queuedJob.job.priority;
        }
        long agingLevels = (now - queuedJob.enqueuedNanos) / AGING_INTERVAL_NANOS;
        // This is an internal ranking score; the Job's original priority is unchanged.
        return (long) queuedJob.job.priority - agingLevels;
    }

    private static final class QueuedJob {
        private final Job job;
        private final long enqueuedNanos;

        private QueuedJob(Job job, long enqueuedNanos) {
            this.job = job;
            this.enqueuedNanos = enqueuedNanos;
        }
    }
}
