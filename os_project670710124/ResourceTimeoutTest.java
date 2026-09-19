public final class ResourceTimeoutTest {
    private ResourceTimeoutTest() {
    }

    public static void main(String[] args) throws Exception {
        ResourceManager resources = new ResourceManager(1, 1);
        resources.acquire(ResourceType.PRINTER);

        long start = System.currentTimeMillis();
        boolean acquired = resources.tryAcquire(ResourceType.PRINTER, 100);
        long waitedMs = System.currentTimeMillis() - start;

        if (acquired) {
            throw new AssertionError("Busy Printer was acquired unexpectedly");
        }
        if (waitedMs < 80) {
            throw new AssertionError("Timeout returned too early: " + waitedMs + "ms");
        }

        resources.release(ResourceType.PRINTER);
        if (!resources.tryAcquire(ResourceType.PRINTER, 0)) {
            throw new AssertionError("Available Printer was not acquired");
        }
        resources.release(ResourceType.PRINTER);
        System.out.println("Resource timeout test passed; waited=" + waitedMs + "ms");
    }
}
