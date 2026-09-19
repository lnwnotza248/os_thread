import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Optional controller that scales worker threads between a minimum and maximum. */
public final class DynamicWorkerPool implements AutoCloseable {
    private final int minimumWorkers;
    private final int maximumWorkers;
    private final IntSupplier queueLength;
    private final Supplier<Runnable> workerFactory;
    private final List<Thread> workers = new ArrayList<>();
    private final Map<Thread, Runnable> workerTasks = new HashMap<>();
    private volatile boolean running;
    private Thread controller;

    public DynamicWorkerPool(int minimumWorkers, int maximumWorkers,
                             IntSupplier queueLength, Supplier<Runnable> workerFactory) {
        if (minimumWorkers < 1 || maximumWorkers < minimumWorkers) {
            throw new IllegalArgumentException("Invalid worker bounds");
        }
        this.minimumWorkers = minimumWorkers;
        this.maximumWorkers = maximumWorkers;
        this.queueLength = queueLength;
        this.workerFactory = workerFactory;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < minimumWorkers; i++) {
            startWorker();
        }
        controller = new Thread(this::controlLoop, "DynamicWorkerController");
        controller.start();
    }

    private void controlLoop() {
        try {
            while (running) {
                synchronized (this) {
                    removeFinishedWorkers();
                    int queued = queueLength.getAsInt();
                    if (queued > 0 && workers.size() < maximumWorkers) {
                        startWorker();
                    } else if (queued == 0 && workers.size() > minimumWorkers) {
                        requestRetirement();
                    }
                }
                Thread.sleep(25);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void startWorker() {
        Runnable task = workerFactory.get();
        Thread worker = new Thread(task, "DynamicWorker-" + workers.size());
        workers.add(worker);
        workerTasks.put(worker, task);
        worker.start();
    }

    private void removeFinishedWorkers() {
        workers.removeIf(thread -> {
            if (thread.isAlive()) {
                return false;
            }
            workerTasks.remove(thread);
            return true;
        });
    }

    private void requestRetirement() {
        for (int index = workers.size() - 1; index >= 0; index--) {
            Thread worker = workers.get(index);
            Runnable task = workerTasks.get(worker);
            if (task instanceof WorkerControl control && control.isIdle()) {
                control.requestStop();
                worker.interrupt();
                return;
            }
        }
    }

    public synchronized int activeWorkers() {
        removeFinishedWorkers();
        return workers.size();
    }

    @Override
    public synchronized void close() throws InterruptedException {
        running = false;
        if (controller != null) {
            controller.interrupt();
            controller.join();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        workers.clear();
    }
}
