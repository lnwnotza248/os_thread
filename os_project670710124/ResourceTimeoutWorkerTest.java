import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ResourceTimeoutWorkerTest {
    private ResourceTimeoutWorkerTest() {
    }

    public static void main(String[] args) throws Exception {
        PriorityPolicy policy = new PriorityPolicy();
        ResourceManager resources = new ResourceManager(1, 1);
        Job job = new Job("TIME01", 0, 1, 1,
                ResourceType.PRINTER, 50, 0);
        policy.put(job);
        resources.acquire(ResourceType.PRINTER);

        long start = System.currentTimeMillis();
        Statistics statistics = new Statistics(start);
        ProjectLogger logger = new ProjectLogger(start);
        List<Job> executionOrder = Collections.synchronizedList(new ArrayList<>());
        Worker workerTask = new Worker(policy, new AtomicInteger(1),
            executionOrder, resources, start, statistics, logger, 10);
        Thread worker = new Thread(workerTask, "timeout-worker");

        worker.start();
        Thread.sleep(100);
        if (statistics.getCompletedJobs() != 0) {
            throw new AssertionError("Job completed while Printer was unavailable");
        }

        resources.release(ResourceType.PRINTER);
        worker.join(3000);
        if (worker.isAlive() || statistics.getCompletedJobs() != 1
                || job.getState() != JobState.COMPLETED) {
            throw new AssertionError("Timed-out job did not recover");
        }

        System.out.println("Worker resource timeout test passed");
    }
}
