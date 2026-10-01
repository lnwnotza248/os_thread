import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/** Dynamic Worker bonus */
public final class DynamicWorkerBonusRunner {
    private DynamicWorkerBonusRunner() {}

    public static void run(String workload, int minWorkers, int maxWorkers,
                           int printerPermits, int databasePermits, int threshold) throws Exception {
        List<Job> jobs = WorkloadLoader.load(workload);
        ProjectLogger logger = new ProjectLogger();
        BlockingQueue<Job> arrival = new LinkedBlockingQueue<>();
        ReadyQueue ready = new ReadyQueue(Config.Policy.PRIORITY);
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        CountDownLatch done = new CountDownLatch(jobs.size());

        Thread scheduler = new Thread(() -> {
            try {
                while (true) {
                    Job job = arrival.take();
                    if (job == BonusSupport.END) return;
                    job.setState(JobState.READY);
                    ready.add(job);
                    logger.systemEvent(job.id + " READY");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "dynamic-scheduler");

        Thread generator = new Thread(() -> {
            try {
                for (Job job : jobs) BonusSupport.putAtArrival(job, arrival, logger);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                arrival.offer(BonusSupport.END);
            }
        }, "dynamic-generator");

        DynamicWorkerPool pool = new DynamicWorkerPool(ready, resources, logger, done,
                minWorkers, maxWorkers, threshold);
        pool.start();
        scheduler.start();
        generator.start();
        generator.join();
        scheduler.join();
        done.await();
        Thread.sleep(500L);
        ready.close();
        pool.shutdown();
        pool.awaitWorkers();
        logger.systemEvent("DYNAMIC_WORKER_BONUS_DONE completed=" + jobs.size() + "/" + jobs.size());
    }
}
