import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Deadlock bonus: หลาย resource แต่บังคับ order เดียวกัน */
public final class DeadlockBonusRunner {
    private DeadlockBonusRunner() {}

    public static void run() throws Exception {
        ProjectLogger logger = new ProjectLogger();
        DeadlockSafeResourceManager resources = new DeadlockSafeResourceManager(1, 1);
        List<MultiResourceJob> jobs = Arrays.asList(
                new MultiResourceJob("MR01", Arrays.asList(ResourceType.PRINTER, ResourceType.DATABASE), 250, 600),
                new MultiResourceJob("MR02", Arrays.asList(ResourceType.DATABASE, ResourceType.PRINTER), 250, 600)
        );
        CountDownLatch done = new CountDownLatch(jobs.size());

        int workerNo = 1;
        for (MultiResourceJob job : jobs) {
            final String workerName = "deadlock-worker-" + workerNo++;
            Thread.ofVirtual().name(workerName).start(() -> {
                try {
                    logger.systemEvent(job.id + " requests=" + job.resources);
                    Thread.sleep(job.workMs);
                    List<ResourceType> acquired = resources.acquireAll(job.resources);
                    try {
                        logger.systemEvent(job.id + " acquired-in-safe-order=" + acquired);
                        Thread.sleep(job.resourceMs);
                    } finally {
                        resources.releaseAll(acquired);
                        logger.systemEvent(job.id + " released=" + acquired);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();
        logger.systemEvent("DEADLOCK_BONUS_DONE all multi-resource jobs completed");
    }
}
