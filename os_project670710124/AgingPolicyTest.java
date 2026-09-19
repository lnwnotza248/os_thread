public final class AgingPolicyTest {
    private AgingPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        AgingPriorityPolicy policy = new AgingPriorityPolicy();
        Job waitingJob = new Job("WAITING", 0, 5, 1,
                ResourceType.NONE, 0, 0);
        Job newerHighPriorityJob = new Job("NEW", 0, 1, 1,
                ResourceType.NONE, 0, 1);

        policy.put(waitingJob);
        Thread.sleep(5100);
        policy.put(newerHighPriorityJob);

        Job selected = policy.take();
        if (selected != waitingJob) {
            throw new AssertionError("Aging did not prevent starvation");
        }

        System.out.println("Aging policy test passed: " + selected.getId());
    }
}
