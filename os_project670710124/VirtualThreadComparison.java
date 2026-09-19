import java.util.ArrayList;
import java.util.List;

/** Compares platform and virtual threads for sleep-heavy workloads. */
public final class VirtualThreadComparison {
    private VirtualThreadComparison() {
    }

    public static void main(String[] args) throws Exception {
        int taskCount = args.length == 0 ? 100 : Integer.parseInt(args[0]);
        long waitMs = args.length < 2 ? 50 : Long.parseLong(args[1]);
        long platformMs = run(taskCount, waitMs, false);
        long virtualMs = run(taskCount, waitMs, true);
        System.out.printf("tasks=%d wait=%dms platform=%dms virtual=%dms%n",
                taskCount, waitMs, platformMs, virtualMs);
    }

    private static long run(int taskCount, long waitMs, boolean virtual)
            throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < taskCount; i++) {
            Thread thread = virtual ? Thread.startVirtualThread(() -> sleep(waitMs))
                    : new Thread(() -> sleep(waitMs));
            threads.add(thread);
            if (!virtual) {
                thread.start();
            }
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return System.currentTimeMillis() - start;
    }

    private static void sleep(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
