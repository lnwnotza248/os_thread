import java.util.concurrent.BlockingQueue;

final class BonusSupport {
    private BonusSupport() {
    }

    static final Job END = JobGenerator.END_OF_INPUT;

    static void waitUntilArrival(ProjectLogger logger, long arrivalMs) throws InterruptedException {
        while (true) {
            long remaining = arrivalMs - logger.now();
            if (remaining <= 0) return;
            Thread.sleep(Math.min(remaining, 50L));
        }
    }

    static void putAtArrival(Job job, BlockingQueue<Job> queue,
                             ProjectLogger logger) throws InterruptedException {
        waitUntilArrival(logger, job.arrivalMs);
        job.setState(JobState.ARRIVED);
        logger.jobArrived(job);
        queue.put(job);
    }
}
