import java.util.List;

public final class Job {
    private final String id;
    private final long arrivalMs;
    private final int priority;
    private final long workMs;
    private final ResourceType resource;
    private final List<ResourceType> resources;
    private final long resourceMs;
    private final int sequence;

    private volatile JobState state;
    private volatile boolean cancelled;
    private volatile long remainingWorkMs;
    private volatile int mlfqLevel;
    private volatile long actualArrivalTime;
    private volatile long startTime;
    private volatile long resourceWaitStartTime;
    private volatile long resourceAcquiredTime;
    private volatile long completionTime;

    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this(id, arrivalMs, priority, workMs, List.of(resource), resourceMs, sequence);
    }

    public Job(String id, long arrivalMs, int priority, long workMs,
               List<ResourceType> resources, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resources = List.copyOf(resources);
        this.resource = this.resources.isEmpty() ? ResourceType.NONE : this.resources.get(0);
        this.resourceMs = resourceMs;
        this.sequence = sequence;
        this.state = JobState.ARRIVED;
        this.remainingWorkMs = workMs;
        this.mlfqLevel = 0;
    }

    public String getId() {
        return id;
    }

    public long getArrivalMs() {
        return arrivalMs;
    }

    public int getPriority() {
        return priority;
    }

    public long getWorkMs() {
        return workMs;
    }

    public long getRemainingWorkMs() {
        return remainingWorkMs;
    }

    public void setRemainingWorkMs(long remainingWorkMs) {
        this.remainingWorkMs = Math.max(0, remainingWorkMs);
    }

    public int getMlfqLevel() {
        return mlfqLevel;
    }

    public void setMlfqLevel(int mlfqLevel) {
        this.mlfqLevel = Math.max(0, Math.min(2, mlfqLevel));
    }

    public ResourceType getResource() {
        return resource;
    }

    public List<ResourceType> getResources() {
        return resources;
    }

    public long getResourceMs() {
        return resourceMs;
    }

    public int getSequence() {
        return sequence;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    public long getActualArrivalTime() {
        return actualArrivalTime;
    }

    public void setActualArrivalTime(long actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getResourceWaitStartTime() {
        return resourceWaitStartTime;
    }

    public void setResourceWaitStartTime(long resourceWaitStartTime) {
        this.resourceWaitStartTime = resourceWaitStartTime;
    }

    public long getResourceAcquiredTime() {
        return resourceAcquiredTime;
    }

    public void setResourceAcquiredTime(long resourceAcquiredTime) {
        this.resourceAcquiredTime = resourceAcquiredTime;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    @Override
    public String toString() {
        return id + " arrivalMs=" + arrivalMs
                + " priority=" + priority
                + " workMs=" + workMs
                + " resource=" + resource
                + " resourceMs=" + resourceMs
                + " sequence=" + sequence;
    }
}
