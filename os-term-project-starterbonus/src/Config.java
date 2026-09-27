/**
 * ค่าที่รับมาจาก command line
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 * เหตุผลที่แจกให้: ผู้ตรวจต้องสั่งรันทุกกลุ่มด้วยคำสั่งรูปแบบเดียวกัน
 * และต้องรู้ชัดว่ากำลังรันด้วยค่าใด จึงไม่มีค่า default ซ่อนอยู่เลย
 *
 * รูปแบบการใช้งาน:
 *   java Main jobs_standard.csv <fcfs|priority|mlfq> 3 1 2 [aging] [dynamic] [virtual] [timeout=ms]
 */
public final class Config {

    public static final String USAGE =
            "Usage: java Main <workload.csv> <fcfs|priority|mlfq> <maxWorkers> <printerPermits> <databasePermits> [aging] [dynamic] [virtual] [timeout=ms]\n"
          + "  workload.csv      ไฟล์ชุดงานทดสอบ\n"
                    + "  fcfs | priority | mlfq   นโยบายการจัดลำดับงาน\n"
          + "  workers           จำนวน Worker Thread (ตั้งแต่ 1 ขึ้นไป)\n"
          + "  printerPermits    จำนวนสิทธิ์ใช้ PRINTER พร้อมกัน (ตั้งแต่ 1 ขึ้นไป)\n"
          + "  databasePermits   จำนวนสิทธิ์ใช้ DATABASE พร้อมกัน (ตั้งแต่ 1 ขึ้นไป)\n"
          + "  aging             ตัวเลือกของ priority: aging เปิด หรือ no-aging ปิด (ค่าเริ่มต้น: ปิด)\n"
          + "  dynamic           ปรับจำนวน Worker อัตโนมัติระหว่าง 1 ถึง maxWorkers\n"
          + "  virtual           ใช้ Virtual Threads แทน Platform Threads (Java 21+)\n"
          + "  timeout=ms        ยกเลิกงานเมื่อรอ resource แต่ละชนิดเกินเวลาที่กำหนด\n"
          + "\n"
          + "ตัวอย่าง: java Main jobs_standard.csv priority 3 1 2";

    /** นโยบายการจัดลำดับงาน */
    public enum Policy {
        FCFS,
        PRIORITY,
        MLFQ
    }

    public final String workloadPath;
    public final Policy policy;
    public final int workers;
    public final int printerPermits;
    public final int databasePermits;
    public final boolean agingEnabled;
    public final boolean dynamicWorkers;
    public final boolean virtualThreads;
    public final long resourceTimeoutMs;

    private Config(String workloadPath, Policy policy, int workers,
                   int printerPermits, int databasePermits, boolean agingEnabled,
                   boolean dynamicWorkers, boolean virtualThreads, long resourceTimeoutMs) {
        this.workloadPath = workloadPath;
        this.policy = policy;
        this.workers = workers;
        this.printerPermits = printerPermits;
        this.databasePermits = databasePermits;
        this.agingEnabled = agingEnabled;
        this.dynamicWorkers = dynamicWorkers;
        this.virtualThreads = virtualThreads;
        this.resourceTimeoutMs = resourceTimeoutMs;
    }

    /**
     * แปลง argument จาก main() เป็น Config
     *
     * @throws IllegalArgumentException เมื่อจำนวนหรือค่าของ argument ไม่ถูกต้อง
     *         ข้อความของ exception เหมาะกับการแสดงต่อผู้ใช้โดยตรง
     */
    public static Config parse(String[] args) {
        if (args.length < 5) {
            throw new IllegalArgumentException(
            "ต้องใส่ argument อย่างน้อย 5 ตัว แต่ได้รับ " + args.length + " ตัว");
        }

        String workloadPath = args[0].trim();
        if (workloadPath.isEmpty()) {
            throw new IllegalArgumentException("ชื่อไฟล์ workload เป็นค่าว่าง");
        }

        Policy policy;
        String policyText = args[1].trim().toLowerCase();
        if (policyText.equals("fcfs")) {
            policy = Policy.FCFS;
        } else if (policyText.equals("priority")) {
            policy = Policy.PRIORITY;
        } else if (policyText.equals("mlfq")) {
            policy = Policy.MLFQ;
        } else {
            throw new IllegalArgumentException(
                    "นโยบายต้องเป็น fcfs, priority หรือ mlfq เท่านั้น แต่พบ \""
                            + args[1].trim() + "\"");
        }

        int workers = parseAtLeastOne(args[2], "workers");
        int printerPermits = parseAtLeastOne(args[3], "printerPermits");
        int databasePermits = parseAtLeastOne(args[4], "databasePermits");
        boolean agingEnabled = false;
        boolean dynamicWorkers = false;
        boolean virtualThreads = false;
        long resourceTimeoutMs = 0;
        for (int i = 5; i < args.length; i++) {
            String option = args[i].trim().toLowerCase();
            if (option.equals("aging")) {
                agingEnabled = true;
            } else if (option.equals("no-aging")) {
                agingEnabled = false;
            } else if (option.equals("dynamic")) {
                dynamicWorkers = true;
            } else if (option.equals("virtual")) {
                virtualThreads = true;
            } else if (option.startsWith("timeout=")) {
                resourceTimeoutMs = parsePositiveLong(option.substring(8), "timeout");
            } else {
                throw new IllegalArgumentException(
                        "ไม่รู้จักตัวเลือก \"" + args[i].trim() + "\"");
            }
        }
        if (agingEnabled && policy != Policy.PRIORITY) {
            throw new IllegalArgumentException("ตัวเลือก aging ใช้ได้เฉพาะเมื่อนโยบายเป็น priority");
        }

        return new Config(workloadPath, policy, workers, printerPermits, databasePermits,
                agingEnabled, dynamicWorkers, virtualThreads, resourceTimeoutMs);
    }

    private static int parseAtLeastOne(String text, String name) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text.trim() + "\"");
        }
        if (value < 1) {
            throw new IllegalArgumentException(
                    name + " ต้องมีค่าตั้งแต่ 1 ขึ้นไป แต่พบ " + value);
        }
        return value;
    }

    private static long parsePositiveLong(String text, String name) {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " ต้องเป็นจำนวนเต็มบวก แต่พบ \"" + text + "\"");
        }
        if (value < 1) {
            throw new IllegalArgumentException(name + " ต้องมีค่ามากกว่า 0 แต่พบ " + value);
        }
        return value;
    }

    /** ข้อความบรรทัดเดียวสำหรับบันทึกลง log ว่ารันด้วยค่าใด */
    public String describe() {
        return String.format("workload=%s policy=%s workers=%d printer=%d database=%d aging=%s dynamic=%s virtual=%s resourceTimeoutMs=%d",
            workloadPath, policy.name().toLowerCase(), workers, printerPermits,
            databasePermits, agingEnabled ? "on" : "off", dynamicWorkers,
            virtualThreads, resourceTimeoutMs);
    }
}
