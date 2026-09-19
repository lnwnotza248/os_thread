import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Owns the shared fair semaphores for Printer and Database resources. */
public final class ResourceManager {
    private final Map<ResourceType, Semaphore> semaphores = new EnumMap<>(ResourceType.class);
    private final Map<ResourceType, Integer> permitCounts = new EnumMap<>(ResourceType.class);

    public ResourceManager(int printerPermits, int databasePermits) {
        if (printerPermits < 1 || databasePermits < 1) {
            throw new IllegalArgumentException("Resource permits must be positive");
        }
        semaphores.put(ResourceType.PRINTER, new Semaphore(printerPermits, true));
        semaphores.put(ResourceType.DATABASE, new Semaphore(databasePermits, true));
        permitCounts.put(ResourceType.PRINTER, printerPermits);
        permitCounts.put(ResourceType.DATABASE, databasePermits);
    }

    public void acquire(ResourceType resource) throws InterruptedException {
        // NONE has no semaphore; real resources block until a permit is available.
        Semaphore semaphore = semaphores.get(resource);
        if (semaphore != null) {
            semaphore.acquire();
        }
    }

    public void release(ResourceType resource) {
        Semaphore semaphore = semaphores.get(resource);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    public int availablePermits(ResourceType resource) {
        Semaphore semaphore = semaphores.get(resource);
        return semaphore == null ? 0 : semaphore.availablePermits();
    }

    public int totalPermits(ResourceType resource) {
        return permitCounts.getOrDefault(resource, 0);
    }

    public int usedPermits(ResourceType resource) {
        return totalPermits(resource) - availablePermits(resource);
    }

}
