import java.util.concurrent.Semaphore;

/**
 * จัดการสิทธิ์ใช้ทรัพยากรร่วมของระบบ
 *
 * PRINTER และ DATABASE ใช้ Semaphore คนละตัว และสร้างแบบ fair
 * เพื่อให้การรอสิทธิ์มีลำดับที่ยุติธรรมตามคิวของ Semaphore
 */
public class ResourceManager {

    private final int printerCapacity;
    private final int databaseCapacity;
    private final Semaphore printer;
    private final Semaphore database;

    public ResourceManager(int printerPermits, int databasePermits) {
        if (printerPermits < 1 || databasePermits < 1) {
            throw new IllegalArgumentException("resource permits ต้องมีค่าตั้งแต่ 1 ขึ้นไป");
        }

        this.printerCapacity = printerPermits;
        this.databaseCapacity = databasePermits;
        this.printer = new Semaphore(printerPermits, true);
        this.database = new Semaphore(databasePermits, true);
    }

    /**
     * ขอสิทธิ์ใช้ทรัพยากร และ block อย่างปลอดภัยเมื่อ permit เต็ม
     */
    public void acquire(ResourceType type) throws InterruptedException {
        switch (type) {
            case NONE:
                return;
            case PRINTER:
                printer.acquire();
                return;
            case DATABASE:
                database.acquire();
                return;
            default:
                throw new IllegalArgumentException("ไม่รู้จัก resource: " + type);
        }
    }

    /** ขอ resource แบบมีเวลารอจำกัด ใช้สำหรับ Bonus timeout/cancellation */
    public boolean tryAcquire(ResourceType type, long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0) throw new IllegalArgumentException("timeoutMs ต้องไม่ติดลบ");
        switch (type) {
            case NONE:
                return true;
            case PRINTER:
                return printer.tryAcquire(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            case DATABASE:
                return database.tryAcquire(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            default:
                throw new IllegalArgumentException("ไม่รู้จัก resource: " + type);
        }
    }

    /**
     * คืนสิทธิ์ใช้ทรัพยากรหลังใช้งานเสร็จ
     */
    public void release(ResourceType type) {
        switch (type) {
            case NONE:
                return;
            case PRINTER:
                printer.release();
                return;
            case DATABASE:
                database.release();
                return;
            default:
                throw new IllegalArgumentException("ไม่รู้จัก resource: " + type);
        }
    }

    /**
     * snapshot แบบสั้นสำหรับ Monitor/Log
     * used/total = จำนวนที่กำลังถูกใช้งาน / จำนวน permit ทั้งหมด
     */
    public String status() {
        int printerUsed = printerCapacity - printer.availablePermits();
        int databaseUsed = databaseCapacity - database.availablePermits();
        return String.format("printer=%d/%d database=%d/%d",
                printerUsed, printerCapacity,
                databaseUsed, databaseCapacity);
    }

    public int printerInUse() {
        return printerCapacity - printer.availablePermits();
    }

    public int databaseInUse() {
        return databaseCapacity - database.availablePermits();
    }
}
