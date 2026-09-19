import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class CancellationTest {
    private CancellationTest() {
    }

    public static void main(String[] args) throws Exception {
        PriorityPolicy policy = new PriorityPolicy();
        Job job = new Job("CANCEL01", 0, 1, 25, ResourceType.NONE, 0, 0);
        job.cancel();
        policy.put(job);
        long start = System.currentTimeMillis();
        Statistics statistics = new Statistics(start);
        statistics.recordStart();
        Worker workerTask = new Worker(policy, new AtomicInteger(1),
                Collections.synchronizedList(new ArrayList<>()),
                new ResourceManager(1, 1), start, statistics,
                new ProjectLogger(start));
        Thread worker = new Thread(workerTask, "cancel-worker");
        worker.start();
        worker.join(1000);
        if (worker.isAlive() || job.getState() != JobState.CANCELLED
                || statistics.getCancelledJobs() != 1
            || statistics.getCompletedJobs() != 0
            || statistics.getRunningJobs() != 1) {
            throw new AssertionError("Cancelled job was not discarded safely");
        }
        System.out.println("Cancellation test passed");
    }
}
