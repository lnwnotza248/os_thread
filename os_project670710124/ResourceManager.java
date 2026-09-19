import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

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

    /** Attempts to acquire a permit within the given timeout. */
    public boolean tryAcquire(ResourceType resource, long timeoutMs)
            throws InterruptedException {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        Semaphore semaphore = semaphores.get(resource);
        return semaphore == null || semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Acquires multiple resources in a stable order and rolls back on timeout. */
    public boolean tryAcquireAll(Collection<ResourceType> requested, long timeoutMs)
            throws InterruptedException {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        List<ResourceType> ordered = orderedResources(requested);
        List<ResourceType> acquired = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            for (ResourceType resource : ordered) {
                long remaining = deadline - System.nanoTime();
                if (remaining < 0 || !semaphores.get(resource).tryAcquire(
                        remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
                acquired.add(resource);
            }
            return true;
        } finally {
            if (acquired.size() != ordered.size()) {
                for (ResourceType resource : acquired) {
                    release(resource);
                }
            }
        }
    }

    /** Acquires multiple resources in stable order and rolls back on interruption. */
    public void acquireAll(Collection<ResourceType> requested) throws InterruptedException {
        List<ResourceType> ordered = orderedResources(requested);
        List<ResourceType> acquired = new ArrayList<>();
        try {
            for (ResourceType resource : ordered) {
                semaphores.get(resource).acquire();
                acquired.add(resource);
            }
        } catch (InterruptedException exception) {
            for (ResourceType resource : acquired) {
                release(resource);
            }
            throw exception;
        }
    }

    private List<ResourceType> orderedResources(Collection<ResourceType> requested) {
        List<ResourceType> ordered = new ArrayList<>(requested);
        ordered.removeIf(resource -> resource == ResourceType.NONE);
        ordered.sort(Comparator.comparingInt(Enum::ordinal));
        return ordered;
    }

    public void releaseAll(Collection<ResourceType> resources) {
        for (ResourceType resource : resources) {
            release(resource);
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
