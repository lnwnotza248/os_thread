# Bonus / Extension

โหมดหลักของงานยังใช้ `java Main ...` เหมือนเดิม ส่วน Bonus แยกไว้ใน `BonusMain` เพื่อไม่ให้ไปเปลี่ยน flow หลักที่อาจารย์ตรวจ

## 1. Aging
ลด effective priority ของงานที่รอนาน

```bash
cd src
javac *.java
java BonusMain aging ../workloads/jobs_standard.csv 3 1 2 1000
```

รูปแบบ: `agingIntervalMs` คือทุกกี่ ms ที่งานรอนานขึ้น 1 ระดับ

## 2. MLFQ
มี 3 queue, time quantum และ requeue พร้อม `remainingWorkMs`

```bash
java BonusMain mlfq ../workloads/jobs_standard.csv 3 1 2 300 600 1200
```

## 3. Dynamic Worker
เพิ่ม/ลด Worker ตามความยาว Ready Queue

```bash
java BonusMain dynamic ../workloads/jobs_standard.csv 1 5 1 2 2
```

`1 5` = min/max worker และ `2` = threshold ที่ใช้ scale up

## 4. Resource Timeout / Cancellation
ถ้ารอ resource เกินเวลาที่กำหนด Job จะถูกยกเลิก

```bash
java BonusMain timeout ../workloads/jobs_printer.csv 3 1 2 200
```

## 5. Virtual Thread Comparison
รัน workload เดียวกันด้วย platform thread pool และ virtual threads แล้วพิมพ์เวลาเทียบกัน

```bash
java BonusMain virtual ../workloads/jobs_printer.csv 3 1 2
```

ต้องใช้ Java 21+

## 6. Deadlock Challenge
Job ขอสอง resource และ `DeadlockSafeResourceManager` บังคับลำดับการ acquire เดียวกัน เพื่อลด circular wait

```bash
java BonusMain deadlock
```

## 7. ทดสอบที่เช็กไว้

- Aging กับ `jobs_standard.csv` จบครบ 10/10
- MLFQ กับ `jobs_standard.csv` จบครบ 10/10 และทดสอบ `jobs_printer.csv` 6/6
- Dynamic Worker กับ `jobs_standard.csv` จบครบ 10/10
- Timeout กับ `jobs_printer.csv` จบครบ โดยตัวอย่าง timeout 200ms มีทั้งงานเสร็จและงานถูกยกเลิก
- Virtual Thread comparison รันได้บน Java 21
- Deadlock test จบครบ 2/2 และทั้งสองงานใช้ลำดับ acquire เดียวกัน
