import java.util.concurrent.PriorityBlockingQueue;

/** Priority policy: lower priority values run first; sequence breaks ties. */
public final class PriorityPolicy implements SchedulingPolicy {
    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>(
            11,
            (left, right) -> {
                // Lower values are higher priority; sequence makes ties deterministic.
                int priorityOrder = Integer.compare(left.getPriority(), right.getPriority());
                if (priorityOrder != 0) {
                    return priorityOrder;
                }
                return Integer.compare(left.getSequence(), right.getSequence());
            });

    @Override
    public void put(Job job) throws InterruptedException {
        queue.put(job);
    }

    @Override
    public Job take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
