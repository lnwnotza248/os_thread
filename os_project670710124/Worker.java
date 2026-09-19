import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes ready jobs and coordinates shared resource usage. */
public final class Worker implements Runnable, WorkerControl {
    private final SchedulingPolicy policy;
    private final AtomicInteger remainingJobs;
    private final List<Job> executionOrder;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private final long resourceTimeoutMs;
    private volatile boolean stopRequested;
    private volatile boolean busy;

    public Worker(SchedulingPolicy policy, AtomicInteger remainingJobs,
                  List<Job> executionOrder, ResourceManager resources,
                  long runStartTime, Statistics statistics,
                  ProjectLogger logger) {
            this(policy, remainingJobs, executionOrder, resources, runStartTime,
                statistics, logger, -1);
            }

            public Worker(SchedulingPolicy policy, AtomicInteger remainingJobs,
                  List<Job> executionOrder, ResourceManager resources,
                  long runStartTime, Statistics statistics,
                  ProjectLogger logger, long resourceTimeoutMs) {
        this.policy = policy;
        this.remainingJobs = remainingJobs;
        this.executionOrder = executionOrder;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
        if (resourceTimeoutMs < -1) {
            throw new IllegalArgumentException("Resource timeout cannot be negative");
        }
        this.resourceTimeoutMs = resourceTimeoutMs;
    }

    @Override
    public void run() {
        try {
            while (reserveJob()) {
                // A job is reserved before take(); interruption requeues it below.
                Job job;
                try {
                    job = policy.take();
                } catch (InterruptedException exception) {
                    remainingJobs.incrementAndGet();
                    if (stopRequested) {
                        return;
                    }
                    throw exception;
                }
                busy = true;
                synchronized (executionOrder) {
                    if (!executionOrder.contains(job)) {
                        executionOrder.add(job);
                    }
                }
                try {
                    if (job.isCancelled()) {
                        cancel(job);
                        continue;
                    }
                    boolean completed = process(job);
                    if (!completed) {
                        statistics.recordRequeue();
                        remainingJobs.incrementAndGet();
                        policy.put(job);
                        logger.event("QUANTUM_EXPIRE job=" + job.getId()
                                + " level=" + job.getMlfqLevel()
                                + " remaining=" + job.getRemainingWorkMs() + "ms");
                    }
                } catch (ResourceTimeoutException exception) {
                    requeue(job, "TIMEOUT");
                } catch (InterruptedException exception) {
                    if (stopRequested) {
                        requeue(job, "RETIRE");
                        return;
                    }
                    requeue(job, "REQUEUE");
                } finally {
                    busy = false;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isIdle() {
        return !busy;
    }

    @Override
    public void requestStop() {
        stopRequested = true;
    }

    private void cancel(Job job) {
        job.setState(JobState.CANCELLED);
        statistics.recordCancellation();
        logger.event("CANCEL job=" + job.getId());
    }

    private boolean reserveJob() {
        while (true) {
            int available = remainingJobs.get();
            if (available <= 0) {
                return false;
            }
            if (remainingJobs.compareAndSet(available, available - 1)) {
                return true;
            }
        }
    }

    private void requeue(Job job, String event) throws InterruptedException {
        job.setState(JobState.READY);
        statistics.recordRequeue();
        remainingJobs.incrementAndGet();
        policy.put(job);
        logger.event(event + " job=" + job.getId());
    }

    private boolean process(Job job) throws InterruptedException, ResourceTimeoutException {
        if (job.isCancelled()) {
            cancel(job);
            return true;
        }
        job.setState(JobState.RUNNING);
        if (job.getStartTime() == 0) {
            job.setStartTime(System.currentTimeMillis());
            statistics.recordStart();
            logger.event(job.getId() + " START cpu=" + job.getWorkMs() + "ms");
        } else {
            statistics.recordStart();
        }
        if (job.getRemainingWorkMs() > 0) {
            long sliceMs = job.getRemainingWorkMs();
            if (policy instanceof MlfqPolicy mlfq) {
                sliceMs = Math.min(sliceMs, mlfq.quantumFor(job));
            }
            Thread.sleep(sliceMs);
            job.setRemainingWorkMs(job.getRemainingWorkMs() - sliceMs);
            if (job.getRemainingWorkMs() > 0) {
                if (policy instanceof MlfqPolicy mlfq) {
                    mlfq.demote(job);
                }
                return false;
            }
        }
        useResource(job);
        job.setCompletionTime(System.currentTimeMillis());
        job.setState(JobState.COMPLETED);
        statistics.recordCompletion(job);
        logger.event(job.getId() + " COMPLETE");
        return true;
    }

    private void useResource(Job job) throws InterruptedException, ResourceTimeoutException {
        if (job.getResources().stream().allMatch(resource -> resource == ResourceType.NONE)) {
            return;
        }
        job.setState(JobState.WAITING_RESOURCE);
        job.setResourceWaitStartTime(System.currentTimeMillis());
        boolean acquired = false;
        try {
            // WAIT is logged before acquire; ACQUIRE is logged only after a permit exists.
                logger.event(job.getId() + " WAIT resource=" + job.getResource());
            if (resourceTimeoutMs < 0) {
                resources.acquireAll(job.getResources());
                acquired = true;
            } else {
                acquired = resources.tryAcquireAll(job.getResources(), resourceTimeoutMs);
                if (!acquired) {
                    logger.event(job.getId() + " TIMEOUT resource=" + job.getResources()
                            + " after=" + resourceTimeoutMs + "ms");
                    throw new ResourceTimeoutException();
                }
            }
            job.setState(JobState.RUNNING);
            job.setResourceAcquiredTime(System.currentTimeMillis());
                    logger.event(job.getId() + " ACQUIRE resource=" + job.getResources()
                    + " wait=" + (job.getResourceAcquiredTime()
                    - job.getResourceWaitStartTime()) + "ms");
            Thread.sleep(job.getResourceMs());
        } finally {
            if (acquired) {
                resources.releaseAll(job.getResources());
                logger.event(job.getId() + " RELEASE resource=" + job.getResources());
            }
        }
    }

    private static final class ResourceTimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
