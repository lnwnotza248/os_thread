# Bonus: Resource Timeout

The development version now includes `ResourceManager.tryAcquire(resource, timeoutMs)`.
It waits for a permit only up to the requested duration and returns `false` if
the resource remains unavailable.

`Worker` can use this API through an optional sixth argument:

```powershell
java Main <workload.csv> <fcfs|priority|aging> <workers> <printerPermits> <databasePermits> <resourceTimeoutMs>
```

When a resource cannot be acquired within the timeout, the worker logs a
timeout and requeues the job. Omitting the sixth argument preserves blocking
resource acquisition.

Run the focused test:

```powershell
javac *.java
java ResourceTimeoutTest
java ResourceTimeoutWorkerTest
```

Expected result:

```text
Resource timeout test passed; waited=...ms
Worker resource timeout test passed
```

The tests confirm that a busy Printer times out, an available Printer can be
acquired immediately, permits are released correctly, and a timed-out worker
can recover and complete after the resource becomes available. This remains a
development-only bonus; the separate submission folder is unchanged.
