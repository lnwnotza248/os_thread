import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** ป้องกัน deadlock โดยกำหนดลำดับการขอ resource แบบเดียวกันทั้งระบบ */
public final class DeadlockSafeResourceManager {
    private final Map<ResourceType, Semaphore> semaphores = new EnumMap<>(ResourceType.class);

    public DeadlockSafeResourceManager(int printerPermits, int databasePermits) {
        if (printerPermits < 1 || databasePermits < 1) {
            throw new IllegalArgumentException("resource permits ต้องมีค่าตั้งแต่ 1 ขึ้นไป");
        }
        semaphores.put(ResourceType.PRINTER, new Semaphore(printerPermits, true));
        semaphores.put(ResourceType.DATABASE, new Semaphore(databasePermits, true));
    }

    public List<ResourceType> acquireAll(List<ResourceType> requested) throws InterruptedException {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("ต้องขอ resource อย่างน้อย 1 ชนิด");
        }
        Set<ResourceType> unique = new HashSet<>(requested);
        if (unique.size() != requested.size()) {
            throw new IllegalArgumentException("ห้ามขอ resource ชนิดเดิมซ้ำใน request เดียว");
        }
        for (ResourceType type : requested) {
            if (type == null || type == ResourceType.NONE || !semaphores.containsKey(type)) {
                throw new IllegalArgumentException("resource ไม่ถูกต้อง: " + type);
            }
        }
        List<ResourceType> ordered = new ArrayList<>(requested);
        ordered.sort(Comparator.comparingInt(DeadlockSafeResourceManager::rank));
        List<ResourceType> acquired = new ArrayList<>();
        try {
            for (ResourceType type : ordered) {
                semaphores.get(type).acquire();
                acquired.add(type);
            }
            return acquired;
        } catch (InterruptedException e) {
            releaseAll(acquired);
            throw e;
        } catch (RuntimeException e) {
            releaseAll(acquired);
            throw e;
        }
    }

    public void releaseAll(List<ResourceType> acquired) {
        for (int i = acquired.size() - 1; i >= 0; i--) {
            semaphores.get(acquired.get(i)).release();
        }
    }

    private static int rank(ResourceType type) {
        if (type == ResourceType.PRINTER) return 0;
        if (type == ResourceType.DATABASE) return 1;
        return 2;
    }
}
