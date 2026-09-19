# Bonus Results: Aging

## Implementation

`AgingPriorityPolicy` is a development-only policy selected with `aging`.
It keeps each job's original priority and sequence unchanged. Every 1000ms of
waiting in the ready queue reduces the job's effective priority by one level,
with a minimum effective priority of 1.

## Run

```powershell
javac *.java
java Main "OS_Term_Project_Workloads\jobs_standard.csv" aging 3 1 2
```

Observed result:

```text
Summary: completed=10/10
```

## Focused test

```powershell
java AgingPolicyTest
```

Observed result:

```text
Aging policy test passed: WAITING
```

The focused test places a priority-5 job in the queue, waits long enough for
aging to improve it, then inserts a newer priority-1 job. The older job is
selected first, demonstrating starvation prevention.

## Scope

Aging is implemented only in the development project. The separate submission
folder remains unchanged and does not include this bonus feature.
