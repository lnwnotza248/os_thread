# Java Thread Job Scheduler

## Compile

```powershell
javac *.java
```

## Run

```powershell
java Main <workload.csv> <fcfs|priority|aging|mlfq|dynamic> <workers> <printerPermits> <databasePermits> [resourceTimeoutMs] [mlfqQuantumMs]
```

Example:

```powershell
java Main "OS_Term_Project_Workloads\jobs_printer.csv" priority 2 1 2
```

## Current pipeline

```text
CSV -> JobGenerator -> arrivalQueue -> Scheduler
    -> FCFS/PriorityPolicy -> Worker(s)
    -> ResourceManager -> Statistics
    -> Monitor -> final report
```

- `JobGenerator` releases jobs according to `arrivalMs`.
- `Scheduler` moves arrived jobs into the selected policy.
- `Worker` executes `workMs` and uses resource permits for `resourceMs`.
- `ResourceManager` controls Printer and Database permits.
- `Statistics` reports completed jobs, waiting time, turnaround, resource wait, and throughput.
- `ProjectLogger` prints synchronized events with elapsed time and thread name.
- `Monitor` periodically shows ready, running, and completed counts, plus Printer and Database usage.
- `InterruptTest` verifies that an interrupted job is requeued and can complete after its resource becomes available.

The development build also supports the following optional features:

- `aging` dynamically improves the effective priority of jobs waiting in the ready queue.
- `mlfq` uses three feedback levels and demotes unfinished jobs after each CPU quantum.
- `resourceTimeoutMs` makes workers requeue a job when a resource cannot be acquired within the timeout.
- `mlfqQuantumMs` sets the base MLFQ quantum and is valid only with the `mlfq` policy.
- `dynamic` uses one initial worker and scales up to the requested worker count
    according to ready-queue length; idle workers retire cooperatively.

Examples:

```powershell
java Main "OS_Term_Project_Workloads\jobs_standard.csv" aging 3 1 2
java Main "OS_Term_Project_Workloads\jobs_standard.csv" mlfq 3 1 2
java Main "OS_Term_Project_Workloads\jobs_standard.csv" mlfq 3 1 2 1000 50
java Main "OS_Term_Project_Workloads\jobs_standard.csv" dynamic 5 1 2
```

## Bonus: Aging policy

The development version also supports `aging`:

```powershell
java Main "OS_Term_Project_Workloads\jobs_standard.csv" aging 3 1 2
```

`AgingPriorityPolicy` lowers a waiting job's effective priority by one level
for every 1000ms spent in the ready queue. The original priority and sequence
remain unchanged; aging is calculated dynamically when a worker takes a job.
This bonus code is not included in the separate `submission` folder yet.

The current implementation includes logging and interruption testing. Experiment results should be captured separately by running the required workload commands.

Focused development tests:

```powershell
java InterruptTest
java AgingPolicyTest
java ResourceTimeoutTest
java ResourceTimeoutWorkerTest
java MlfqPolicyTest
java CancellationTest
java MultiResourceTest
java DynamicWorkerPoolTest
java VirtualThreadComparison 100 50
```

## Advanced development bonuses

- `CancellationTest` demonstrates a cancelled job being discarded safely.
- `ResourceManager.tryAcquireAll()` and `acquireAll()` acquire multiple resources
    in stable enum order and roll back partial acquisition, preventing circular
    wait. Multi-resource jobs use the `Job` constructor that accepts a resource
    list; the original CSV format remains single-resource for compatibility.
- `DynamicWorkerPool` grows from its minimum to maximum worker count when the
    ready queue grows and removes workers after they finish.
- `VirtualThreadComparison` compares platform and virtual threads for a
    sleep-heavy workload. It requires JDK 21 or newer.

These advanced bonuses are development-only and are not included in the
separate submission folder.

## Experiment results

The seven required runs and their raw console logs are recorded in:

- `EXPERIMENT_RESULTS.md`
- `experiment-logs/`

`EXPERIMENT_RESULTS.md` also records the bottleneck analysis and explains how
resource wait, permit saturation, and throughput identify the limiting resource.

The report support files are:

- `ANALYSIS_ANSWERS.md` - answers to the four experiment questions
- `AI_USE_RECORD.md` - AI assistance and human verification record

## Interruption test

```powershell
javac *.java
java InterruptTest
```

Expected result:

```text
Interrupt recovery test passed
```

Monitor output uses this format:

```text
STATUS ready=4 running=2 completed=0 printer=1/1 database=0/2
```
