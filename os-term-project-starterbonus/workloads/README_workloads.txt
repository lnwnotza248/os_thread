Workloads for 517-312 Operating Systems Term Project

Files
jobs_standard.csv: Scheduling and worker-count experiments
jobs_printer.csv: Printer semaphore bottleneck experiment
jobs_db.csv: Database semaphore correctness test
jobs_same_priority.csv: Priority tie-break test
jobs_single.csv: Single-job graceful-shutdown edge case
jobs_aging.csv: Aging comparison; LOW waits behind BLOCK while HIGH jobs arrive
jobs_mlfq.csv: MLFQ quantum and requeue test
jobs_deadlock.csv: mixed resource order; loader normalizes both jobs to PRINTER then DATABASE
jobs_timeout.csv: holds DATABASE, then times out after partially acquiring PRINTER
jobs_dynamic.csv: simultaneous CPU-bound jobs for worker-pool scaling

Required experiment commands
java Main jobs_standard.csv fcfs 3 1 2
java Main jobs_standard.csv priority 3 1 2
java Main jobs_standard.csv priority 3 1 2 aging
java Main jobs_aging.csv priority 1 1 1 no-aging
java Main jobs_aging.csv priority 1 1 1 aging
java Main jobs_mlfq.csv mlfq 1 1 1
java Main jobs_printer.csv priority 3 1 2
java Main jobs_standard.csv priority 1 1 2
java Main jobs_standard.csv priority 5 1 2
java Main jobs_printer.csv priority 3 2 2

Extension checks
java Main jobs_dynamic.csv fcfs 4 1 1 dynamic
java Main jobs_timeout.csv fcfs 2 1 1 timeout=100
java Main jobs_printer.csv priority 3 1 2 virtual
java Main jobs_deadlock.csv fcfs 2 1 1

For the nondeterminism comparison, run this baseline one additional time:
java Main jobs_standard.csv priority 3 1 2

CSV format
id,arrivalMs,priority,workMs,resource,resourceMs
Line endings: LF
When parsing, trim each field before numeric/enum conversion.
For multi-resource jobs, write resource names joined by `+` in the resource column.
Acquisition uses the fixed order PRINTER then DATABASE to prevent circular wait.
