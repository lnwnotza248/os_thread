import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** จุดเริ่มต้น: สร้างส่วนต่าง ๆ แล้วต่อ pipeline ของระบบเข้าด้วยกัน */
public class Main {

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ผิดพลาด: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            return;
        }

        ProjectLogger logger = new ProjectLogger();
        List<Job> jobs;
        try {
            jobs = WorkloadLoader.load(config.workloadPath);
        } catch (Exception e) {
            System.err.println("โหลด workload ไม่สำเร็จ: " + e.getMessage());
            return;
        }

        logger.systemStart(config);
        logger.systemEvent("เริ่มระบบ: workers + resources + metrics + monitor");
        logger.systemEvent("โหลดงานได้ " + jobs.size() + " ชิ้น");

        BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();
        ReadyQueue readyQueue = new ReadyQueue(config.policy);

        ResourceManager resources = new ResourceManager(config.printerPermits, config.databasePermits);
        Statistics statistics = new Statistics();

        JobGenerator generator = new JobGenerator(jobs, arrivalQueue, logger, statistics);
        Scheduler scheduler = new Scheduler(arrivalQueue, readyQueue, logger);
        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger);
        Worker[] workers = new Worker[config.workers];
        for (int i = 0; i < config.workers; i++) {
            workers[i] = new Worker("worker-" + (i + 1), readyQueue, resources, statistics, logger);
        }

        monitor.start();
        scheduler.start();
        for (Worker worker : workers) {
            worker.start();
        }
        generator.start();

        boolean interrupted = false;
        try {
            // Normal shutdown: wait for the generator to announce end-of-input,
            // then let the scheduler close the ReadyQueue. Workers drain the
            // remaining jobs before they return from take().
            generator.join();
            scheduler.join();
            for (Worker worker : workers) {
                worker.join();
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
            logger.systemEvent("main interrupted; requesting emergency shutdown");
            requestEmergencyStop(generator, scheduler, workers, readyQueue, monitor);
        } finally {
            // Monitor is not allowed to keep the process alive or print forever.
            monitor.requestStop();
            if (!interrupted) {
                try {
                    monitor.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.systemEvent("main interrupted while stopping monitor");
                    monitor.requestStop();
                }
            }
        }

        long makespanMs = logger.now();
        int completed = statistics.completedCount();
        if (completed == jobs.size()) {
            logger.systemEvent("GRACEFUL_SHUTDOWN all jobs completed");
        } else {
            logger.systemEvent("SHUTDOWN_INCOMPLETE completed=" + completed + "/" + jobs.size());
        }
        logger.systemStop(completed, jobs.size());
        statistics.printSummary(jobs, makespanMs);
    }

    private static void requestEmergencyStop(JobGenerator generator, Scheduler scheduler,
                                              Worker[] workers, ReadyQueue readyQueue,
                                              Monitor monitor) {
        readyQueue.close();
        generator.interrupt();
        scheduler.interrupt();
        for (Worker worker : workers) {
            worker.interrupt();
        }
        monitor.requestStop();
    }
}
