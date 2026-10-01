import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ arrival queue ตาม arrivalMs
 */
public class JobGenerator extends Thread {

    /** สัญญาณพิเศษบอก Scheduler ว่าไม่มี Job ใหม่อีกแล้ว */
    public static final Job END_OF_INPUT =
            new Job("__END_OF_INPUT__", Long.MAX_VALUE, Integer.MAX_VALUE,
                    0, ResourceType.NONE, 0, Integer.MAX_VALUE);

    private final List<Job> jobs;
    private final BlockingQueue<Job> outputQueue;
    private final ProjectLogger logger;
    private final Statistics statistics;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> outputQueue,
                        ProjectLogger logger, Statistics statistics) {
        super("generator");
        this.jobs = new ArrayList<>(jobs);
        this.jobs.sort(Comparator.comparingLong((Job job) -> job.arrivalMs)
                .thenComparingInt(job -> job.sequence)
                .thenComparing(job -> job.id));
        this.outputQueue = outputQueue;
        this.logger = logger;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        try {
            for (Job job : jobs) {
                waitUntilArrival(job.arrivalMs);
                long actualArrivalMs = logger.now();
                job.setState(JobState.ARRIVED);
                statistics.recordArrival(job, actualArrivalMs);
                logger.jobArrived(job);
                outputQueue.put(job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.systemEvent("JobGenerator interrupted");
        } finally {
            // แจ้ง Scheduler ว่าต้นทางจะไม่ส่งงานต่อแล้ว
            // ด้วย unbounded LinkedBlockingQueue การส่ง sentinel ไม่ต้องรอ
            // และทำให้ normal shutdown ไม่ค้างเพราะ Generator ถูก interrupt
            // ระหว่างช่วงรอ arrival ของงาน
            outputQueue.offer(END_OF_INPUT);
            logger.systemEvent("JobGenerator sent END_OF_INPUT");
        }
    }

    private void waitUntilArrival(long arrivalMs) throws InterruptedException {
        while (true) {
            long remaining = arrivalMs - logger.now();
            if (remaining <= 0) {
                return;
            }
            Thread.sleep(Math.min(remaining, 50L));
        }
    }
}
