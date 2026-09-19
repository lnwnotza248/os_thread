import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;

/** Releases jobs into the arrival queue according to their arrival times. */
public final class JobGenerator implements Runnable {
    private final List<Job> jobs;
    private final BlockingQueue<Job> arrivalQueue;
    private final ProjectLogger logger;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> arrivalQueue,
                        ProjectLogger logger) {
        this.jobs = jobs;
        this.arrivalQueue = arrivalQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        long releaseStart = System.currentTimeMillis();
        List<Job> releaseOrder = new ArrayList<>(jobs);
        // Do not mutate the input list; sequence preserves input order for ties.
        releaseOrder.sort(Comparator.comparingLong(Job::getArrivalMs)
                .thenComparingInt(Job::getSequence));
        try {
            for (Job job : releaseOrder) {
                // Release each job relative to the start of this generator.
                long elapsed = System.currentTimeMillis() - releaseStart;
                long waitTime = job.getArrivalMs() - elapsed;
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }

                job.setActualArrivalTime(System.currentTimeMillis());// Record the actual arrival time of the job
                job.setState(JobState.ARRIVED);
                logger.event(job.getId() + " ARRIVED");
                // The scheduler blocks on this queue until a job arrives.
                arrivalQueue.put(job);// Put the job into the arrival queue
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
