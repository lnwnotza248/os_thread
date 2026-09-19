import java.util.List;

public final class MultiResourceTest {
    private MultiResourceTest() {
    }

    public static void main(String[] args) throws Exception {
        ResourceManager resources = new ResourceManager(1, 1);
        if (!resources.tryAcquireAll(List.of(ResourceType.DATABASE, ResourceType.PRINTER), 100)) {
            throw new AssertionError("Available resources were not acquired");
        }
        if (resources.availablePermits(ResourceType.PRINTER) != 0
                || resources.availablePermits(ResourceType.DATABASE) != 0) {
            throw new AssertionError("Both resources were not held");
        }
        resources.releaseAll(List.of(ResourceType.DATABASE, ResourceType.PRINTER));

        resources.acquire(ResourceType.PRINTER);
        if (resources.tryAcquireAll(List.of(ResourceType.PRINTER, ResourceType.DATABASE), 25)) {
            throw new AssertionError("Busy multi-resource request unexpectedly succeeded");
        }
        if (resources.availablePermits(ResourceType.DATABASE) != 1) {
            throw new AssertionError("Failed acquisition did not roll back");
        }
        resources.release(ResourceType.PRINTER);
        System.out.println("Multi-resource deadlock prevention test passed");
    }
}
