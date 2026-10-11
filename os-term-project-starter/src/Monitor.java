
/**
 * Thread ที่รายงานสถานะระบบเป็นระยะ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ (หัวข้อ 10):
 * - รายงานประมาณทุก 1,000 ms ไม่ต้องแม่นตรงทุกครั้ง
 * - อย่างน้อยต้องมี ready, running, completed และสถานะการใช้ resource
 * - ข้อมูลที่อ่านต้องเป็น snapshot ที่ปลอดภัย
 * โดยเฉพาะตัวนับ running ซึ่ง Worker หลายตัวเพิ่ม/ลดพร้อมกัน
 * - ห้ามอ่าน collection หรือตัวนับที่กำลังถูกแก้ไขโดยไม่มีการป้องกัน
 *
 * ให้พิมพ์ผ่าน logger.monitor(ready, running, completed, resources.status())
 * เพื่อให้รูปแบบตรงกับกลุ่มอื่น
 *
 * ตัวนับสถานะทั้งสามอยู่ใน Statistics และ snapshot() อ่านค่าภายใต้ lock เดียวกัน
 */
public class Monitor extends Thread {
    
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Monitor(ResourceManager resources, Statistics statistics, ProjectLogger logger) {
        super("monitor");
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    
        @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Statistics.Snapshot snapshot = statistics.snapshot();
                logger.monitor(
                        snapshot.ready,
                        snapshot.running,
                        snapshot.completed,
                        resources.status()
                );
    
                Thread.sleep(1000);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
