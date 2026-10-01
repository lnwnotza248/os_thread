/** Job ที่มี level และ remaining CPU time สำหรับ MLFQ */
public final class MlfqJob {
    public final Job job;
    public long remainingWorkMs;
    public int level;

    public MlfqJob(Job job) {
        this.job = job;
        this.remainingWorkMs = job.workMs;
        this.level = 0;
    }
}
