import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Worker ที่สามารถถูกเพิ่ม/ลดจาก pool ได้ */
public final class DynamicWorker extends Thread {
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final ProjectLogger logger;
    private final CountDownLatch completed;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile boolean idle = true;

    public DynamicWorker(String name, ReadyQueue readyQueue, ResourceManager resources,
                         ProjectLogger logger, CountDownLatch completed) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.logger = logger;
        this.completed = completed;
    }

    public void requestStop() {
        stopRequested.set(true);
        interrupt();
    }

    public boolean isIdle() { return idle; }

    @Override
    public void run() {
        try {
            while (!stopRequested.get()) {
                Job job = readyQueue.take();
                if (job == null) return;
                idle = false;
                process(job);
                idle = true;
            }
        } catch (InterruptedException e) {
            if (!stopRequested.get()) Thread.currentThread().interrupt();
        } finally {
            idle = true;
        }
    }

    private void process(Job job) throws InterruptedException {
        job.setState(JobState.RUNNING);
        logger.jobStarted(job);
        Thread.sleep(job.workMs);
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
        completed.countDown();
    }
}
