/** สถานะของ Job ที่ใช้ในระบบ ไม่ใช่ Thread.State */
public enum JobState {
    ARRIVED,
    READY,
    RUNNING,
    WAITING_RESOURCE,
    COMPLETED
}
