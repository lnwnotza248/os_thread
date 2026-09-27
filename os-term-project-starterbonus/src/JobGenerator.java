import java.util.ArrayList; // ใช้เก็บรายการงานชั่วคราว
import java.util.Comparator; // ใช้เปรียบเทียบลำดับงานตาม arrivalMs
import java.util.List; // ใช้เก็บรายการงานทั้งหมดที่ต้องปล่อยเข้าสู่ระบบ
import java.util.concurrent.BlockingQueue; //ใช้เป็นช่องทางส่งงานไปยัง Scheduler

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs ของแต่ละ Job
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * หน้าที่ (หัวข้อ 3 ของโจทย์):
 * - รอจนถึงเวลา arrivalMs ของแต่ละงาน แล้วส่งงานต่อไปยัง Scheduler
 * - บันทึกเวลาที่งานเข้าสู่ระบบ "จริง" ลงใน Job
 * (อาจไม่ตรงกับ arrivalMs เป๊ะ เพราะ Thread ถูกปลุกช้าได้)
 * - เรียก logger.jobArrived(job) ทุกครั้งที่ปล่อยงาน
 *
 * ข้อควรคิด:
 * - รายการงานที่ได้จาก WorkloadLoader เรียงตามลำดับในไฟล์ ไม่ได้เรียงตามเวลา
 * - เมื่อปล่อยงานครบทุกชิ้นแล้ว ต้องมีวิธีบอกระบบว่า "จะไม่มีงานเข้ามาอีก"
 * ดู TODO เรื่องการปิดระบบใน Main
 */
public class JobGenerator extends Thread {

    // TODO: เก็บรายการงาน, ช่องทางส่งงานไปยัง Scheduler และ logger
    private final List<Job> jobs;
    private final BlockingQueue<Job> schedulerQueue;
    private final ProjectLogger logger;
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางส่งงาน"
    // เพราะเป็นสิ่งที่กลุ่มต้องออกแบบเอง (หัวข้อ 2 ห้ามให้ JobGenerator
    // ใส่งานลง ReadyQueue โดยตรง ต้องผ่าน Scheduler เสมอ)
    // ให้เพิ่ม parameter เข้าไปตามที่ออกแบบ เช่น BlockingQueue<Job>
    // หรือคลาสของกลุ่มเอง — เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> schedulerQueue, ProjectLogger logger) {
        // TODO: เก็บค่า parameter ลง field
        super("generator");
        this.jobs = List.copyOf(jobs);
        this.schedulerQueue = schedulerQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        List<Job> releaseOrder = new ArrayList<>(jobs);

        releaseOrder.sort(
                Comparator.comparingLong((Job job) -> job.arrivalMs)
                        .thenComparingInt(job -> job.sequence));

        try {
            for (Job job : releaseOrder) {
                // เหลือเวลาอีกกี่ ms ก่อน Job ต้องเข้าระบบ
                long remaining = job.arrivalMs - logger.now();

                // ยังไม่ถึงเวลาก็พัก Generator ไว้ก่อน
                if (remaining > 0) {
                    Thread.sleep(remaining);
                }

                // ถึงเวลาแล้ว: บันทึกเวลา, log และส่งให้ Scheduler
                job.actualArrivalTime = logger.now();
                logger.jobArrived(job);
                schedulerQueue.put(job);
            }
        } catch (InterruptedException exception) {
            // Generator ถูกสั่งให้หยุดระหว่าง sleep หรือ put
            Thread.currentThread().interrupt();
        }
    }
}
