import java.util.concurrent.BlockingQueue;

/** Moves arrived jobs from the arrival queue into the selected ready policy. */
public final class Scheduler implements Runnable {
    private final BlockingQueue<Job> arrivalQueue;
    private final SchedulingPolicy policy;
    private final int jobCount;
    private final ProjectLogger logger;

    public Scheduler(BlockingQueue<Job> arrivalQueue,
                     SchedulingPolicy policy,
                     int jobCount, ProjectLogger logger) {
        if (jobCount < 0) {
            throw new IllegalArgumentException("Job count cannot be negative");
        }
        this.arrivalQueue = arrivalQueue;
        this.policy = policy;
        this.jobCount = jobCount;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < jobCount; i++) {
                Job job = arrivalQueue.take();
                // Only jobs that have arrived can enter the ready policy.
                job.setState(JobState.READY);
                logger.event(job.getId() + " READY");
                policy.put(job);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
