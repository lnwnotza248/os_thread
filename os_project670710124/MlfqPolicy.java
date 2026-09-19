import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/** Three-level feedback queue with FIFO order within each level. */
public final class MlfqPolicy implements SchedulingPolicy {
    private final BlockingQueue<Job>[] queues;
    private final Semaphore availableJobs = new Semaphore(0);
    private final long baseQuantumMs;

    @SuppressWarnings("unchecked")
    public MlfqPolicy(long baseQuantumMs) {
        if (baseQuantumMs < 1) {
            throw new IllegalArgumentException("MLFQ quantum must be positive");
        }
        this.baseQuantumMs = baseQuantumMs;
        queues = new BlockingQueue[] {
            new LinkedBlockingQueue<>(),
            new LinkedBlockingQueue<>(),
            new LinkedBlockingQueue<>()
        };
    }

    @Override
    public void put(Job job) throws InterruptedException {
        queues[job.getMlfqLevel()].put(job);
        availableJobs.release();
    }

    @Override
    public Job take() throws InterruptedException {
        availableJobs.acquire();
        for (BlockingQueue<Job> queue : queues) {
            Job job = queue.poll();
            if (job != null) {
                return job;
            }
        }
        throw new IllegalStateException("MLFQ permit did not match a queued job");
    }

    public long quantumFor(Job job) {
        return baseQuantumMs << job.getMlfqLevel();
    }

    public void demote(Job job) {
        job.setMlfqLevel(job.getMlfqLevel() + 1);
    }

    @Override
    public int size() {
        return queues[0].size() + queues[1].size() + queues[2].size();
    }
}