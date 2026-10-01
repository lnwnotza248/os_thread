import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** เก็บเวลาของแต่ละ Job และคำนวณ metric จากข้อมูลชุดเดียวกับ log */
public class Statistics {

    public static final class JobMetric {
        public final String jobId;
        public final long actualArrivalMs;
        public final long startMs;
        public final long resourceWaitMs;
        public final long completionMs;
        public final long waitingMs;
        public final long turnaroundMs;

        private JobMetric(String jobId, long actualArrivalMs, long startMs,
                          long resourceWaitMs, long completionMs) {
            this.jobId = jobId;
            this.actualArrivalMs = actualArrivalMs;
            this.startMs = startMs;
            this.resourceWaitMs = resourceWaitMs;
            this.completionMs = completionMs;
            this.waitingMs = startMs - actualArrivalMs;
            this.turnaroundMs = completionMs - actualArrivalMs;
        }
    }

    private static final class MutableMetric {
        long arrivalMs = -1;
        long startMs = -1;
        long resourceWaitStartMs = -1;
        long resourceWaitMs = 0;
        long completionMs = -1;
    }

    private final java.util.Map<String, MutableMetric> metrics = new java.util.HashMap<>();
    private int runningCount = 0;

    public synchronized void recordArrival(Job job, long actualArrivalMs) {
        MutableMetric metric = metricFor(job);
        metric.arrivalMs = actualArrivalMs;
    }

    public synchronized void recordStart(Job job, long startMs) {
        MutableMetric metric = metricFor(job);
        metric.startMs = startMs;
        runningCount++;
    }

    public synchronized void recordResourceWaitStart(Job job, long waitStartMs) {
        MutableMetric metric = metricFor(job);
        metric.resourceWaitStartMs = waitStartMs;
    }

    public synchronized void recordResourceAcquired(Job job, long acquiredMs) {
        MutableMetric metric = metricFor(job);
        if (metric.resourceWaitStartMs >= 0) {
            metric.resourceWaitMs += Math.max(0, acquiredMs - metric.resourceWaitStartMs);
            metric.resourceWaitStartMs = -1;
        }
    }

    public synchronized void recordCompletion(Job job, long completionMs) {
        MutableMetric metric = metricFor(job);
        metric.completionMs = completionMs;
        if (runningCount > 0) {
            runningCount--;
        }
    }

    /** จำนวน Job ที่กำลังอยู่ในขั้นตอนของ Worker */
    public synchronized int runningCount() {
        return runningCount;
    }

    private MutableMetric metricFor(Job job) {
        return metrics.computeIfAbsent(job.id, key -> new MutableMetric());
    }

    public synchronized int completedCount() {
        int count = 0;
        for (MutableMetric metric : metrics.values()) {
            if (metric.completionMs >= 0) {
                count++;
            }
        }
        return count;
    }

    public synchronized List<JobMetric> snapshotCompleted() {
        List<JobMetric> result = new ArrayList<>();
        for (java.util.Map.Entry<String, MutableMetric> entry : metrics.entrySet()) {
            MutableMetric m = entry.getValue();
            if (m.arrivalMs >= 0 && m.startMs >= 0 && m.completionMs >= 0) {
                result.add(new JobMetric(entry.getKey(), m.arrivalMs, m.startMs,
                        m.resourceWaitMs, m.completionMs));
            }
        }
        Collections.sort(result, Comparator.comparing(item -> item.jobId));
        return result;
    }

    public synchronized void printSummary(List<Job> allJobs, long makespanMs) {
        List<JobMetric> completed = snapshotCompleted();
        if (completed.isEmpty()) {
            System.out.println("=== Statistics ===");
            System.out.println("Completed jobs: 0/" + allJobs.size());
            System.out.println("No completed-job metrics available.");
            return;
        }

        long totalWaiting = 0;
        long totalTurnaround = 0;
        long totalResourceWait = 0;
        int resourceJobCount = 0;

        for (JobMetric metric : completed) {
            totalWaiting += metric.waitingMs;
            totalTurnaround += metric.turnaroundMs;

            Job job = findJob(allJobs, metric.jobId);
            if (job != null && job.resource != ResourceType.NONE) {
                totalResourceWait += metric.resourceWaitMs;
                resourceJobCount++;
            }
        }

        double avgWaiting = (double) totalWaiting / completed.size();
        double avgTurnaround = (double) totalTurnaround / completed.size();
        double avgResourceWait = resourceJobCount == 0
                ? 0.0
                : (double) totalResourceWait / resourceJobCount;
        double throughput = makespanMs <= 0
                ? 0.0
                : completed.size() / (makespanMs / 1000.0);

        System.out.println("=== Statistics ===");
        System.out.println("Completed jobs: " + completed.size() + "/" + allJobs.size());
        System.out.printf("Average Waiting Time: %.0f ms%n", avgWaiting);
        System.out.printf("Average Turnaround Time: %.0f ms%n", avgTurnaround);
        System.out.printf("Average Resource Wait Time: %.0f ms%n", avgResourceWait);
        System.out.printf("Throughput: %.2f jobs/sec%n", throughput);
        System.out.println();
        System.out.println("Per-job metrics:");
        System.out.printf("%-8s %-10s %-10s %-12s %-12s%n",
                "Job", "Wait(ms)", "TAT(ms)", "ResWait(ms)", "Check(ms)");

        for (JobMetric metric : completed) {
            Job job = findJob(allJobs, metric.jobId);
            long expected = metric.waitingMs;
            if (job != null) {
                expected += job.workMs + metric.resourceWaitMs + job.resourceMs;
            }
            System.out.printf("%-8s %-10d %-10d %-12d %-12d%n",
                    metric.jobId, metric.waitingMs, metric.turnaroundMs,
                    metric.resourceWaitMs, expected);
        }
    }

    private Job findJob(List<Job> jobs, String id) {
        for (Job job : jobs) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return null;
    }
}
