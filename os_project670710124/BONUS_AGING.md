# Bonus: Aging Priority Policy

`AgingPriorityPolicy` is a development-only extension. It uses the normal
`SchedulingPolicy` interface and keeps the original `PriorityPolicy` unchanged.

When a job enters the ready queue, the policy records its queue-entry time.
When a worker requests a job, the policy calculates:

```text
effective priority = max(1, original priority - waited seconds)
```

Lower effective values are selected first. The original workload `priority`
and `sequence` fields are not modified, and `sequence` breaks effective-priority
ties deterministically.

Run it from the development project root:

```powershell
javac *.java
java Main "OS_Term_Project_Workloads\jobs_standard.csv" aging 3 1 2
```

The standard aging run completed `10/10` jobs. The feature is intentionally
not copied into `submission` until it has been reviewed and approved for the
final project package.