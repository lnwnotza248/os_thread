import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 * - หัวข้อ 5: ใช้ Semaphore ควบคุม PRINTER และ DATABASE
 * จำนวน permit มาจาก command line (Config)
 * ในส่วนบังคับให้สร้าง Semaphore แบบ fair = true
 * - Worker ทุกตัวต้องใช้ ResourceManager object เดียวกัน
 * - หัวข้อ 7: permit ต้องไม่สูญหายหรือค้าง แม้เกิด exception
 * หรือถูก interrupt ระหว่างถือ resource
 *
 * คำถามที่จะถูกถามใน Demo:
 * - ทำไมต้อง fair = true และถ้าเปลี่ยนเป็น false จะเกิดอะไรขึ้น
 * ตอบtrue หมายถึง Worker ที่เข้าคิวรอก่อนควรได้ permit ก่อน false หมายถึงอาจเกิดการแซงคิวได้ (มาถึงตอน permit ถูก release พอดี)
 * - ถ้า Thread ถูก interrupt หลัง acquire สำเร็จแต่ก่อน release
 * โค้ดของกลุ่มยังคืน permit ได้หรือไม่ 
 * ตอบ Semaphore ไม่คืน permit อัตโนมัติ แต่ Worker รับประกันว่าจะเรียก release() ใน finally
 */
public class ResourceManager {

    private final Semaphore printerPermits;
    private final Semaphore databasePermits;
    private final int printerTotal;
    private final int databaseTotal;

    public ResourceManager(int printerPermits, int databasePermits) {
        // TODO
        this.printerPermits = new Semaphore(printerPermits, true);// fair = true เป็นข้อกำหนดของโจทย์ ช่วยให้ Worker
                                                                  // ที่รอก่อนได้สิทธิ์ก่อน ลดโอกาสที่ Thread ใด Thread
                                                                  // หนึ่งจะถูกแซงซ้ำ ๆ
        this.databasePermits = new Semaphore(databasePermits, true);
        this.printerTotal = printerPermits;
        this.databaseTotal = databasePermits;
    }

    /** ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้ */
    public void acquire(ResourceType type) throws InterruptedException {
        switch (type) {
            case PRINTER:
                printerPermits.acquire();
                break;
            case DATABASE:
                databasePermits.acquire();
                break;
            case NONE:
                break;
        }
    }

    /** คืนสิทธิ์ใช้ทรัพยากร */
    public void release(ResourceType type) {
        switch (type) {
            case PRINTER:
                printerPermits.release();
                break;
            case DATABASE:
                databasePermits.release();
                break;
            case NONE:
                break;
        }
    }

    /**
     * ข้อความสั้น ๆ บอกสถานะการใช้ทรัพยากร สำหรับส่งให้ ProjectLogger.monitor()
     * เช่น "printer=1/1 database=0/2"
     */
    public String status() {
        int printerUsed = printerTotal - printerPermits.availablePermits();
        int databaseUsed = databaseTotal - databasePermits.availablePermits();

        return String.format(
                "printer=%d/%d database=%d/%d",
                printerUsed, printerTotal,
                databaseUsed, databaseTotal);
    }
}
