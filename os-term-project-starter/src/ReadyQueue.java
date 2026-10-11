import java.util.Comparator;//กฎการเปรียบเทียบ
import java.util.concurrent.BlockingQueue;//เป็น interface ที่เราประกาศว่า queue ของเราจะต้องเป็น Queue ที่รองรับการทำงานแบบหลาย Thread และสามารถ รอ (block) ได้ โดยไม่ต้องเขียน loop เช็ก Queue ตลอดเวลา ซึ่งจะเปลือง CPU
import java.util.concurrent.LinkedBlockingQueue;//คิวแบบ FIFO ที่รองรับการทำงานแบบหลาย Thread และสามารถรอ (block) ได้
import java.util.concurrent.PriorityBlockingQueue;//คิวแบบ Priority ที่รองรับการทำงานแบบหลาย Thread และสามารถรอ (block) ได้

/**
 * คิวงานที่พร้อมถูกหยิบไปทำ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * สิ่งที่คลาสนี้ต้องทำได้:
 *   - เก็บงานที่รอ Worker อยู่
 *   - หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority)
 *   - ถูกเรียกจากหลาย Thread พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 4: priority = 1 สูงสุด เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break)
 *     ที่ตัดสินจากข้อมูลของ Job ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน
 *   - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU (busy waiting) — Worker ที่ไม่มีงานทำ
 *     ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */

public class ReadyQueue {

    private final BlockingQueue<Job> queue;// เก็บนโยบาย (Config.Policy) และโครงสร้างข้อมูลที่ใช้เก็บงาน
    private final Statistics statistics;

    public ReadyQueue(Config.Policy policy, Statistics statistics) {
        this.statistics = statistics;
        if (policy == Config.Policy.FCFS) {
            queue = new LinkedBlockingQueue<>();
        } else {
            Comparator<Job> priorityOrder = Comparator
                    .comparingInt((Job job) -> job.priority)
                    .thenComparingInt(job -> job.sequence);

            queue = new PriorityBlockingQueue<>(11, priorityOrder);
        }
        
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        synchronized (statistics) {
            queue.add(job);
            statistics.jobQueued();
        }
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * เรียก BlockingQueue.take() ถ้าคิวว่าง Worker จะถูกพักไว้ (park) ภายใน JDK ด้วย lock/condition ไม่ได้วนถามซ้ำ จึงไม่กิน CPU
     */
    public Job take() throws InterruptedException {
        Job job = queue.take();
        synchronized (statistics) {
            statistics.jobStarted();
        }
        return job;
    }

    /** จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor — ต้องอ่านได้อย่างปลอดภัย */
    public int size() {
        return queue.size();
    }
}
