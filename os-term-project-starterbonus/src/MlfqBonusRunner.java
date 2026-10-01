import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/** MLFQ bonus: time quantum + remainingWorkMs + requeue */
public final class MlfqBonusRunner {
    private MlfqBonusRunner() {}

    public static void run(String workload, int workers, int printerPermits,
                           int databasePermits, long q0, long q1, long q2) throws Exception {
        if (q0 <= 0 || q1 <= 0 || q2 <= 0) throw new IllegalArgumentException("quantum ต้องมากกว่า 0");
        List<Job> jobs = WorkloadLoader.load(workload);
        ProjectLogger logger = new ProjectLogger();
        BlockingQueue<Job> arrival = new LinkedBlockingQueue<>();
        MlfqReadyQueue ready = new MlfqReadyQueue();
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        CountDownLatch done = new CountDownLatch(jobs.size());
        List<MlfqJob> mlfqJobs = new ArrayList<>();
        for (Job job : jobs) mlfqJobs.add(new MlfqJob(job));

        Thread scheduler = new Thread(() -> {
            try {
                while (true) {
                    Job job = arrival.take();
                    if (job == BonusSupport.END) {
                        // งานใหม่หมดแล้ว แต่ Worker ยังสามารถ requeue งานเดิมได้
                        return;
                    }
                    MlfqJob m = mlfqJobs.stream().filter(x -> x.job == job).findFirst().orElseThrow();
                    m.level = 0;
                    ready.add(m);
                    logger.systemEvent(job.id + " READY MLFQ-q0");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ready.close();
            }
        }, "mlfq-scheduler");

        Thread generator = new Thread(() -> {
            try {
                for (Job job : jobs) BonusSupport.putAtArrival(job, arrival, logger);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                arrival.offer(BonusSupport.END);
            }
        }, "mlfq-generator");

        List<Thread> workersList = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Thread worker = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        MlfqJob m = ready.take();
                        if (m == null) return;
                        Job job = m.job;
                        job.setState(JobState.RUNNING);
                        long quantum = m.level == 0 ? q0 : (m.level == 1 ? q1 : q2);
                        long slice = Math.min(quantum, m.remainingWorkMs);
                        logger.systemEvent(job.id + " MLFQ level=" + m.level + " slice=" + slice + "ms remaining=" + m.remainingWorkMs);
                        Thread.sleep(slice);
                        m.remainingWorkMs -= slice;

                        if (m.remainingWorkMs > 0) {
                            if (m.level < 2) m.level++;
                            ready.add(m);
                            logger.systemEvent(job.id + " REQUEUE MLFQ-q" + m.level + " remaining=" + m.remainingWorkMs + "ms");
                            continue;
                        }

                        if (job.resource != ResourceType.NONE) {
                            job.setState(JobState.WAITING_RESOURCE);
                            long waitStart = logger.now();
                            resources.acquire(job.resource);
                            try {
                                job.setState(JobState.RUNNING);
                                logger.resourceAcquired(job, logger.now() - waitStart);
                                Thread.sleep(job.resourceMs);
                            } finally {
                                resources.release(job.resource);
                                logger.resourceReleased(job);
                            }
                        }
                        job.setState(JobState.COMPLETED);
                        logger.jobCompleted(job);
                        done.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "mlfq-worker-" + (i + 1));
            workersList.add(worker);
        }

        scheduler.start();
        for (Thread t : workersList) t.start();
        generator.start();
        generator.join();
        scheduler.join();
        done.await();
        ready.close();
        for (Thread t : workersList) t.join();
        logger.systemEvent("MLFQ_BONUS_DONE completed=" + jobs.size() + "/" + jobs.size()
                + " quantum=" + q0 + "/" + q1 + "/" + q2 + "ms");
    }
}
