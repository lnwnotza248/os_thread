# Final Check

ไฟล์นี้ใช้เช็กงานก่อนส่งแบบเร็ว ๆ

## ของที่ต้องมี

- [x] โหลด Job จาก external CSV
- [x] รับ workload / policy / workers / printer permits / database permits จาก command line
- [x] FCFS
- [x] Priority และ tie-break ที่กำหนดจากข้อมูล Job
- [x] Job state: ARRIVED / READY / RUNNING / WAITING_RESOURCE / COMPLETED
- [x] Worker หลายตัวใช้ ReadyQueue เดียวกัน
- [x] ResourceManager ตัวเดียวใช้ร่วมกัน
- [x] Semaphore แบบ fair
- [x] ไม่มี busy waiting ใน ReadyQueue
- [x] เก็บ Waiting / Turnaround / Resource Wait / Throughput
- [x] Monitor อ่านค่าระหว่างที่หลาย Thread ทำงานได้อย่างปลอดภัย
- [x] Shutdown ปกติไม่ใช้ `System.exit()`
- [x] release resource อย่างปลอดภัยหลัง acquire สำเร็จ
- [x] มี workload ทั้ง 5 ชุด
- [x] มี raw log ของการทดลอง 7 แถว
- [x] มี results และ AI usage record
- [x] package ไม่มี `.class` / cache / build

## ที่ลองรัน

- `jobs_single.csv` -> 1/1 และโปรแกรมจบเอง
- `jobs_standard.csv priority 3 1 2` -> 10/10 และโปรแกรมจบเอง
- การทดลอง 7 กรณีเก็บ raw log ไว้ใน `logs/`

ตัวเลขเวลาแต่ละรอบอาจต่างกันเล็กน้อย เพราะเป็นโปรแกรมหลาย Thread และ `Thread.sleep()` ขึ้นกับการจัดตารางของ OS

## Flow ที่ควรจำ

`JobGenerator -> arrivalQueue -> Scheduler -> ReadyQueue -> Worker[] -> ResourceManager -> Statistics`
