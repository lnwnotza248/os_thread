public final class MlfqPolicyTest {
    private MlfqPolicyTest() {
    }

    public static void main(String[] args) throws Exception {
        MlfqPolicy policy = new MlfqPolicy(10);
        Job first = new Job("MLFQ01", 0, 1, 20,
                ResourceType.NONE, 0, 0);
        Job second = new Job("MLFQ02", 0, 1, 20,
                ResourceType.NONE, 0, 1);

        policy.put(first);
        policy.put(second);
        if (policy.take() != first) {
            throw new AssertionError("Initial MLFQ order is not FIFO");
        }

        policy.demote(first);
        policy.put(first);
        if (policy.take() != second) {
            throw new AssertionError("Level-0 job was not preferred");
        }
        if (policy.take() != first || first.getMlfqLevel() != 1) {
            throw new AssertionError("Demoted job was not retained at level 1");
        }

        if (policy.quantumFor(first) != 20) {
            throw new AssertionError("MLFQ quantum did not double at level 1");
        }
        System.out.println("MLFQ policy test passed");
    }
}
