/** ข้อมูลและสถานะของงานหนึ่งชิ้น */
public class Job {

    public final String id;
    public final long arrivalMs;
    public final int priority;
    public final long workMs;
    public final ResourceType resource;
    public final long resourceMs;
    public final int sequence;
    private volatile JobState state = JobState.ARRIVED;

    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}
