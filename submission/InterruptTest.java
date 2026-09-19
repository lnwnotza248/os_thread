import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class InterruptTest {
    private InterruptTest() {
    }

    public static void main(String[] args) throws Exception {
        PriorityPolicy policy = new PriorityPolicy();
        ResourceManager resources = new ResourceManager(1, 1);
        Job job = new Job("INT01", 0, 1, 1,
                ResourceType.PRINTER, 1000, 0);
        policy.put(job);

        resources.acquire(ResourceType.PRINTER);
        long start = System.currentTimeMillis();
        Statistics statistics = new Statistics(start);
        ProjectLogger logger = new ProjectLogger(start);
        List<Job> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch waiting = new CountDownLatch(1);
        Worker workerTask = new Worker(policy, new AtomicInteger(1),
            executionOrder, resources, start, statistics, logger, waiting);
        Thread worker = new Thread(workerTask, "interrupt-worker");

        worker.start();
        if (!waiting.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("Worker did not reach resource wait");
        }
        worker.interrupt();

        if (!worker.isAlive()) {
            throw new AssertionError("Worker stopped instead of retrying the job");
        }
        if (statistics.getCompletedJobs() != 0) {
            throw new AssertionError("Interrupted job was marked completed");
        }
        if (resources.availablePermits(ResourceType.PRINTER) != 0) {
            throw new AssertionError("Existing Printer holder lost its permit");
        }

        resources.release(ResourceType.PRINTER);
        worker.join(3000);

        if (worker.isAlive() || statistics.getCompletedJobs() != 1
                || job.getState() != JobState.COMPLETED
                || resources.availablePermits(ResourceType.PRINTER) != 1) {
            throw new AssertionError("Requeued job did not complete safely");
        }

        System.out.println("Interrupt recovery test passed");
    }
}
