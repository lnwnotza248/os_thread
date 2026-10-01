import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/** Resource timeout/cancellation bonus: tryAcquire() ถ้ารอนานเกินกำหนดให้ยกเลิก Job */
public final class TimeoutBonusRunner {
    private TimeoutBonusRunner() {}

    public static void run(String workload, int workers, int printerPermits,
                           int databasePermits, long timeoutMs) throws Exception {
        if (timeoutMs < 0) throw new IllegalArgumentException("timeoutMs ต้องไม่ติดลบ");
        List<Job> jobs = WorkloadLoader.load(workload);
        ProjectLogger logger = new ProjectLogger();
        BlockingQueue<Job> arrival = new LinkedBlockingQueue<>();
        ReadyQueue ready = new ReadyQueue(Config.Policy.PRIORITY);
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        CountDownLatch done = new CountDownLatch(jobs.size());
        java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger cancelledCount = new java.util.concurrent.atomic.AtomicInteger();

        Scheduler scheduler = new Scheduler(arrival, ready, logger);
        Thread generator = new Thread(() -> {
            try {
                for (Job job : jobs) BonusSupport.putAtArrival(job, arrival, logger);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                arrival.offer(BonusSupport.END);
            }
        }, "timeout-generator");

        Thread[] pool = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            final int index = i + 1;
            pool[i] = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Job job = ready.take();
                        if (job == null) return;
                        boolean acquired = false;
                        try {
                            job.setState(JobState.RUNNING);
                            logger.jobStarted(job);
                            Thread.sleep(job.workMs);
                            if (job.resource != ResourceType.NONE) {
                                job.setState(JobState.WAITING_RESOURCE);
                                logger.resourceWaitStarted(job);
                                long waitStart = logger.now();
                                acquired = resources.tryAcquire(job.resource, timeoutMs);
                                if (!acquired) {
                                    cancelledCount.incrementAndGet();
                                    logger.systemEvent("JOB_CANCELLED job=" + job.id + " reason=resource-timeout resource=" + job.resource);
                                    continue;
                                }
                                job.setState(JobState.RUNNING);
                                logger.resourceAcquired(job, logger.now() - waitStart);
                                Thread.sleep(job.resourceMs);
                            }
                            job.setState(JobState.COMPLETED);
                            logger.jobCompleted(job);
                            completedCount.incrementAndGet();
                        } finally {
                            if (acquired) resources.release(job.resource);
                            done.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "timeout-worker-" + index);
        }

        scheduler.start();
        for (Thread t : pool) t.start();
        generator.start();
        generator.join();
        scheduler.join();
        done.await();
        for (Thread t : pool) t.join();
        logger.systemEvent("TIMEOUT_BONUS_DONE completed=" + completedCount.get()
                + " cancelled=" + cancelledCount.get() + " total=" + jobs.size()
                + " timeout=" + timeoutMs + "ms");
    }
}
