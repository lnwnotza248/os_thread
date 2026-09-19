/** Periodically reports ready, running, completed, and resource usage. */
public final class Monitor implements Runnable {
    private final SchedulingPolicy policy;
    private final Statistics statistics;
    private final ResourceManager resources;
    private final ProjectLogger logger;
    private final long intervalMs;

    public Monitor(SchedulingPolicy policy, Statistics statistics,
                   ResourceManager resources, ProjectLogger logger,
                   long intervalMs) {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("Monitor interval must be positive");
        }
        this.policy = policy;
        this.statistics = statistics;
        this.resources = resources;
        this.logger = logger;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        int lastReady = -1;
        int lastRunning = -1;
        int lastCompleted = -1;
        int lastPrinterUsed = -1;
        int lastDatabaseUsed = -1;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervalMs);
                // Avoid duplicate snapshots when no monitored value changed.
                int ready = policy.size();
                int running = statistics.getRunningJobs();
                int completed = statistics.getCompletedJobs();
                int printerUsed = resources.usedPermits(ResourceType.PRINTER);
                int databaseUsed = resources.usedPermits(ResourceType.DATABASE);
                if (ready != lastReady || running != lastRunning
                    || completed != lastCompleted
                    || printerUsed != lastPrinterUsed
                    || databaseUsed != lastDatabaseUsed) {
                    logger.event("STATUS ready=" + ready
                        + " running=" + running
                        + " completed=" + completed
                        + " printer=" + printerUsed + "/"
                        + resources.totalPermits(ResourceType.PRINTER)
                        + " database=" + databaseUsed + "/"
                        + resources.totalPermits(ResourceType.DATABASE));
                    lastReady = ready;
                    lastRunning = running;
                    lastCompleted = completed;
                    lastPrinterUsed = printerUsed;
                    lastDatabaseUsed = databaseUsed;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
