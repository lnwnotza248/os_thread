/** คอยพิมพ์ภาพรวมของระบบเป็นช่วง ๆ */
public class Monitor extends Thread {

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private volatile boolean stopRequested = false;

    public Monitor(ReadyQueue readyQueue, ResourceManager resources,
                   Statistics statistics, ProjectLogger logger) {
        super("monitor");
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
        setDaemon(true);
    }

    /** ส่งสัญญาณให้ Monitor หยุด */
    public void requestStop() {
        stopRequested = true;
        interrupt();
    }

    @Override
    public void run() {
        while (!stopRequested) {
            reportSnapshot();
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                if (stopRequested) {
                    break;
                }
                Thread.currentThread().interrupt();
                break;
            }
        }

        // พิมพ์สถานะรอบสุดท้ายก่อนจบ
        reportSnapshot();
    }

    private void reportSnapshot() {
        logger.monitor(
                readyQueue.size(),
                statistics.runningCount(),
                statistics.completedCount(),
                resources.status());
    }
}
