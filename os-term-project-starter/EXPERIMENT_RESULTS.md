# Experiment Results

Values below come from the seven required runs executed from the current final source. Times are integer milliseconds; throughput is jobs/second. Because thread scheduling and `Thread.sleep()` timing are nondeterministic, small run-to-run timing differences are expected.

| # | Workload / Policy | Workers | Printer | DB | avg WT (ms) | avg TAT (ms) | Throughput (jobs/sec) | avg Resource Wait (ms) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | standard / FCFS | 3 | 1 | 2 | 2959 | 5456 | 1.15 | 78 |
| 2 | standard / Priority | 3 | 1 | 2 | 2321 | 4939 | 1.09 | 253 |
| 3 | standard / Priority | 1 | 1 | 2 | 8507 | 10949 | 0.41 | 0 |
| 4 | standard / Priority | 5 | 1 | 2 | 1128 | 4068 | 1.30 | 711 |
| 5 | printer / Priority | 3 | 1 | 2 | 1156 | 4252 | 0.80 | 1617 |
| 6 | printer / Priority | 3 | 2 | 2 | 771 | 2558 | 1.43 | 309 |
| 7 | standard / Priority (repeat) | 3 | 1 | 2 | 2321 | 4940 | 1.09 | 252 |

## Required analysis

### 1. Workers: 3 -> 5

In these runs, throughput increased from 1.09 jobs/sec with 3 workers to 1.30 jobs/sec with 5 workers, so it did not increase in direct proportion to worker count. Average resource wait increased from 253 ms to 711 ms because more workers could reach the shared resources concurrently and contend for limited permits; shared resources therefore limit scaling.

### 2. FCFS vs Priority

For this `jobs_standard.csv` run, Priority had lower average waiting time (2321 ms vs 2959 ms) and lower average turnaround time (4939 ms vs 5456 ms). The START order shows that higher-priority jobs can be selected before lower-priority jobs that arrived earlier, so some higher-priority jobs benefit while lower-priority jobs can wait longer. Priority can also have slightly lower throughput in this run (1.09 vs 1.15 jobs/sec) because scheduling changes which jobs reach shared resources and can increase contention; throughput is not determined by average waiting time alone.

### 3. Printer permits: 1 -> 2

For the printer workload, increasing printer permits from 1 to 2 reduced average resource wait from 1617 ms to 309 ms and increased throughput from 0.80 to 1.43 jobs/sec. The one-permit printer is a clear bottleneck; with two permits, printer contention is reduced and the limiting work shifts toward the remaining processing/resource demand rather than a single printer permit.

### 4. Repeat run (#2 vs #7)

The workload and command-line configuration were unchanged, but the runs can have slightly different timestamps, event ordering, and measured values because the operating system schedules threads nondeterministically and `Thread.sleep()` does not guarantee an exact wake-up time. In the recorded runs, avg WT was 2321 vs 2321 ms, avg TAT 4939 vs 4940 ms, avg Resource Wait 253 vs 252 ms, and throughput 1.09 vs 1.09 jobs/sec. These are small timing variations, not a change in the scheduling rule or input.

## Raw-log note

Rows 2 and 7 have separate raw logs in `logs/`. The two runs both completed 10/10 jobs with the same command configuration. Event timestamps can differ slightly even when the same deterministic scheduling rule is used.
