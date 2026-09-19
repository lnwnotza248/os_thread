import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

/** Executes ready jobs and coordinates shared resource usage. */
public final class Worker implements Runnable {
    private final SchedulingPolicy policy;
    private final AtomicInteger remainingJobs;
    private final List<Job> executionOrder;
    private final ResourceManager resources;
    private final long runStartTime;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private final CountDownLatch resourceWaitSignal;

    public Worker(SchedulingPolicy policy, AtomicInteger remainingJobs,
                  List<Job> executionOrder, ResourceManager resources,
                  long runStartTime, Statistics statistics,
                  ProjectLogger logger) {
            this(policy, remainingJobs, executionOrder, resources, runStartTime,
                statistics, logger, null);
            }

            public Worker(SchedulingPolicy policy, AtomicInteger remainingJobs,
                  List<Job> executionOrder, ResourceManager resources,
                  long runStartTime, Statistics statistics,
                  ProjectLogger logger, CountDownLatch resourceWaitSignal) {
        this.policy = policy;
        this.remainingJobs = remainingJobs;
        this.executionOrder = executionOrder;
        this.resources = resources;
        this.runStartTime = runStartTime;
        this.statistics = statistics;
        this.logger = logger;
        this.resourceWaitSignal = resourceWaitSignal;
    }

    @Override
    public void run() {
        try {
            while (remainingJobs.getAndDecrement() > 0) {
                // A job is reserved before take(); interruption requeues it below.
                Job job = policy.take();
                try {
                    process(job);
                    executionOrder.add(job);
                } catch (InterruptedException exception) {
                    job.setState(JobState.READY);
                    statistics.recordRequeue();
                    remainingJobs.incrementAndGet();
                    policy.put(job);
                    logger.event("REQUEUE job=" + job.getId());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void process(Job job) throws InterruptedException {
        job.setState(JobState.RUNNING);
        job.setStartTime(System.currentTimeMillis());
        statistics.recordStart();
        logger.event(job.getId() + " START cpu=" + job.getWorkMs() + "ms");
        Thread.sleep(job.getWorkMs());
        useResource(job);
        job.setCompletionTime(System.currentTimeMillis());
        job.setState(JobState.COMPLETED);
        statistics.recordCompletion(job);
        logger.event(job.getId() + " COMPLETE");
    }

    private void useResource(Job job) throws InterruptedException {
        if (job.getResource() == ResourceType.NONE) {
            return;
        }
        job.setState(JobState.WAITING_RESOURCE);
        job.setResourceWaitStartTime(System.currentTimeMillis());
        boolean acquired = false;
        try {
            // WAIT is logged before acquire; ACQUIRE is logged only after a permit exists.
                logger.event(job.getId() + " WAIT resource=" + job.getResource());
                if (resourceWaitSignal != null) {
                    resourceWaitSignal.countDown();
                }
            resources.acquire(job.getResource());
            acquired = true;
            job.setState(JobState.RUNNING);
            job.setResourceAcquiredTime(System.currentTimeMillis());
                logger.event(job.getId() + " ACQUIRE resource=" + job.getResource()
                    + " wait=" + (job.getResourceAcquiredTime()
                    - job.getResourceWaitStartTime()) + "ms");
            Thread.sleep(job.getResourceMs());
        } finally {
            if (acquired) {
                resources.release(job.getResource());
                logger.event(job.getId() + " RELEASE resource=" + job.getResource());
            }
        }
    }
}
