import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Scales the worker count between one and the configured maximum. */
public final class DynamicWorkerPool extends Thread {
    private final int maxWorkers;
    private final boolean dynamicSizing;
    private final boolean virtualThreads;
    private final long resourceTimeoutMs;
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private final List<ManagedWorker> workers = new ArrayList<>();
    private volatile boolean stopping;
    private int nextWorkerId;
    private int lastLoggedActive = -1;
    private int lastLoggedTarget = -1;

        public DynamicWorkerPool(int maxWorkers, boolean dynamicSizing, boolean virtualThreads,
            long resourceTimeoutMs,
            ReadyQueue readyQueue, ResourceManager resources, Statistics statistics,
            ProjectLogger logger) {
        super("worker-pool-manager");
        this.maxWorkers = maxWorkers;
        this.dynamicSizing = dynamicSizing;
        this.virtualThreads = virtualThreads;
        this.resourceTimeoutMs = resourceTimeoutMs;
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!stopping) {
                int desired = dynamicSizing
                        ? Math.max(1, Math.min(maxWorkers, readyQueue.size()))
                        : maxWorkers;
                scaleTo(desired);
                Thread.sleep(100);
            }
        } catch (InterruptedException exception) {
            if (!stopping) {
                Thread.currentThread().interrupt();
            }
        } finally {
            retireAndJoinAll();
        }
    }

    public void shutdownAndJoin() throws InterruptedException {
        stopping = true;
        interrupt();
        join();
    }

    private synchronized void scaleTo(int desired) {
        removeTerminatedWorkers();

        int active = 0;
        for (ManagedWorker managed : workers) {
            if (managed.thread.isAlive() && !managed.worker.retirementRequested()) {
                active++;
            }
        }

        if (active < desired) {
            for (int i = workers.size() - 1; i >= 0 && active < desired; i--) {
                ManagedWorker managed = workers.get(i);
                if (managed.thread.isAlive() && managed.worker.retirementRequested()) {
                    managed.worker.cancelRetirementRequest();
                    active++;
                }
            }
            while (active < desired) {
                startWorker();
                active++;
            }
        } else if (active > desired) {
            int toRetire = active - desired;
            for (ManagedWorker managed : workers) {
                if (toRetire == 0) {
                    break;
                }
                if (managed.thread.isAlive() && !managed.worker.retirementRequested()
                        && !managed.worker.isProcessing()) {
                    managed.worker.requestRetirement();
                    toRetire--;
                }
            }
            for (int i = workers.size() - 1; i >= 0 && toRetire > 0; i--) {
                ManagedWorker managed = workers.get(i);
                if (managed.thread.isAlive() && !managed.worker.retirementRequested()) {
                    managed.worker.requestRetirement();
                    toRetire--;
                }
            }
        }

        int available = 0;
        for (ManagedWorker managed : workers) {
            if (managed.thread.isAlive()) {
                if (!managed.worker.retirementRequested()) {
                    available++;
                }
            }
        }
        if (available != lastLoggedActive || desired != lastLoggedTarget) {
            logger.workerPoolChanged(available, desired);
            lastLoggedActive = available;
            lastLoggedTarget = desired;
        }
    }

    private void startWorker() {
        String name = "worker-" + (++nextWorkerId);
        Worker worker = new Worker(name, readyQueue, resources, statistics, logger,
                resourceTimeoutMs, true);
        Thread thread;
        if (virtualThreads) {
            thread = Thread.ofVirtual().name(name).start(worker);
        } else {
            worker.start();
            thread = worker;
        }
        workers.add(new ManagedWorker(worker, thread));
    }

    private synchronized void removeTerminatedWorkers() {
        Iterator<ManagedWorker> iterator = workers.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().thread.isAlive()) {
                iterator.remove();
            }
        }
    }

    private void retireAndJoinAll() {
        List<ManagedWorker> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(workers);
            for (ManagedWorker managed : snapshot) {
                managed.worker.requestRetirement();
            }
        }
        for (ManagedWorker managed : snapshot) {
            try {
                managed.thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static final class ManagedWorker {
        private final Worker worker;
        private final Thread thread;

        private ManagedWorker(Worker worker, Thread thread) {
            this.worker = worker;
            this.thread = thread;
        }
    }
}