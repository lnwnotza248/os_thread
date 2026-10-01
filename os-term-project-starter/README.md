# Mini Job Scheduler & Shared Resource Manager

## วิธีรัน

เปิด Terminal ที่โฟลเดอร์ `src` ก่อน แล้ว compile:

```bash
javac *.java
```

จากนั้นรันตามรูปแบบนี้:

```bash
java Main ../workloads/<workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>
```

ตัวอย่างที่ใช้ทดสอบ:

```bash
java Main ../workloads/jobs_standard.csv priority 3 1 2
```

ความหมายของค่าท้ายคำสั่งคือ workload, policy, จำนวน Worker, จำนวน Printer permit และจำนวน Database permit ตามลำดับ

## โปรแกรมทำงานยังไง

ผมมอง flow หลักของโปรแกรมเป็นแบบนี้:

`CSV -> JobGenerator -> arrivalQueue -> Scheduler -> ReadyQueue -> Worker -> ResourceManager -> Statistics`

- `WorkloadLoader` อ่าน Job จาก CSV ที่ส่งเข้ามาทาง command line
- `JobGenerator` ปล่อย Job ตาม `arrivalMs`
- `Scheduler` รับจาก arrival queue แล้วเอาเข้า ReadyQueue
- `ReadyQueue` เลือก Job ตาม FCFS หรือ Priority
- Worker หลายตัวใช้ ReadyQueue เดียวกัน
- ถ้า Job ต้องใช้ Printer/Database จะผ่าน `ResourceManager` และ Semaphore
- `Statistics` เก็บเวลาที่ใช้วัดผล และ `Monitor` คอยดูสถานะรวม

## Scheduling

มี 2 แบบตามโจทย์:

- FCFS: งานที่เข้าคิวพร้อมทำก่อนถูกเลือกก่อน
- Priority: เลข priority น้อยกว่าถือว่าสำคัญกว่า โดย `1` สูงสุด
- ถ้า priority เท่ากัน ใช้ sequence แล้วค่อย id เป็นตัวตัดสิน เพื่อให้กติกาไม่ขึ้นกับจังหวะของ Thread

## Resource

ค่า resource ที่ใช้ใน workload คือ `NONE`, `PRINTER` และ `DATABASE`

- Printer default 1 permit
- Database default 2 permits
- Semaphore ของทั้งสองตัวสร้างแบบ fair

ถ้า permit เต็ม Worker จะรอที่ `acquire()` ไม่ได้วนลูปเช็กเอง และหลังจาก acquire สำเร็จแล้วจะคืน permit ใน `finally`

## Metrics

ค่าหลักที่โปรแกรมสรุปให้คือ Waiting Time, Turnaround Time, Resource Wait Time และ Throughput

สำหรับเช็กแต่ละ Job ใช้แนวคิด:

`TAT = WT + workMs + Resource Wait + resourceMs`

ค่าที่วัดจริงอาจต่างกันเล็กน้อยจากเวลาที่คำนวณด้วย `sleep()` เพราะ Thread ถูกจัดตารางโดย OS

## Workload

ไฟล์ใน `workloads/` เป็นชุด workload ที่ใช้กับโปรเจกต์นี้ โปรแกรมไม่ได้เขียน Job ตัวอย่างไว้ใน `Main` แต่โหลดจาก CSV ตอนรัน

## ไฟล์ที่เกี่ยวข้อง

- `DEMO_GUIDE.md` เป็นโน้ตสั้น ๆ สำหรับทบทวนก่อน Demo
- `EXPERIMENT_RESULTS.md` เป็นผลการทดลอง 7 กรณีและคำตอบวิเคราะห์
- `FINAL_CHECK.md` เป็น checklist ที่ใช้เช็กงานก่อนส่ง
- `AI_USAGE_RECORD.md` บันทึกส่วนที่ใช้ AI ช่วยในโปรเจกต์
- `logs/` เก็บ raw log ของการทดลอง

ก่อนส่งควรไม่มี `.class` หรือไฟล์ cache/build ติดไปด้วย
