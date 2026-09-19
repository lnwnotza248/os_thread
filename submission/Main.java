import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds and joins the generator, scheduler, workers, and monitor pipeline. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            printUsage();
            return;
        }

        String policyName = args[1].toLowerCase(Locale.ROOT);
        SchedulingPolicy policy;
        if (policyName.equals("fcfs")) {
            policy = new FcfsPolicy();
        } else if (policyName.equals("priority")) {
            policy = new PriorityPolicy();
        } else {
            System.out.println("Policy must be fcfs or priority");
            printUsage();
            return;
        }

        int workerCount;
        int printerPermits;
        int databasePermits;
        try {
            workerCount = parsePositive(args[2]);
            printerPermits = parsePositive(args[3]);
            databasePermits = parsePositive(args[4]);
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
            printUsage();
            return;
        }
        List<Job> jobs = WorkloadLoader.load(Path.of(args[0]));
        List<Job> executionOrder = Collections.synchronizedList(new ArrayList<>());
        long runStartTime = System.currentTimeMillis();
        ProjectLogger logger = new ProjectLogger(runStartTime);
        Statistics statistics = new Statistics(runStartTime);
        ResourceManager resources = new ResourceManager(printerPermits, databasePermits);
        AtomicInteger remainingJobs = new AtomicInteger(jobs.size());
        BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();

        Thread generator = new Thread(new JobGenerator(jobs, arrivalQueue, logger,
            runStartTime), "JobGenerator");
        Thread scheduler = new Thread(new Scheduler(arrivalQueue, policy, jobs.size(), logger), "Scheduler");
        List<Thread> workers = new ArrayList<>();
        for (int i = 1; i <= workerCount; i++) {
            workers.add(new Thread(new Worker(policy, remainingJobs, executionOrder,
                    resources, runStartTime, statistics, logger), "Worker-" + i));
        }
        Thread monitor = new Thread(new Monitor(policy, statistics, resources, logger, 1000), "Monitor");

        generator.start();
        scheduler.start();
        for (Thread worker : workers) {
            worker.start();
        }
        monitor.start();
        generator.join();
        scheduler.join();
        for (Thread worker : workers) {
            worker.join();
        }
        monitor.interrupt();
        monitor.join();
        printReport(jobs, policyName, executionOrder, runStartTime, statistics);
    }

    private static void printReport(List<Job> jobs, String policyName,
                                    List<Job> executionOrder, long runStartTime,
                                    Statistics statistics) {
        System.out.println("Loaded jobs: " + jobs.size());
        System.out.println("Policy: " + policyName);
        System.out.println("Execution order:");
        for (Job job : executionOrder) {
            System.out.println(job.getId() + " state=" + job.getState()
                    + " start=" + (job.getStartTime() - runStartTime) + "ms"
                    + " done=" + (job.getCompletionTime() - runStartTime) + "ms");
        }
        System.out.printf("Summary: completed=%d/%d turnaround=%dms "
            + "waiting=%dms resourceWait=%dms throughput=%.2f jobs/s%n",
                statistics.getCompletedJobs(), jobs.size(),
            Math.round(statistics.getAverageTurnaroundMs()),
            Math.round(statistics.getAverageWaitingMs()),
            Math.round(statistics.getAverageResourceWaitMs()),
                statistics.getThroughputJobsPerSecond());
    }

    private static void printUsage() {
        System.out.println("Usage: java Main <workload.csv> <fcfs|priority>"
                + " <workers> <printerPermits> <databasePermits>");
    }

    private static int parsePositive(String value) {
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Arguments must be positive", exception);
        }
        if (number < 1) {
            throw new IllegalArgumentException("Arguments must be positive");
        }
        return number;
    }
}
