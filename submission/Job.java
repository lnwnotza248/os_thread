public final class Job {
    private final String id;
    private final long arrivalMs;
    private final int priority;
    private final long workMs;
    private final ResourceType resource;
    private final long resourceMs;
    private final int sequence;

    private volatile JobState state;
    private volatile long actualArrivalTime;
    private volatile long startTime;
    private volatile long resourceWaitStartTime;
    private volatile long resourceAcquiredTime;
    private volatile long completionTime;

    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
        this.state = JobState.ARRIVED;
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

    public ResourceType getResource() {
        return resource;
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
