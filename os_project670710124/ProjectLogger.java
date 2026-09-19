/** Serializes event output and adds simulation time and thread identity. */
public final class ProjectLogger {
    private final long simulationStart;

    public ProjectLogger(long simulationStart) {
        this.simulationStart = simulationStart;
    }

    public synchronized void event(String message) {
        long elapsed = System.currentTimeMillis() - simulationStart;
        System.out.printf("[%04d ms] [%s] %s%n",
                elapsed, Thread.currentThread().getName(), message);
    }
}
