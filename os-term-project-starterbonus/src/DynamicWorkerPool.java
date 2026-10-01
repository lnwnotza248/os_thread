import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** คอยเพิ่ม/ลดจำนวน Worker ตามจำนวนงานใน ReadyQueue */
public final class DynamicWorkerPool extends Thread {
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final ProjectLogger logger;
    private final CountDownLatch completed;
    private final int minWorkers;
    private final int maxWorkers;
    private final int scaleThreshold;
    private final List<DynamicWorker> workers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean stop;
    private int nextId = 1;

    public DynamicWorkerPool(ReadyQueue readyQueue, ResourceManager resources,
                             ProjectLogger logger, CountDownLatch completed,
                             int minWorkers, int maxWorkers, int scaleThreshold) {
        super("dynamic-pool");
        if (minWorkers < 1 || maxWorkers < minWorkers || scaleThreshold < 1) {
            throw new IllegalArgumentException("worker/threshold ไม่ถูกต้อง");
        }
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.logger = logger;
        this.completed = completed;
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.scaleThreshold = scaleThreshold;
    }

    @Override
    public void run() {
        for (int i = 0; i < minWorkers; i++) addWorker();
        while (!stop) {
            cleanupDead();
            int queueSize = readyQueue.size();
            int count = currentCount();

            if (queueSize >= scaleThreshold && count < maxWorkers) addWorker();
            else if (queueSize == 0 && count > minWorkers) stopOneIdle();

            try { Thread.sleep(100L); }
            catch (InterruptedException e) { if (stop) break; }
        }
    }

    private void addWorker() {
        DynamicWorker worker = new DynamicWorker("dynamic-worker-" + nextId++, readyQueue, resources, logger, completed);
        workers.add(worker);
        worker.start();
        logger.systemEvent("DYNAMIC_ADD " + worker.getName() + " total=" + currentCount());
    }

    private void stopOneIdle() {
        synchronized (workers) {
            for (Iterator<DynamicWorker> it = workers.iterator(); it.hasNext();) {
                DynamicWorker worker = it.next();
                if (worker.isAlive() && worker.isIdle()) {
                    worker.requestStop();
                    logger.systemEvent("DYNAMIC_REMOVE " + worker.getName());
                    return;
                }
            }
        }
    }

    private void cleanupDead() {
        synchronized (workers) { workers.removeIf(w -> !w.isAlive()); }
    }

    private int currentCount() {
        synchronized (workers) { return workers.size(); }
    }

    public void shutdown() {
        stop = true;
        interrupt();
        synchronized (workers) {
            for (DynamicWorker worker : workers) worker.requestStop();
        }
    }

    public void awaitWorkers() throws InterruptedException {
        while (true) {
            synchronized (workers) {
                boolean allDead = true;
                for (DynamicWorker worker : workers) {
                    worker.join(10L);
                    if (worker.isAlive()) allDead = false;
                }
                if (allDead) return;
            }
        }
    }
}
