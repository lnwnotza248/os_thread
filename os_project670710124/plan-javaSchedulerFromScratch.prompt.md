## Plan: Java Scheduler From Scratch

TL;DR: Build the required scheduler in small, testable layers: data/loading, scheduling queues, resource control, workers, shutdown/monitoring, metrics/logging, then run the required experiments and prepare submission files.

**Steps**
1. Confirm local prerequisites: JDK installed and `java`/`javac` available. Keep all Java files in `c:\Users\not24\OneDrive\Desktop\os_project670710124` or a clearly named `src` folder; use the workload CSV directory as the input location.
2. Create the minimal data model and loader: `Job` with id, arrivalMs, priority, workMs, resource, resourceMs, actual-arrival/start/completion timestamps, resource-wait timestamp, and a project JobState enum. Reuse the supplied CSV format and trim fields while parsing.
3. Add scheduling abstractions: a common `SchedulingPolicy` interface, FCFS queue, and Priority queue. Priority must treat 1 as highest and use a deterministic tie-break independent of thread timing, such as a sequence assigned from workload order. Make queue operations blocking so workers do not busy-wait.
4. Add the required thread pipeline: `JobGenerator` sleeps until each job's arrival time and puts jobs on `arrivalQueue`; `Scheduler` takes from that queue and puts jobs into the selected ready policy; `Worker` threads take jobs from the shared ready policy and process them. Do not let the generator bypass the scheduler.
5. Add `ResourceManager`: one shared fair Semaphore for PRINTER and one for DATABASE, with permit counts from command-line arguments. In worker processing, acquire/release in a `try/finally`-equivalent structure and preserve correctness on interruption.
6. Add thread-safe `Statistics` and `ProjectLogger`: record actual arrival, start, resource wait, completion, per-job metrics, and synchronized event lines containing elapsed time and thread name. Add a `Monitor` thread that periodically prints a safe snapshot of ready/running/completed counts and resource usage.
7. Design shutdown before final integration: track generator completion and total unfinished jobs; ensure the scheduler and workers receive an explicit end-of-stream signal or equivalent; wait for all jobs to complete before stopping workers and monitor. Join every thread and do not use `System.exit()` or arbitrary sleeps for shutdown.
8. Add `Main` argument validation for exactly five arguments: workload path, `fcfs|priority`, positive worker count, positive printer permits, and positive database permits. Print usage and exit before starting any thread when invalid. Wire construction, startup, joins, final summary, and throughput calculation.
9. Validate incrementally with the supplied workloads: first `jobs_single.csv`; then `jobs_standard.csv` with multiple workers and both policies; then `jobs_printer.csv` with printer permits 1 and 2; then `jobs_db.csv` with database permits 2; then `jobs_same_priority.csv` for tie-breaking. Also test interruption while waiting for a resource and verify the JVM terminates after the summary.
10. Run and capture the required experiments from `OS_Term_Project_Workloads/README_workloads.txt`, including the repeated priority baseline. Fill the seven-row results table with average WT, average TAT, throughput, and average resource wait, then answer the four required analysis questions from actual logs.
11. Prepare submission artifacts: concise `README.md` with compile/run commands and limitations, raw logs for each experiment, one-page results/answers, and an AI-use record describing assistance and at least one human correction/check.

**Relevant files**
- `c:\Users\not24\OneDrive\Desktop\os_project670710124\OS_Term_Project_Java_Thread_Job_Scheduler.pdf` — authoritative requirements, thread architecture, metrics, shutdown rules, experiments, and grading.
- `c:\Users\not24\OneDrive\Desktop\os_project670710124\OS_Term_Project_Workloads\README_workloads.txt` — exact workload format and required run commands.
- `c:\Users\not24\OneDrive\Desktop\os_project670710124\OS_Term_Project_Workloads\jobs_*.csv` — test inputs; do not hard-code their job IDs or counts.
- New Java files in the project root or `src`: `Main.java`, `Job.java`, `JobState.java`, `WorkloadLoader.java`, `SchedulingPolicy.java`, `FcfsPolicy.java`, `PriorityPolicy.java`, `JobGenerator.java`, `Scheduler.java`, `Worker.java`, `ResourceManager.java`, `Statistics.java`, `ProjectLogger.java`, and `Monitor.java`.

**Verification**
1. Compile all Java files with `javac` and run `jobs_single.csv`; confirm one completion and automatic shutdown.
2. Run the standard FCFS and Priority commands; inspect START order, timestamps, and summary metrics.
3. Run printer tests and verify no more jobs hold PRINTER than the configured permit count; compare permit 1 versus 2.
4. Run database test with two permits and verify at most two DATABASE users concurrently.
5. Run same-priority workload and verify tie-breaking follows the chosen workload sequence, not whichever thread reaches the queue first.
6. Run invalid command lines and verify usage appears before any thread starts.
7. Repeat the baseline and explain small timestamp/log-order variation while preserving scheduling/resource rules.
8. Check final JVM termination and inspect diagnostics after compilation/tests.

**Decisions**
- Build the mandatory portion first; leave Aging, MLFQ, dynamic workers, cancellation, virtual threads, and multi-resource deadlock extensions out of scope.
- Use workload order as the deterministic tie-break sequence unless the starter code or instructor specifies another rule.
- Keep actual implementation and submission documents separate from generated logs/results.
- Do not write custom CSV validation beyond what is necessary if an instructor-provided loader later appears; currently no Java starter files are present.

**Further Considerations**
1. The first implementation checkpoint should be the data model plus loader and a tiny `Main` that prints loaded jobs; this gives a cheap confirmation that paths and CSV parsing work before concurrency is introduced.
2. The shutdown protocol is the highest-risk part and should be designed before adding monitor/reporting polish.
