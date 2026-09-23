import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * จุดเริ่มต้นของโปรแกรม
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ส่วนที่เขียนไว้ให้แล้วคือการรับค่า การโหลด workload และการแสดง error
 * ซึ่งไม่ใช่สิ่งที่โครงงานนี้วัด ส่วนที่เหลือเป็น TODO ทั้งหมด
 *
 * วิธีรัน:
 * java Main jobs_standard.csv priority 3 1 2
 */
public class Main {

    public static void main(String[] args) {
        // ---------- 1. รับค่าจาก command line ----------
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ผิดพลาด: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            System.exit(1);
            return;
        }

        // ---------- 2. เริ่มจับเวลาและโหลด workload ----------
        ProjectLogger logger = new ProjectLogger();
        List<Job> jobs;
        try {
            jobs = WorkloadLoader.load(config.workloadPath);
        } catch (WorkloadFormatException e) {
            System.err.println("ไฟล์ workload ผิดรูปแบบ — " + e.getMessage());
            System.exit(1);
            return;
        } catch (java.io.IOException e) {
            System.err.println("เปิดไฟล์ \"" + config.workloadPath + "\" ไม่ได้");
            System.err.println("ตรวจว่าไฟล์มีอยู่จริงและ path ถูกต้อง (สั่ง java จากโฟลเดอร์ใด)");
            System.exit(1);
            return;
        }
        logger.systemStart(config);
        logger.systemEvent("โหลดงานได้ " + jobs.size() + " ชิ้น");

        // ---------- 3. สร้างส่วนประกอบของระบบ ----------
        // TODO: สร้าง ResourceManager จากจำนวน permit ใน config
        ResourceManager resources = new ResourceManager(
                config.printerPermits,
                config.databasePermits);
        // TODO: สร้าง ReadyQueue ตามนโยบายใน config
        ReadyQueue readyQueue = new ReadyQueue(config.policy);
        // TODO: สร้าง Statistics
        Statistics statistics = new Statistics(jobs.size());

        BlockingQueue<Job> schedulerQueue = new LinkedBlockingQueue<>();
        // ---------- 4. สร้างและเริ่ม Thread ----------
        // TODO: สร้าง Worker จำนวน config.workers ตัว แล้ว start
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < config.workers; i++) {
            Worker worker = new Worker(
                    "worker-" + (i + 1),
                    readyQueue,
                    resources,
                    statistics,
                    logger);
            workers.add(worker);
            worker.start();
        }
        // TODO: สร้างและ start Scheduler
        Scheduler scheduler = new Scheduler(schedulerQueue, readyQueue, jobs.size(), logger);
        scheduler.start();
        // TODO: สร้างและ start Monitor
        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger);
        monitor.start();
        // TODO: สร้างและ start JobGenerator
        JobGenerator jobGenerator = new JobGenerator(jobs, schedulerQueue, logger);
        jobGenerator.start();

        // ลำดับการ start มีผลหรือไม่ ให้คิดและอธิบายได้ใน Demo
        /*
         * ถ้าตั้ง worker ก่อน generator
         * Workers จะรอ readyQueue.take()
         * Generator จะปล่อยงานตามเวลา arrivalMs
         * เมื่อมีงานเข้ามา Workers จะตื่นและทำงานต่อ
         * 
         * ถ้าตั้ง generator ก่อน worker
         * Generator จะส่งงานไปยัง schedulerQueue
         * Scheduler จะนำไปใส่ readyQueue
         * Workers จะเอางานไปทำทีหลัง
         * ผลสุดท้ายก็ยังถูกต้อง แต่การเริ่มต้นอาจมี delay เล็กน้อย
         */
        // ---------- 5. รอจนงานเสร็จครบ ----------
        // TODO: รอจนกว่างานทั้ง jobs.size() ชิ้นจะเสร็จ
        //
        // *** นี่คือจุดที่ยากที่สุดของโครงงานนี้ ***
        // Worker ที่กำลังรออยู่ในคิวไม่มีทางรู้ได้เองว่าจะไม่มีงานเข้ามาอีกแล้ว
        // กลุ่มต้องออกแบบวิธีบอก โดยห้ามใช้การเดาเวลา เช่น sleep(10000)
        //
        // - ตัวนับงานค้างที่ป้องกันด้วย lock
        try {
            statistics.awaitAllCompleted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // อาการผิดที่ต้องไม่เกิด:
        // 1. main จบแล้วแต่ JVM ไม่ปิด เพราะยังมี Thread ค้างอยู่
        // 2. Worker หยุดก่อนที่งานชิ้นสุดท้ายจะทำเสร็จ
        // 3. permit ค้างเพราะถูก interrupt ระหว่างถือ resource

        // ---------- 6. สั่งหยุดทุก Thread ----------
        // TODO: หยุด Worker ทุกตัว, Scheduler, Monitor และ JobGenerator
        // TODO: join ทุก Thread เพื่อยืนยันว่าหยุดจริงก่อนไปขั้นถัดไป
        for (Worker worker : workers) {
            worker.interrupt();
        }
        scheduler.interrupt();
        monitor.interrupt();
        jobGenerator.interrupt();

        try {
            for (Worker worker : workers) {
                worker.join();
            }
            scheduler.join();
            monitor.join();
            jobGenerator.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ---------- 7. สรุปผล ----------
        // TODO: หา makespan = เวลาที่งานชิ้นสุดท้ายเสร็จ (ใช้ logger.now())
        // TODO: เรียก statistics.printSummary(jobs, makespanMs)
        // TODO: logger.systemStop(completed, jobs.size())
        long makespanMs = logger.now();
        statistics.printSummary(jobs, makespanMs);
        logger.systemStop(statistics.completedCount(), jobs.size());
    }
}
