/** จุดรวมสำหรับทดลอง Bonus โดยไม่เปลี่ยนคำสั่ง Main ของงานหลัก */
public class BonusMain {
    private static final String USAGE =
            "Bonus usage:\n" +
            "  java BonusMain aging   <csv> <workers> <printer> <database> <agingIntervalMs>\n" +
            "  java BonusMain mlfq    <csv> <workers> <printer> <database> <q0Ms> <q1Ms> <q2Ms>\n" +
            "  java BonusMain dynamic <csv> <minWorkers> <maxWorkers> <printer> <database> <threshold>\n" +
            "  java BonusMain timeout <csv> <workers> <printer> <database> <timeoutMs>\n" +
            "  java BonusMain virtual <csv> <platformWorkers> <printer> <database>\n" +
            "  java BonusMain deadlock";

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.out.println(USAGE);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "aging":
                    require(args, 6);
                    requirePositive(intArg(args[2]), "workers");
                    requirePositive(intArg(args[3]), "printerPermits");
                    requirePositive(intArg(args[4]), "databasePermits");
                    AgingBonusRunner.run(args[1], intArg(args[2]), intArg(args[3]), intArg(args[4]), longArg(args[5]));
                    break;
                case "mlfq":
                    require(args, 8);
                    requirePositive(intArg(args[2]), "workers");
                    requirePositive(intArg(args[3]), "printerPermits");
                    requirePositive(intArg(args[4]), "databasePermits");
                    MlfqBonusRunner.run(args[1], intArg(args[2]), intArg(args[3]), intArg(args[4]),
                            longArg(args[5]), longArg(args[6]), longArg(args[7]));
                    break;
                case "dynamic":
                    require(args, 7);
                    DynamicWorkerBonusRunner.run(args[1], intArg(args[2]), intArg(args[3]), intArg(args[4]),
                            intArg(args[5]), intArg(args[6]));
                    break;
                case "timeout":
                    require(args, 6);
                    requirePositive(intArg(args[2]), "workers");
                    requirePositive(intArg(args[3]), "printerPermits");
                    requirePositive(intArg(args[4]), "databasePermits");
                    TimeoutBonusRunner.run(args[1], intArg(args[2]), intArg(args[3]), intArg(args[4]), longArg(args[5]));
                    break;
                case "virtual":
                    require(args, 5);
                    requirePositive(intArg(args[2]), "platformWorkers");
                    requirePositive(intArg(args[3]), "printerPermits");
                    requirePositive(intArg(args[4]), "databasePermits");
                    VirtualThreadBonusRunner.run(args[1], intArg(args[2]), intArg(args[3]), intArg(args[4]));
                    break;
                case "deadlock":
                    require(args, 1);
                    DeadlockBonusRunner.run();
                    break;
                default:
                    throw new IllegalArgumentException("ไม่รู้จัก bonus: " + args[0]);
            }
        } catch (Exception e) {
            System.err.println("Bonus error: " + e.getMessage());
            System.err.println(USAGE);
        }
    }

    private static void require(String[] args, int expected) {
        if (args.length != expected) throw new IllegalArgumentException("จำนวน argument ไม่ถูกต้อง");
    }


    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " ต้องมีค่าตั้งแต่ 1 ขึ้นไป แต่พบ " + value);
        }
    }

    private static int intArg(String s) {
        return Integer.parseInt(s);
    }

    private static long longArg(String s) {
        return Long.parseLong(s);
    }
}
