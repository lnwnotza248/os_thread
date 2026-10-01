# Demo Guide / โน้ตที่ใช้ทบทวน

อันนี้ตั้งใจเขียนเป็นโน้ตสั้น ๆ ไว้อ่านก่อน Demo ไม่ใช่คำตอบที่ต้องท่องทุกคำ

## 1. ภาพรวมที่ควรพูดให้ได้

Job มาจาก CSV แล้ว Generator ค่อย ๆ ปล่อยตามเวลา -> เข้า arrivalQueue -> Scheduler ย้ายเข้า ReadyQueue -> Worker หลายตัวดึงจากคิวเดียวกัน -> ถ้าต้องใช้ resource ก็ขอผ่าน ResourceManager -> ทำเสร็จแล้วบันทึกสถิติ

Monitor แยกออกมาอ่านสถานะระบบเป็นระยะ

## 2. ทำไมต้องมี Scheduler

โจทย์บังคับให้ JobGenerator ไม่ข้ามไป ReadyQueue เอง ดังนั้น Generator ส่งเข้า arrivalQueue ก่อน แล้ว Scheduler เป็นตัวกลางจัด Job เข้า ReadyQueue

## 3. FCFS กับ Priority

FCFS = ใครพร้อมก่อนก็ได้ก่อน

Priority = ดูค่า priority ก่อน โดยเลข 1 สูงสุด

ถ้า priority เท่ากัน โค้ดใช้ sequence แล้วค่อย id เป็น tie-break จะได้ไม่ต้องอาศัยว่า Worker ตัวไหนแย่งคิวทันก่อน

## 4. ทำไม Worker หลายตัวใช้คิวเดียวกัน

เพราะต้องการให้ทุก Worker ดึงงานจากชุด Ready Queue เดียวกัน เวลาไม่มีงานก็ block ที่ `take()` แทนการวนลูปเช็กเอง

## 5. Semaphore คืออะไรในงานนี้

มองง่าย ๆ ว่าเป็นจำนวนสิทธิ์ใช้ resource พร้อมกัน

Printer มี 1 permit = มีได้ทีละ 1 Job

Database มี 2 permits = มีได้พร้อมกันสูงสุด 2 Job

ใช้ `fair=true` ตามโจทย์

## 6. `sleep()` กับ `acquire()` ไม่เหมือนกัน

`Thread.sleep(workMs)` คือจำลองเวลาที่ Job กำลังทำงาน

`Semaphore.acquire()` คือรอสิทธิ์ใช้ resource ที่มีจำนวนจำกัด

เพราะคนละเหตุผล เวลาสองส่วนนี้จึงเก็บแยกกัน

## 7. ทำไม release อยู่ใน finally

หลัง `acquire()` สำเร็จแล้วต้องคืน permit ไม่ว่าจะทำ resource work สำเร็จหรือเกิด exception/interrupt ระหว่างนั้น

ใน Worker เลยมีตัวแปร `acquired` ไว้เช็กก่อนว่าเคยได้ permit จริงหรือยัง ถ้ายังไม่เคยได้ จะไม่ release มั่ว ๆ

## 8. Interrupt ตอนรอ resource

ถ้าโดน interrupt ขณะ `acquire()` ยังไม่สำเร็จ จะออกจากการรอ และ `acquired` ยังเป็น false ดังนั้น finally จะไม่ release permit ที่ตัวเองไม่ได้ถือ

## 9. Shutdown

Generator ส่งสัญญาณ `END_OF_INPUT` หลังส่ง Job หมด -> Scheduler ปิด ReadyQueue -> Worker ยังทำงานที่เหลือจนคิวปิดและไม่มีงานค้าง -> Main join Thread หลัก ๆ แล้วหยุด Monitor

จุดสำคัญคือไม่ใช้ `System.exit()` เพื่อบังคับปิดโปรแกรม

## 10. Statistics

Waiting Time = start - actual arrival

Turnaround Time = completion - actual arrival

Resource Wait = เวลาที่รอ Semaphore

Throughput = จำนวน Job ที่เสร็จ / เวลารวมของ simulation

และเช็กต่อ Job ด้วย:

`TAT = WT + workMs + Resource Wait + resourceMs`

ถ้าค่า Check ต่างจาก TAT นิดหน่อย ให้คิดถึงความละเอียดของ wall-clock และการ wake-up ของ Thread ก่อน เพราะ `sleep()` ไม่ได้ตื่นตรงเป๊ะทุกครั้ง

## 11. จุดที่ควรเปิดโค้ดให้ดูถ้าอาจารย์ถาม

- `Main.java` -> การประกอบทุก component และการ start/join
- `Config.java` -> รับและตรวจ command-line arguments
- `JobGenerator.java` -> ปล่อย Job ตาม arrival time
- `Scheduler.java` -> ย้ายจาก arrivalQueue ไป ReadyQueue
- `ReadyQueue.java` -> FCFS/Priority และการ block ตอนคิวว่าง
- `Worker.java` -> ลำดับการทำงานของ Job และ resource
- `ResourceManager.java` -> Semaphore
- `Statistics.java` -> ค่าที่ใช้คำนวณผล
- `Monitor.java` -> snapshot ของระบบ

## 12. เรื่อง AI

โปรเจกต์นี้มีการใช้ AI ช่วยบางส่วนจริง และมี `AI_USAGE_RECORD.md` ระบุไว้แล้ว จุดที่ต้องทำเองตอน Demo คืออธิบายโค้ดและตอบว่าทำไมแต่ละส่วนถึงเขียนแบบนี้
