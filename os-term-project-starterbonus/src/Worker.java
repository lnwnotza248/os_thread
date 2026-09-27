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
import java.util.ArrayList;
import java.util.List;

public class Worker extends Thread {
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private final long resourceTimeoutMs;
    private final boolean dynamicPolling;
    private volatile boolean retirementRequested;
    private volatile boolean processing;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
            Statistics statistics, ProjectLogger logger) {
        this(name, readyQueue, resources, statistics, logger, 0, false);
        }

        public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
            Statistics statistics, ProjectLogger logger, long resourceTimeoutMs,
            boolean dynamicPolling) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
        this.resourceTimeoutMs = resourceTimeoutMs;
        this.dynamicPolling = dynamicPolling;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (retirementRequested) {
                    break;
                }
                Job job = dynamicPolling ? readyQueue.take(100) : readyQueue.take();
                if (job == null) {
                    continue;
                }
                processing = true;
                statistics.jobStarted();
                try {
                    processJob(job);
                } catch (InterruptedException exception) {
                    job.completionTime = logger.now();
                    logger.jobCancelled(job, "interrupted");
                    statistics.recordCancellation(job);
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    statistics.jobFinished();
                    processing = false;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** ทำงานหนึ่งชิ้นให้จบตามลำดับ 5 ขั้นด้านบน */
    private void processJob(Job job) throws InterruptedException {
        long dispatchTime = logger.now();
        if (job.startTime < 0) {
            job.startTime = dispatchTime;
            job.totalQueueWaitingTimeMs += dispatchTime - job.actualArrivalTime;
            logger.jobStarted(job);
        } else {
            if (job.readyWaitStartTime >= 0) {
                job.totalQueueWaitingTimeMs += dispatchTime - job.readyWaitStartTime;
                job.readyWaitStartTime = -1;
            }
            logger.jobResumed(job, job.mlfqLevel, job.remainingWorkMs);
        }

        if (readyQueue.isMlfq()) {
            long quantumMs = readyQueue.timeQuantumMs(job);
            long workSliceMs = Math.min(job.remainingWorkMs, quantumMs);
            Thread.sleep(workSliceMs);
            long remainingWorkMs = job.remainingWorkMs - workSliceMs;
            if (remainingWorkMs > 0) {
                job.readyWaitStartTime = logger.now();
                readyQueue.requeue(job, remainingWorkMs, logger);
                return;
            }
            job.remainingWorkMs = 0;
        } else {
            Thread.sleep(job.workMs);
        }
        logger.workFinished(job);

        List<ResourceType> acquired = new ArrayList<>();
        try {
            if (!job.resources.isEmpty()) {
                for (ResourceType resource : job.resources) {
                    logger.resourceWaitStarted(job, resource);
                    // Exclude WAIT logging from the measured semaphore wait.
                    long waitStart = logger.now();
                    if (job.resourceWaitStartTime < 0) {
                        job.resourceWaitStartTime = waitStart;
                    }
                    boolean obtained;
                    long waitedMs;
                    try {
                        if (resourceTimeoutMs == 0) {
                            resources.acquire(resource);
                            obtained = true;
                        } else {
                            obtained = resources.tryAcquire(resource, resourceTimeoutMs);
                        }
                    } finally {
                        // Preserve elapsed wait even on timeout or interruption.
                        waitedMs = logger.now() - waitStart;
                        job.resourceWaitMs += waitedMs;
                    }
                    if (obtained) {
                        acquired.add(resource);
                    } else {
                        String releasedResources = acquired.toString();
                        releaseAcquired(job, acquired);
                        job.completionTime = logger.now();
                        logger.jobCancelled(job, "resource-timeout=" + resource
                                + " released=" + releasedResources);
                        statistics.recordCancellation(job);
                        return;
                    }
                    logger.resourceAcquired(job, resource, waitedMs);
                }

                Thread.sleep(job.resourceMs);
            }
        } finally { // ถ้า acquire() สำเร็จแล้วเกิด interrupt ระหว่าง Thread.sleep(job.resourceMs) ต้องเรียก release() คืน permit เสมอ
            if (!acquired.isEmpty()) {
                releaseAcquired(job, acquired);
            }
        }

        job.completionTime = logger.now();
        logger.jobCompleted(job);
        statistics.recordCompletion(job);
    }

    private void releaseAcquired(Job job, List<ResourceType> acquired) {
        for (int i = acquired.size() - 1; i >= 0; i--) {
            ResourceType resource = acquired.get(i);
            resources.release(resource);
            logger.resourceReleased(job, resource);
        }
        acquired.clear();
    }

    public void requestRetirement() {
        retirementRequested = true;
    }

    public void cancelRetirementRequest() {
        retirementRequested = false;
    }

    public boolean retirementRequested() {
        return retirementRequested;
    }

    public boolean isProcessing() {
        return processing;
    }
}
