# Java Thread Job Scheduler

## Compile

```powershell
javac *.java
```

## Run

```powershell
java Main <workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>
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

The current implementation includes logging and interruption testing. Experiment results should be captured separately by running the required workload commands.

## Experiment results

The seven required runs, one-page results table, and four analysis answers are recorded in:

- `RESULTS_AND_ANALYSIS.md`
- `experiment-logs/`

`AI_USE_RECORD.md` records the AI assistance and human verification process.

## Known limitations

- Each job uses at most one shared resource type (`PRINTER`, `DATABASE`, or `NONE`).
- FCFS and Priority scheduling are non-preemptive.
- Real timestamps and thread interleavings can vary slightly between runs.
- `Thread.sleep()` simulates CPU and resource time; it is not real CPU or I/O work.

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
