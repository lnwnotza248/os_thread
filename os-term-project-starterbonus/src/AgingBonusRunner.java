import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/** Aging bonus: Priority + รอครบช่วงแล้ว effective priority ลดลง */
public final class AgingBonusRunner {
    private AgingBonusRunner() {}

    public static void run(String workload, int workers, int printerPermits,
                           int databasePermits, long agingIntervalMs) throws Exception {
        List<Job> jobs = WorkloadLoader.load(workload);
        ProjectLogger logger = new ProjectLogger();
        BlockingQueue<Job> arrival = new LinkedBlockingQueue<>();
        AgingReadyQueue ready = new AgingReadyQueue(agingIntervalMs);
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        CountDownLatch done = new CountDownLatch(jobs.size());
        List<Thread> threads = new ArrayList<>();

        Thread scheduler = new Thread(() -> {
            try {
                while (true) {
                    Job job = arrival.take();
                    if (job == BonusSupport.END) {
                        ready.close();
                        return;
                    }
                    ready.add(job, logger.now());
                    logger.systemEvent(job.id + " READY (aging)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ready.close();
            }
        }, "aging-scheduler");

        Thread generator = new Thread(() -> {
            try {
                for (Job job : jobs) BonusSupport.putAtArrival(job, arrival, logger);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                arrival.offer(BonusSupport.END);
            }
        }, "aging-generator");

        for (int i = 0; i < workers; i++) {
            Thread worker = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Job job = ready.take(logger.now());
                        if (job == null) return;
                        job.setState(JobState.RUNNING);
                        logger.jobStarted(job);
                        Thread.sleep(job.workMs);
                        if (job.resource != ResourceType.NONE) {
                            job.setState(JobState.WAITING_RESOURCE);
                            logger.resourceWaitStarted(job);
                            boolean acquired = false;
                            long waitStart = logger.now();
                            try {
                                resources.acquire(job.resource);
                                acquired = true;
                                job.setState(JobState.RUNNING);
                                logger.resourceAcquired(job, logger.now() - waitStart);
                                Thread.sleep(job.resourceMs);
                            } finally {
                                if (acquired) resources.release(job.resource);
                            }
                        }
                        job.setState(JobState.COMPLETED);
                        logger.jobCompleted(job);
                        done.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "aging-worker-" + (i + 1));
            threads.add(worker);
        }

        scheduler.start();
        for (Thread t : threads) t.start();
        generator.start();
        generator.join();
        scheduler.join();
        done.await();
        for (Thread t : threads) t.join();
        logger.systemEvent("AGING_BONUS_DONE completed=" + jobs.size() + "/" + jobs.size()
                + " agingInterval=" + agingIntervalMs + "ms");
    }
}
