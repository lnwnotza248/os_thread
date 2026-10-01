/**
 * Worker หลายตัวใช้ Ready Queue เดียวร่วมกัน
 * และใช้ ResourceManager ตัวเดียวร่วมกันทั้งระบบ
 */
public class Worker extends Thread {

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
                  Statistics statistics, ProjectLogger logger) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Job job = readyQueue.take();
                if (job == null) {
                    logger.systemEvent(getName() + " shutdown: ready queue drained");
                    return;
                }
                processJob(job);
            }
        } catch (InterruptedException e) {
            // Interrupt is an emergency stop. processJob() releases a resource
            // in finally if this Worker already acquired it.
            Thread.currentThread().interrupt();
            logger.systemEvent(getName() + " interrupted; exiting safely");
        }
    }

    private void processJob(Job job) throws InterruptedException {
        job.setState(JobState.RUNNING);
        logger.jobStarted(job);
        statistics.recordStart(job, logger.now());

        // CPU/work portion: ยังไม่เกี่ยวกับ shared resource
        Thread.sleep(job.workMs);
        logger.workFinished(job);

        if (job.resource == ResourceType.NONE) {
            job.setState(JobState.COMPLETED);
            logger.jobCompleted(job);
            statistics.recordCompletion(job, logger.now());
            return;
        }

        // Resource wait starts immediately before acquire().
        job.setState(JobState.WAITING_RESOURCE);
        logger.resourceWaitStarted(job);
        long waitStart = logger.now();
        statistics.recordResourceWaitStart(job, waitStart);
        boolean acquired = false;
        try {
            resources.acquire(job.resource);
            acquired = true;

            job.setState(JobState.RUNNING);
            long acquiredAt = logger.now();
            long waitedMs = acquiredAt - waitStart;
            statistics.recordResourceAcquired(job, acquiredAt);
            logger.resourceAcquired(job, waitedMs);

            // Simulate using the shared resource while holding its permit.
            Thread.sleep(job.resourceMs);
        } finally {
            // Only release when this Worker actually acquired the permit.
            // This prevents an interrupted acquire() from leaking/inflating permits.
            if (acquired) {
                resources.release(job.resource);
                logger.resourceReleased(job);
            }
        }

        job.setState(JobState.COMPLETED);
        logger.jobCompleted(job);
        statistics.recordCompletion(job, logger.now());
    }
}
