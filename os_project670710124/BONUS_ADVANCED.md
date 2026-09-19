# Advanced Development Bonuses

## Dynamic workers

`DynamicWorkerPool` accepts minimum and maximum worker bounds, a queue-length
supplier, and a worker factory. It adds workers when queued work is available
and requests cooperative retirement when the queue becomes empty. The
`dynamic` Main policy uses one initial worker and scales up to the requested
worker count.

```powershell
javac *.java
java DynamicWorkerPoolTest
java Main "OS_Term_Project_Workloads\jobs_standard.csv" dynamic 5 1 2
```

## Cancellation and resource timeout

Call `job.cancel()` before a worker takes the job. The worker marks it
`CANCELLED`, records the cancellation, and does not execute CPU or resource
work. Resource timeout remains available through the optional `Main` argument:

```powershell
java Main "OS_Term_Project_Workloads\jobs_standard.csv" priority 3 1 2 1000
java CancellationTest
java ResourceTimeoutWorkerTest
```

## Virtual-thread comparison

The comparison runs the same sleep-heavy task set using platform threads and
virtual threads:

```powershell
java VirtualThreadComparison 100 50
```

The output reports elapsed milliseconds for each mode. Results depend on the
machine and workload; the tool is intended for comparison rather than a fixed
performance claim.

## Multi-resource deadlock prevention

Jobs can be created with a resource list:

```java
new Job("BOTH", 0, 1, 10,
        List.of(ResourceType.DATABASE, ResourceType.PRINTER), 5, 0);
```

`ResourceManager.acquireAll()` and `tryAcquireAll()` sort requests into a
stable order. If acquisition fails, already-held permits are released. This
prevents the circular wait pattern where two jobs each hold one resource and
wait for the other.

```powershell
java MultiResourceTest
```

All advanced bonuses are development-only. The separate submission folder is
unchanged.