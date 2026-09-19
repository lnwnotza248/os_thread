import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class DynamicWorkerPoolTest {
    private DynamicWorkerPoolTest() {
    }

    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < 8; i++) {
            queue.add(i);
        }
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        DynamicWorkerPool pool = new DynamicWorkerPool(1, 3, queue::size, () -> () -> {
            int now = active.incrementAndGet();
            maximum.accumulateAndGet(now, Math::max);
            try {
                Integer item;
                while ((item = queue.poll()) != null) {
                    Thread.sleep(25);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        });
        pool.start();
        while (!queue.isEmpty() || pool.activeWorkers() > 0) {
            Thread.sleep(25);
        }
        pool.close();
        if (maximum.get() < 2 || maximum.get() > 3) {
            throw new AssertionError("Worker pool did not scale within bounds: " + maximum);
        }
        System.out.println("Dynamic worker pool test passed; maxWorkers=" + maximum);
    }
}
