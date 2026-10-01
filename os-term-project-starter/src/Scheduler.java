import java.util.concurrent.BlockingQueue;

/**
 * Scheduler รับ Job จาก arrivalQueue แล้วใส่ ReadyQueue
 * เพื่อบังคับ pipeline ให้ JobGenerator ไม่ข้าม Scheduler
 */
public class Scheduler extends Thread {

    private final BlockingQueue<Job> inputQueue;
    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    public Scheduler(BlockingQueue<Job> inputQueue, ReadyQueue readyQueue,
                     ProjectLogger logger) {
        super("scheduler");
        this.inputQueue = inputQueue;
        this.readyQueue = readyQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = inputQueue.take();
                if (job == JobGenerator.END_OF_INPUT) {
                    readyQueue.close();
                    logger.systemEvent("Scheduler input closed");
                    return;
                }

                job.setState(JobState.READY);
                readyQueue.add(job);
                logger.systemEvent(job.id + " READY");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            readyQueue.close();
            logger.systemEvent("Scheduler interrupted");
        }
    }
}
