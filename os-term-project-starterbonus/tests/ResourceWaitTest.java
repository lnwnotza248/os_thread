import java.io.*;
import java.util.concurrent.*;

public class ResourceWaitTest {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }
    public static void main(String[] args) throws Exception {
        for (String mode : new String[]{"slow-log", "timeout", "interrupt"}) {
            CountDownLatch entering = new CountDownLatch(1);
            ResourceManager resources = new ResourceManager(1, 1) {
                public void acquire(ResourceType type) throws InterruptedException {
                    entering.countDown(); super.acquire(type);
                }
            };
            boolean held = !mode.equals("slow-log");
            if (held) resources.tryAcquire(ResourceType.PRINTER, 10);
            PrintStream output = new PrintStream(OutputStream.nullOutputStream()) {
                public void println(String line) {
                    if (mode.equals("slow-log") && line.contains("RESOURCE_WAIT")) {
                        try { Thread.sleep(300); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
            };
            ProjectLogger logger = new ProjectLogger(output);
            Statistics stats = new Statistics(1);
            ReadyQueue queue = new ReadyQueue(Config.Policy.FCFS);
            Job job = new Job("test", 0, 1, 0, ResourceType.PRINTER, 1, 0);
            job.actualArrivalTime = logger.now(); queue.add(job);
            Worker worker = new Worker("test", queue, resources, stats, logger,
                    mode.equals("timeout") ? 100 : 0, false);
            worker.setDaemon(true);
            Thread waiter = new Thread(() -> {
                try { stats.awaitAllCompleted(); } catch (InterruptedException e) { }
            });
            waiter.setDaemon(true); worker.start(); waiter.start();
            try {
                if (mode.equals("interrupt")) {
                    check(entering.await(2, TimeUnit.SECONDS), "Reached acquire");
                    Thread.sleep(120); worker.interrupt();
                }
                waiter.join(3000);
                check(!waiter.isAlive(), mode + " terminal state");
                if (mode.equals("slow-log")) {
                    check(job.resourceWaitStartTime >= 300 && job.resourceWaitMs < 150,
                            "300ms WAIT log excluded: " + job.resourceWaitMs + "ms");
                    check(stats.completedCount() == 1, "Completed once");
                } else {
                    check(job.resourceWaitMs >= 80, mode + " wait retained: " + job.resourceWaitMs + "ms");
                    check(stats.cancelledCount() == 1, "Cancelled once");
                }
            } finally {
                worker.interrupt(); waiter.interrupt(); worker.join(2000); waiter.join(2000);
                if (held) resources.release(ResourceType.PRINTER);
                output.close();
            }
            check(resources.status().equals("printer=0/1 database=0/1"), "Permits restored");
        }
    }
}
