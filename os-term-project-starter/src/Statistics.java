import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
/**
 * รวบรวมและคำนวณค่าที่ใช้วัดผลของการรันหนึ่งครั้ง
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้ (หัวข้อ 8):
 *   - Waiting Time, Turnaround Time, Throughput, Resource Wait Time
 *   - ต้องถูกอัปเดตจากหลาย Worker พร้อมกันได้อย่างปลอดภัย
 *   - ผลต้องสอดคล้องกับสมการตรวจสอบ:
 *       Turnaround = Waiting + workMs + Resource Wait + resourceMs
 *     ใช้สมการนี้ตรวจงานทีละชิ้นได้ว่าค่าไหนคำนวณผิด
 *
 * ข้อควรระวัง: ค่าเฉลี่ยของ Resource Wait ให้คิดเฉพาะงานที่ใช้ resource
 * ส่วนงานที่ resource = NONE ให้ถือว่า Resource Wait เป็น 0
 */
public class Statistics {
    private int ready;
    private int running;
    private int completed;
    private final AtomicLong totalWaiting = new AtomicLong();
    private final AtomicLong totalTurnaround = new AtomicLong();
    private final AtomicLong totalResourceWait = new AtomicLong();
    private final AtomicInteger resourceJobCount = new AtomicInteger();
    // นับถอยหลังจากจำนวนงานทั้งหมด ให้ Main รอแบบไม่ต้อง poll/เดาเวลา
    private final CountDownLatch allCompleted;

    public Statistics(int totalJobs) {
        this.allCompleted = new CountDownLatch(totalJobs);
    }

    /** บันทึกว่างานหนึ่งชิ้นถูกส่งเข้า Ready Queue */
    public synchronized void jobQueued() {
        ready++;
    }

    /** ย้ายงานหนึ่งชิ้นจาก Ready Queue ไปเป็นงานที่กำลังทำ */
    public synchronized void jobStarted() {
        ready--;
        running++;
    }

    /** บันทึกว่างานชิ้นหนึ่งเสร็จแล้ว เรียกโดย Worker หลายตัวพร้อมกันได้ */
    public synchronized void recordCompletion(Job job) {
        totalWaiting.addAndGet(job.waitingTime());
        totalTurnaround.addAndGet(job.turnaroundTime());

        if (job.resource != ResourceType.NONE) {
            totalResourceWait.addAndGet(job.resourceWaitMs);
            resourceJobCount.incrementAndGet();
        }

        running--;
        completed++;
        allCompleted.countDown();
    }

    /** พัก Thread ที่เรียกไว้จนกว่างานทุกชิ้นจะ countDown ครบ ไม่กินCPUระหว่างรอ */
    public void awaitAllCompleted() throws InterruptedException {
        allCompleted.await();
    }

    /** จำนวนงานที่เสร็จแล้ว ใช้โดย Monitor และใช้ตรวจว่างานครบหรือยัง */
    public synchronized int completedCount() {
        return completed;
    }

    /** ลด running เมื่องานถูกขัดจังหวะหรือทำไม่สำเร็จก่อนบันทึก completion */
    public synchronized void jobFinished() {
        running--;
    }

    public synchronized int runningCount() {
        return running;
    }

    /** อ่าน ready, running และ completed ภายใต้ lock เดียวกัน */
    public synchronized Snapshot snapshot() {
        return new Snapshot(ready, running, completed);
    }

    public static final class Snapshot {
        public final int ready;
        public final int running;
        public final int completed;

        private Snapshot(int ready, int running, int completed) {
            this.ready = ready;
            this.running = running;
            this.completed = completed;
        }
    }

    /**
     * พิมพ์ตารางสรุปผลตอนจบโปรแกรม
     * อย่างน้อยต้องมี avg Waiting Time, avg Turnaround Time,
     * Throughput และ avg Resource Wait Time
     *
     * ตามหัวข้อ 14 ให้รายงานเวลาเป็นจำนวนเต็มหน่วย ms
     * และ Throughput อย่างน้อย 2 ตำแหน่งทศนิยม
     */
    public void printSummary(List<Job> allJobs, long makespanMs) {
        int count;
        synchronized (this) {
            count = completed;
        }

        long avgWaiting = count == 0 ? 0 : totalWaiting.get() / count;
        long avgTurnaround = count == 0 ? 0 : totalTurnaround.get() / count;
        long avgResourceWait = resourceJobCount.get() == 0
                ? 0
                : totalResourceWait.get() / resourceJobCount.get();

        double throughput = makespanMs == 0
                ? 0.0
                : count * 1000.0 / makespanMs;

        System.out.println("===== SUMMARY =====");
        System.out.println("Completed: " + count + "/" + allJobs.size());
        System.out.println("Average Waiting Time: " + avgWaiting + " ms");
        System.out.println("Average Turnaround Time: " + avgTurnaround + " ms");
        System.out.printf("Throughput: %.2f jobs/s%n", throughput);
        System.out.println("Average Resource Wait Time: "
                + avgResourceWait + " ms");
    }
}
