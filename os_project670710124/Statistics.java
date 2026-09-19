/** Thread-safe aggregate metrics for the completed workload. */
public final class Statistics {
    private final long runStartTime;
    private int completedJobs;
    private int cancelledJobs;
    private int runningJobs;
    private int resourceJobs;
    private long totalWaitingMs;
    private long totalTurnaroundMs;
    private long totalResourceWaitMs;
    private long lastCompletionTime;

    public Statistics(long runStartTime) {
        this.runStartTime = runStartTime;
    }

    public synchronized void recordCompletion(Job job) {
        // WT = start - arrival; TAT = completion - arrival.
        completedJobs++;
        runningJobs--;
        totalWaitingMs += job.getStartTime() - job.getActualArrivalTime();
        totalTurnaroundMs += job.getCompletionTime() - job.getActualArrivalTime();
        lastCompletionTime = Math.max(lastCompletionTime, job.getCompletionTime());
        if (job.getResource() != ResourceType.NONE) {
            // Resource wait is measured only for jobs that use a resource.
            resourceJobs++;
            totalResourceWaitMs += job.getResourceAcquiredTime()
                    - job.getResourceWaitStartTime();
        }
    }

    public synchronized void recordStart() {
        runningJobs++;
    }

    public synchronized void recordCancellation() {
        cancelledJobs++;
    }

    public synchronized void recordRequeue() {
        if (runningJobs > 0) {
            runningJobs--;
        }
    }

    public synchronized int getRunningJobs() {
        return runningJobs;
    }

    public synchronized int getCompletedJobs() {
        return completedJobs;
    }

    public synchronized int getCancelledJobs() {
        return cancelledJobs;
    }

    public synchronized double getAverageTurnaroundMs() {
        return completedJobs == 0 ? 0.0 : totalTurnaroundMs / (double) completedJobs;
    }

    public synchronized double getAverageWaitingMs() {
        return completedJobs == 0 ? 0.0 : totalWaitingMs / (double) completedJobs;
    }

    public synchronized double getAverageResourceWaitMs() {
        return resourceJobs == 0 ? 0.0 : totalResourceWaitMs / (double) resourceJobs;
    }

    public synchronized double getThroughputJobsPerSecond() {
        long elapsedMs = lastCompletionTime - runStartTime;
        return elapsedMs <= 0 ? 0.0 : completedJobs * 1000.0 / elapsedMs;
    }
}
