# Resource Wait regression test

Run from the project root with Java 21 or newer:

```powershell
javac -encoding UTF-8 -d build src/*.java tests/ResourceWaitTest.java
java -cp build ResourceWaitTest
java -cp build Main workloads/jobs_timeout.csv fcfs 2 1 1 timeout=100
```

Resource Wait now measures the elapsed time around acquire/tryAcquire, after writing the WAIT log. It excludes logging before the call but still includes scheduling delays while the call is in progress. A timeout or interruption also retains the elapsed wait on the Job. Summary averages still include completed jobs only; cancellations are counted separately.

The test checks that a deliberately slow 300ms WAIT log is excluded, timeout/interruption release the completion waiter, cancellation is recorded once, and permits are restored.

Earlier benchmark logs predate this measurement correction. Keep them as historical results; rerun experiments before reporting corrected Resource Wait values. Logging and OS scheduling still contribute to real turnaround time, so the nominal-duration equation is approximate.
