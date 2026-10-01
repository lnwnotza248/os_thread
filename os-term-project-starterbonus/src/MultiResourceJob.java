import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Job สำหรับ deadlock bonus ที่ขอ resource มากกว่า 1 ชนิด */
public final class MultiResourceJob {
    public final String id;
    public final List<ResourceType> resources;
    public final long workMs;
    public final long resourceMs;

    public MultiResourceJob(String id, List<ResourceType> resources, long workMs, long resourceMs) {
        this.id = id;
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        this.workMs = workMs;
        this.resourceMs = resourceMs;
    }
}
