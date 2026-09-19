# Results and Analysis

Times are rounded integer milliseconds. Raw logs are in `experiment-logs/`.

| Run | Workload | Policy | Workers | Printer | Database | Completed | Avg WT | Avg TAT | Avg resource wait | Throughput |
| --- | --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| E1 | jobs_standard.csv | FCFS | 3 | 1 | 2 | 10/10 | 2965ms | 5474ms | 79ms | 1.14 jobs/s |
| E2 | jobs_standard.csv | Priority | 3 | 1 | 2 | 10/10 | 2326ms | 4958ms | 256ms | 1.08 jobs/s |
| E3 | jobs_standard.csv | Priority | 1 | 1 | 2 | 10/10 | 8511ms | 10964ms | 0ms | 0.41 jobs/s |
| E4 | jobs_standard.csv | Priority | 5 | 1 | 2 | 10/10 | 1130ms | 4088ms | 721ms | 1.29 jobs/s |
| E5 | jobs_printer.csv | Priority | 3 | 1 | 2 | 6/6 | 1155ms | 4266ms | 1620ms | 0.79 jobs/s |
| E6 | jobs_printer.csv | Priority | 3 | 2 | 2 | 6/6 | 768ms | 2566ms | 304ms | 1.41 jobs/s |
| E7 | jobs_standard.csv | Priority | 3 | 1 | 2 | 10/10 | 2328ms | 4961ms | 256ms | 1.08 jobs/s |

## Answers

### 1. What is the effect of increasing workers from 3 to 5?

For Priority on `jobs_standard.csv`, increasing workers from 3 to 5 reduced Avg WT from 2326ms to 1130ms and Avg TAT from 4958ms to 4088ms. Throughput increased from 1.08 to 1.29 jobs/s, but not proportionally because Printer contention increased: Avg resource wait rose from 256ms to 721ms.

### 2. Which jobs benefit from Priority scheduling?

The Priority run starts the available high-priority jobs J07-J10 earlier when they are ready; the recorded E2 START order begins J01, J02, J03, then J07, J08, J09, J10, followed by J06, J04, and J05. J07-J10 benefit from priority, while lower-priority J04-J06 are delayed. Priority improves WT/TAT, but throughput is slightly below FCFS (1.08 vs 1.14 jobs/s) because it changes the order and resource contention rather than increasing CPU capacity.

### 3. Where is the bottleneck when Printer permits increase from 1 to 2?

With one Printer permit, Printer is the main bottleneck: resource wait is 1620ms and throughput is 0.79 jobs/s. With two permits, wait falls to 304ms and throughput rises to 1.41 jobs/s. The limitation shifts toward worker/CPU execution time, although Printer can still show `2/2` usage and short periods of contention.

### 4. Why do repeated Priority runs differ slightly?

E2 and E7 keep the same main priority behavior and nearly identical metrics, but thread assignment and timestamps differ. For example, E2 starts J02 on Worker-1 at 124ms, while E7 starts J02 on Worker-3 at 109ms; J04 starts on Worker-1 at 5351ms in E2 but Worker-3 at 5338ms in E7. OS thread scheduling causes these small variations without changing the scheduling rules.