import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** First-come, first-served policy backed by a FIFO blocking queue. */
public final class FcfsPolicy implements SchedulingPolicy {
    private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();

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
