# Analysis Answers

These answers are based on the measurements in `EXPERIMENT_RESULTS.md` and the raw logs in `experiment-logs/`.

## 1. FCFS versus Priority

For the three-worker standard workload, Priority produced lower average waiting time and turnaround time than FCFS in the recorded runs. FCFS measured 2965ms average waiting and 5474ms average turnaround. Priority measured 2326ms waiting and 4958ms turnaround. Priority favors higher-priority ready jobs, where a lower priority number is higher priority. It does not select jobs that have not arrived yet.

## 2. Effect of worker count

Increasing the worker count improved the standard workload results. With one worker, average waiting was 8511ms and throughput was 0.41 jobs/s. With five workers, average waiting was 1130ms and throughput was 1.29 jobs/s. More workers allow CPU work to overlap, although resource contention can still limit the improvement.

## 3. Effect of Printer permits

The Printer is the bottleneck in `jobs_printer.csv` when only one permit is available. With one permit, average resource wait was 1620ms and throughput was 0.79 jobs/s. With two permits, resource wait fell to 304ms and throughput increased to 1.41 jobs/s. The logs also show the Printer reaching `1/1` usage while jobs wait.

## 4. Repeatability and nondeterminism

The repeated three-worker Priority baseline was very close to the first run. Average waiting changed from 2326ms to 2328ms, and turnaround changed from 4958ms to 4961ms. Small changes are expected because thread scheduling and wake-up timing are controlled by the operating system. The scheduling and semaphore rules remain the same even when event timestamps or interleavings differ.
