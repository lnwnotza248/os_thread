/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ลำดับการทำงานของ Job หนึ่งชิ้น บังคับตามหัวข้อ 6 ของโจทย์:
 * 1. รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
 * 2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
 * 3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
 * 4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
 * 5. release แล้วบันทึกเวลาจบ
 *
 * ห้ามสลับขั้นที่ 2 กับ 3 เพราะจะทำให้ผลของทุกกลุ่มเทียบกันไม่ได้
 *
 * จุดที่มักพลาด:
 * - ถ้า exception หรือ interrupt เกิดขึ้นหลัง acquire แต่ก่อน release
 * permit จะค้างถาวรและระบบจะแขวน ต้องออกแบบให้คืนได้เสมอ
 * - Worker ต้องหยุดเองได้เมื่อไม่มีงานเหลือแล้ว ไม่ใช่วนรอตลอดไป
 */
public class Worker extends Thread {
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
            Statistics statistics, ProjectLogger logger) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Job job = readyQueue.take();
                statistics.jobStarted();
                try {
                    processJob(job);
                } finally {
                    statistics.jobFinished();
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** ทำงานหนึ่งชิ้นให้จบตามลำดับ 5 ขั้นด้านบน */
    private void processJob(Job job) throws InterruptedException {
        job.startTime = logger.now();
        logger.jobStarted(job);

        Thread.sleep(job.workMs);
        logger.workFinished(job);

        boolean acquired = false;

        try {
            if (job.resource != ResourceType.NONE) {
                job.resourceWaitStartTime = logger.now();
                logger.resourceWaitStarted(job);

                resources.acquire(job.resource);
                acquired = true;

                job.resourceWaitMs = logger.now() - job.resourceWaitStartTime;

                logger.resourceAcquired(job, job.resourceWaitMs);

                Thread.sleep(job.resourceMs);
            }
        } finally { // ถ้า acquire() สำเร็จแล้วเกิด interrupt ระหว่าง Thread.sleep(job.resourceMs) ต้องเรียก release() คืน permit เสมอ
            if (acquired) {
                resources.release(job.resource);
                logger.resourceReleased(job);
            }
        }

        job.completionTime = logger.now();
        logger.jobCompleted(job);
        statistics.recordCompletion(job);
    }
}
