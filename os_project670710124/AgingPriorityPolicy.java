import java.util.ArrayList;
import java.util.List;

/** Priority policy with one effective-priority improvement per second of waiting. */
public final class AgingPriorityPolicy implements SchedulingPolicy {
    private static final long AGING_INTERVAL_MS = 1000;

    private final List<QueuedJob> queue = new ArrayList<>();

    @Override
    public synchronized void put(Job job) throws InterruptedException {
        queue.add(new QueuedJob(job, System.currentTimeMillis()));
        notifyAll();
    }

    @Override
    public synchronized Job take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }

        long now = System.currentTimeMillis();
        int selectedIndex = 0;
        for (int i = 1; i < queue.size(); i++) {
            if (compare(queue.get(i), queue.get(selectedIndex), now) < 0) {
                selectedIndex = i;
            }
        }
        return queue.remove(selectedIndex).job;
    }

    @Override
    public synchronized int size() {
        return queue.size();
    }

    private int compare(QueuedJob left, QueuedJob right, long now) {
        int priorityOrder = Integer.compare(effectivePriority(left, now),
                effectivePriority(right, now));
        if (priorityOrder != 0) {
            return priorityOrder;
        }
        return Integer.compare(left.job.getSequence(), right.job.getSequence());
    }

    private int effectivePriority(QueuedJob queuedJob, long now) {
        long waitedMs = Math.max(0, now - queuedJob.queuedAt);
        long agingSteps = waitedMs / AGING_INTERVAL_MS;
        return (int) Math.max(1, queuedJob.job.getPriority() - agingSteps);
    }

    private static final class QueuedJob {
        private final Job job;
        private final long queuedAt;

        private QueuedJob(Job job, long queuedAt) {
            this.job = job;
            this.queuedAt = queuedAt;
        }
    }
}
