/** Blocking ready-queue contract shared by FCFS and priority scheduling. */
public interface SchedulingPolicy {
    /** Adds a job to the ready queue. */
    void put(Job job) throws InterruptedException;

    /** Removes the next job according to the policy, waiting if empty. */
    Job take() throws InterruptedException;

    /** Returns the number of jobs currently waiting in the ready queue. */
    int size();
}
