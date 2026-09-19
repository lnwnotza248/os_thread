# Bonus: Multilevel Feedback Queue

The development version includes a three-level MLFQ policy:

- New jobs enter level 0.
- An unfinished job is demoted after each CPU quantum.
- Quantums are `base`, `2 * base`, and `4 * base` for levels 0, 1, and 2.
- Jobs at a higher level are selected before lower-level jobs.
- Jobs at the same level are served FIFO.

Run MLFQ with its default 100 ms base quantum:

```powershell
javac *.java
java Main "OS_Term_Project_Workloads\jobs_standard.csv" mlfq 3 1 2
```

The optional sixth argument enables resource timeout mode, and the optional
seventh argument sets the MLFQ base quantum:

```powershell
java Main "OS_Term_Project_Workloads\jobs_standard.csv" mlfq 3 1 2 1000 50
```

Focused validation:

```powershell
java MlfqPolicyTest
java Main "OS_Term_Project_Workloads\jobs_single.csv" mlfq 1 1 1 1000 10
```

This is development-only bonus code. The separate submission folder remains
unchanged.
