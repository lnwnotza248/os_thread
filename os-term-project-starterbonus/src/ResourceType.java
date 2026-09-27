/**
 * ชนิดของทรัพยากรร่วมที่ Job หนึ่งตัวอาจต้องใช้
 *
 * Job ใช้ทรัพยากรได้หลายชนิดพร้อมกัน โดยเรียงลำดับ PRINTER ก่อน DATABASE
 * เพื่อให้ทุก Job ขอ permit ด้วยลำดับเดียวกันและป้องกัน deadlock
 * ค่า NONE หมายถึงไม่ต้องใช้ทรัพยากรร่วมเลย
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 */
public enum ResourceType {
    NONE,
    PRINTER,
    DATABASE;

    /**
     * แปลงข้อความจากไฟล์ CSV เป็นค่า enum
     * ยอมรับตัวพิมพ์เล็ก/ใหญ่ปนกันได้ แต่ไม่ยอมรับชื่ออื่นนอกจาก 3 ค่าข้างบน
     *
     * @throws IllegalArgumentException เมื่อข้อความไม่ตรงกับค่าใดเลย
     */
    public static ResourceType parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("resource เป็นค่าว่าง");
        }
        String normalized = text.trim().toUpperCase();
        for (ResourceType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "resource ต้องเป็น NONE, PRINTER หรือ DATABASE เท่านั้น แต่พบ \"" + text.trim() + "\"");
    }
}
