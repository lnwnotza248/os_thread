import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** เปรียบเทียบ platform thread pool กับ virtual threads */
public final class VirtualThreadBonusRunner {
    private VirtualThreadBonusRunner() {}

    public static void run(String workload, int platformWorkers,
                           int printerPermits, int databasePermits) throws Exception {
        List<Job> jobs = WorkloadLoader.load(workload);
        RunResult platform = runWithPlatformThreads(jobs, platformWorkers, printerPermits, databasePermits);
        RunResult virtual = runWithVirtualThreads(jobs, printerPermits, databasePermits);

        System.out.println("=== Virtual Thread Comparison ===");
        System.out.printf("Platform threads : %d workers, %d ms, completed=%d%n",
                platformWorkers, platform.elapsedMs, platform.completed);
        System.out.printf("Virtual threads  : per-job, %d ms, completed=%d%n",
                virtual.elapsedMs, virtual.completed);
    }

    private static RunResult runWithPlatformThreads(List<Job> jobs, int workers,
                                                    int printerPermits, int databasePermits) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        return run(executor, jobs, printerPermits, databasePermits, true);
    }

    private static RunResult runWithVirtualThreads(List<Job> jobs,
                                                   int printerPermits, int databasePermits) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return run(executor, jobs, printerPermits, databasePermits, false);
    }

    private static RunResult run(ExecutorService executor, List<Job> jobs,
                                 int printerPermits, int databasePermits, boolean reusePlatform) throws Exception {
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        CountDownLatch done = new CountDownLatch(jobs.size());
        AtomicInteger completed = new AtomicInteger();
        long start = System.nanoTime();
        for (Job job : jobs) {
            executor.submit(() -> {
                try {
                    waitUntil(start, job.arrivalMs);
                    Thread.sleep(job.workMs);
                    if (job.resource != ResourceType.NONE) {
                        resources.acquire(job.resource);
                        try { Thread.sleep(job.resourceMs); }
                        finally { resources.release(job.resource); }
                    }
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        return new RunResult(elapsed, completed.get());
    }


    private static void waitUntil(long startNanos, long arrivalMs) throws InterruptedException {
        long target = startNanos + arrivalMs * 1_000_000L;
        while (true) {
            long remaining = target - System.nanoTime();
            if (remaining <= 0) return;
            long millis = remaining / 1_000_000L;
            int nanos = (int) (remaining % 1_000_000L);
            Thread.sleep(millis, nanos);
        }
    }

    private record RunResult(long elapsedMs, int completed) {}
}

