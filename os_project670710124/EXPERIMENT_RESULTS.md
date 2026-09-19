# Experiment Results

All runs used the five-argument command format. Times are rounded integer milliseconds.

| Run | Workload | Policy | Workers | Printer permits | Database permits | Completed | Avg WT | Avg TAT | Avg resource wait | Throughput |
| --- | --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| E1 | jobs_standard.csv | FCFS | 3 | 1 | 2 | 10/10 | 2965ms | 5474ms | 79ms | 1.14 jobs/s |
| E2 | jobs_standard.csv | Priority | 3 | 1 | 2 | 10/10 | 2326ms | 4958ms | 256ms | 1.08 jobs/s |
| E3 | jobs_standard.csv | Priority | 1 | 1 | 2 | 10/10 | 8511ms | 10964ms | 0ms | 0.41 jobs/s |
| E4 | jobs_standard.csv | Priority | 5 | 1 | 2 | 10/10 | 1130ms | 4088ms | 721ms | 1.29 jobs/s |
| E5 | jobs_printer.csv | Priority | 3 | 1 | 2 | 6/6 | 1155ms | 4266ms | 1620ms | 0.79 jobs/s |
| E6 | jobs_printer.csv | Priority | 3 | 2 | 2 | 6/6 | 768ms | 2566ms | 304ms | 1.41 jobs/s |
| E7 | jobs_standard.csv | Priority | 3 | 1 | 2 | 10/10 | 2328ms | 4961ms | 256ms | 1.08 jobs/s |

## Observations

- Priority with three workers produced lower average waiting and turnaround than FCFS in this run, while throughput was similar and affected by workload timing.
- Increasing workers from one to five reduced average waiting from 8511ms to 1130ms and increased throughput from 0.41 to 1.29 jobs/s.
- Increasing Printer permits from one to two reduced average Printer resource wait from 1620ms to 304ms and improved throughput from 0.79 to 1.41 jobs/s.
- The repeated priority baseline was close to the first priority run: WT 2328ms versus 2326ms and TAT 4961ms versus 4958ms. Small differences are expected from thread scheduling and OS timing.
- Every required run completed all jobs, and the Printer permit comparison shows the semaphore bottleneck clearly.

## Bottleneck analysis

The Printer is the bottleneck in the printer workload. With one Printer permit,
average resource wait was `1620ms` and throughput was `0.79 jobs/s`. Increasing
the permits to two reduced resource wait to `304ms` and increased throughput to
`1.41 jobs/s`. Monitor lines such as
`printer=1/1` while jobs remain ready confirm that the Printer is saturated.

The Database workload uses two permits and completed normally. Its resource
wait is much lower than the one-permit Printer case, so the Database is not the
dominant bottleneck in that experiment.

The one-worker standard run is CPU/worker limited rather than resource limited:
resource wait was `0ms`, while average waiting time was `8511ms`. Adding more
workers reduced the ready-queue waiting time and increased throughput.

## Raw logs

- `experiment-logs/E1_standard_fcfs_3w.txt`
- `experiment-logs/E2_standard_priority_3w.txt`
- `experiment-logs/E3_standard_priority_1w.txt`
- `experiment-logs/E4_standard_priority_5w.txt`
- `experiment-logs/E5_printer_1permit.txt`
- `experiment-logs/E6_printer_2permits.txt`
- `experiment-logs/E7_standard_priority_baseline_repeat.txt`
