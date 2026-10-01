# Bonus Test Results

ตรวจจาก source ชุดนี้ด้วย Java 21 และ workload ที่แจก

## ที่ทดสอบ

| Bonus | Test | ผล |
|---|---|---|
| Aging | standard, 3 workers, aging 1000ms | 10/10 |
| MLFQ | standard, 3 workers, quantum 300/600/1200 | 10/10 |
| MLFQ | printer, 3 workers, quantum 100/200/400 | 6/6 |
| Dynamic Worker | standard, min=1 max=5 threshold=2 | 10/10 |
| Timeout | printer, 3 workers, printer=1, timeout=200ms | 2 completed, 4 cancelled |
| Virtual Thread | printer, platform=3 | 6/6 |
| Deadlock prevention | 2 jobs, printer=1, database=1 | 2/2 |

## Edge cases

- BonusMain ปฏิเสธ workers/platformWorkers ที่น้อยกว่า 1 เพื่อไม่ให้ระบบรอค้าง
- MLFQ quantum ต้องมากกว่า 0
- Dynamic Worker ตรวจ min/max worker และ threshold
- Timeout ตรวจ timeoutMs ไม่ติดลบ
- Deadlock resource manager ตรวจ permit และ request ที่ผิดรูปแบบ

หมายเหตุ: เวลารันจริงอาจต่างกันเล็กน้อยตามการจัดตาราง Thread ของระบบปฏิบัติการ
