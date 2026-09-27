# Mini Job Scheduler & Shared Resource Manager

517-312 Operating Systems | Term Project

เอกสารนี้อธิบายโครงสร้างปัจจุบัน วิธีรัน และวิธีตรวจงานของโปรเจกต์
รายละเอียดข้อกำหนดทั้งหมดอยู่ในเอกสารโจทย์

---

## 0. เอกสารไหนใช้อ้างอิงเรื่องอะไร

| เรื่อง | ให้ยึดตาม |
|---|---|
| ข้อกำหนดของงาน เกณฑ์คะแนน การทดลอง ของที่ต้องส่ง | **เอกสารโจทย์** |
| โครงสร้างและวิธีรันโปรเจกต์ชุดนี้ | **README.md ฉบับนี้** |

เอกสารโจทย์อธิบายเกณฑ์การให้คะแนนและการทดลอง ส่วน README นี้อธิบายโค้ดที่อยู่ใน workspace ปัจจุบัน

---

## 1. ในชุดนี้มีอะไรบ้าง

```
os-term-project-starterbonus/
├── README.md              <- คู่มือโปรเจกต์
├── src/                   <- source Java 15 ไฟล์
├── tests/                 <- regression test และ README การทดสอบ
├── workloads/             <- workload CSV และคำอธิบาย
├── logs/                  <- log การรันตัวอย่างแบบ historical
├── build/                 <- ไฟล์ .class ที่สร้างจากการ compile (ลบแล้วสร้างใหม่ได้)
├── OS_Term_Project_Java_Thread_Job_Scheduler.pdf
└── OS_Scheduler_Experiment_Results.docx
```

### ไฟล์ใน `src/`

| ไฟล์ | ใช้ทำอะไร |
|---|---|
| `WorkloadLoader.java` | อ่าน CSV เดิม และ resource หลายชนิดในรูปแบบ `PRINTER+DATABASE` |
| `ProjectLogger.java` | บันทึก log, worker-pool scaling, requeue และ cancellation; ให้ **นาฬิกากลาง** ผ่าน `now()` |
| `Config.java` | รับ 5 arguments หลัก พร้อมตัวเลือก Aging, dynamic workers, virtual threads และ resource timeout |
| `ResourceType.java` | ชนิดทรัพยากร `NONE` / `PRINTER` / `DATABASE` และลำดับ acquisition คงที่ |
| `WorkloadFormatException.java` | ข้อผิดพลาดของไฟล์ CSV พร้อมหมายเลขบรรทัด |

ไฟล์หลักมี implementation แล้ว และรองรับ FCFS, Priority, Aging, MLFQ,
dynamic worker pool, virtual threads, resource timeout และ multi-resource acquisition
ตามที่ระบุใน workload README

**ห้ามเปลี่ยนชื่อคลาสและชื่อ method ที่ให้ไว้** เพราะวัน Demo ผู้สอนจะขอเปิดดูตามชื่อเหล่านี้

นอกเหนือจากนั้นทำได้เต็มที่ และคาดว่าจะต้องทำด้วย

- **เพิ่ม parameter ใน constructor ได้** และบางตัวจำเป็นต้องเพิ่ม เช่น
  `JobGenerator` กับ `Scheduler` ยังไม่มี parameter สำหรับช่องทางส่งงานระหว่างกัน
  เพราะเป็นสิ่งที่กลุ่มต้องออกแบบเอง
- **เพิ่ม method และฟิลด์ได้** ตามที่ออกแบบ
- **เพิ่มคลาสใหม่ได้** เช่น จะแยก ReadyQueue เป็นสองคลาสตามนโยบายก็ได้
  ขอแค่ `ReadyQueue` ยังอยู่และยังมี method ตามเดิม

---

## 2. ลองรันดูก่อน

```bash
cd src
javac *.java
java Main ../workloads/jobs_standard.csv priority 3 1 2
```

คำสั่งเดิมใช้ Priority ปกติและปิด Aging หากต้องการทดลองเปรียบเทียบ ให้เพิ่ม `aging`
ท้ายคำสั่งเพื่อเปิด หรือ `no-aging` เพื่อระบุว่าปิดอย่างชัดเจน สถานะจะแสดงใน `SYSTEM_START`
ตัวอย่าง: `java Main ../workloads/jobs_standard.csv priority 3 1 2 aging`

สำหรับทดสอบ Aging โดยเฉพาะ ใช้ `jobs_aging.csv` รันด้วย worker เดียวทั้งสองโหมด:

```bash
java Main ../workloads/jobs_aging.csv priority 1 1 1 no-aging
java Main ../workloads/jobs_aging.csv priority 1 1 1 aging
```

เปรียบเทียบลำดับ `JOB_STARTED` หลัง `BLOCK`: เมื่อปิด Aging งาน priority 1 ที่มาทีหลัง
ควรเริ่มก่อน `LOW`; เมื่อเปิด Aging งาน `LOW` ที่รอนานควรได้เริ่มก่อน

คะแนนที่ใช้จัดอันดับภายในเมื่อเปิด Aging อาจติดลบได้ โดยหักหนึ่งคะแนนต่อทุก 1 วินาทีที่รอ
ค่านี้ไม่แก้ `job.priority` ซึ่งยังเป็น priority เดิมจาก workload และยังใช้แสดงใน log

นโยบาย `mlfq` ใช้ 3 คิว โดยมี quantum 100, 200 และ 400 ms ตามลำดับ งานที่ใช้ quantum
หมดแล้วยังเหลืองานจะถูกลดระดับและส่งกลับท้ายคิว พร้อมเก็บ `remainingWorkMs` ไว้ทำต่อ
ดูตัวอย่างได้ด้วย `java Main ../workloads/jobs_mlfq.csv mlfq 1 1 1`
MLFQ เลือกงานใหม่เมื่อจบ quantum เท่านั้น; งานที่กำลังทำจะไม่ถูกขัดจังหวะทันทีเมื่อมีงาน
ระดับสูงกว่าเข้ามาระหว่าง quantum. `Waiting Time` ตามโจทย์วัดจาก arrival ถึงการเริ่มครั้งแรก;
`Total Queue Waiting Time` รวมช่วงรอหลัง requeue เพื่อวิเคราะห์ MLFQ เพิ่มเติม.
สำหรับ MLFQ ให้ตรวจสมการด้วย `Total Queue Waiting Time` แทน `Waiting Time`:
`Turnaround = Total Queue Waiting + workMs + Resource Wait + resourceMs`.

### การทดลองระบบขยาย

`dynamic` ปรับจำนวน Worker ตามความยาว ready queue โดยใช้จำนวน Worker ในคำสั่งเป็นค่าสูงสุด
และคง Worker อย่างน้อยหนึ่งตัวไว้. โหมด fixed จะรักษาจำนวนตาม config; หาก Worker ถูก interrupt
ระหว่างทำงาน จะ cancel งานปัจจุบันและ pool สร้าง Worker ทดแทนเพื่องานที่ยังอยู่ในคิว.
`virtual` ใช้ Java 21 Virtual Threads โดยสร้าง consumer
ตามจำนวน Worker ที่ระบุไว้เหมือนกับ Platform mode เพื่อเปรียบเทียบชนิด Thread โดยไม่เปลี่ยน
จำนวน consumer.

```bash
java Main ../workloads/jobs_dynamic.csv fcfs 4 1 1 dynamic
java Main ../workloads/jobs_printer.csv priority 3 1 2
java Main ../workloads/jobs_printer.csv priority 3 1 2 virtual
```

เพิ่ม `timeout=100` เพื่อยกเลิก Job เมื่อรอ permit ของ resource แต่ละชนิดเกิน 100 ms;
เมื่อ timeout จะคืน permit ที่ถือไว้บางส่วน, เขียน `JOB_CANCELLED` และนับงานเป็น terminal
เพื่อให้ระบบปิดได้ตามปกติ. ตรวจกรณี partial acquire ได้ด้วย
`java Main ../workloads/jobs_timeout.csv fcfs 2 1 1 timeout=100`.

Job ขอหลาย resource ได้โดยใช้ `+` ในช่อง resource เช่น `PRINTER+DATABASE`.
Loader จะ normalize ลำดับ acquisition เป็น PRINTER ก่อน DATABASE ไม่ว่าลำดับใน CSV เป็นอย่างไร
จึงป้องกัน circular wait; [jobs_deadlock.csv](workloads/jobs_deadlock.csv) เป็น workload ท้าทายนี้.

ถ้าทุกอย่างถูกต้อง จะเห็นสองบรรทัดนี้แล้วโปรแกรมจบ

```
[      20 ms] [main      ] SYSTEM_START       workload=... policy=priority workers=3 printer=1 database=2
[      22 ms] [main      ] SYSTEM             โหลดงานได้ 10 ชิ้น
```

สองบรรทัดนี้เป็นส่วนต้นของ output; หลังจากนั้นควรเห็นเหตุการณ์งานและ summary จนระบบปิดเอง

### ถ้าเห็นภาษาไทยเป็น `???`

เป็นเรื่องของ encoding ที่หน้าจอ ไม่ใช่บั๊กของโปรแกรม

- **Windows** — สั่ง `chcp 65001` ก่อน หรือรันด้วย
  `java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 Main ...`
- **macOS / Linux** — ปกติไม่มีปัญหา

### การเก็บ log ลงไฟล์

ไม่ต้องเขียนโค้ดเพิ่ม ใช้การ redirect ตอนรัน

```bash
java Main ../workloads/jobs_standard.csv priority 3 1 2 > ../logs/standard_priority_w3.log
```

---

## 3. ลำดับการตรวจระบบ

ให้ตรวจทีละส่วนและรันคำสั่งให้จบเองทุกครั้งก่อนสรุปผล

**ขั้นที่ 1 — ตรวจเส้นทางงานพื้นฐาน**
รัน `jobs_single.csv` และ `jobs_standard.csv` เพื่อตรวจการรับงาน การจัดคิว การทำงาน และการปิดระบบ

**ขั้นที่ 2 — ตรวจนโยบายคิว**
เปรียบเทียบ `fcfs`, `priority`, `aging` และ `mlfq` โดยตรวจลำดับ `JOB_STARTED`, การ requeue และค่า waiting time

**ขั้นที่ 3 — ตรวจ resource**
ใช้ `jobs_printer.csv`, `jobs_db.csv`, `jobs_deadlock.csv` และ `jobs_timeout.csv` ตรวจ permit,
ลำดับการ acquire, การคืน resource และการยกเลิกเมื่อ timeout

**ขั้นที่ 4 — ตรวจ worker pool และ metrics**
ใช้ `jobs_dynamic.csv` และโหมด `virtual` ตรวจจำนวน worker การจบงานครบ และสมการค่าวัดผลในข้อ 5

**ขั้นที่ 5 — ตรวจการปิดระบบ**
ทุกการรันต้องมี `SYSTEM_STOP`, จำนวนงาน terminal ครบ และ JVM ต้องปิดเองโดยไม่ต้องกด Ctrl+C

---

## 4. จุดที่ยากที่สุด อ่านก่อนเริ่มขั้นที่ 5

**Worker ที่กำลังรอคิวอยู่ ไม่มีทางรู้ได้เองว่าจะไม่มีงานเข้ามาอีกแล้ว**

นี่ไม่ใช่รายละเอียดปลีกย่อย แต่เป็นปัญหา concurrency ที่แท้จริง และเป็นจุดที่กลุ่มส่วนใหญ่
ใช้เวลาดีบักมากที่สุด กลุ่มต้องออกแบบวิธีบอกเอง

เทคนิคที่ไปหาอ่านต่อได้ เลือกอันใดอันหนึ่งหรือผสมกันก็ได้

- poison pill
- `CountDownLatch`
- ตัวนับงานค้างที่ป้องกันด้วย lock

**ห้ามใช้การเดาเวลา** เช่น `Thread.sleep(10000)` แล้วหวังว่างานจะเสร็จพอดี

อาการผิดสามข้อที่ต้องไม่เกิด

1. `main` จบแล้วแต่ JVM ไม่ปิด เพราะยังมี Thread ค้างอยู่
2. Worker หยุดก่อนที่งานชิ้นสุดท้ายจะทำเสร็จ
3. permit ค้างเพราะถูก interrupt ระหว่างถือ resource

---

## 5. เครื่องมือตรวจงานตัวเอง

### สมการตรวจสอบค่าวัดผล

```
Turnaround = Waiting + workMs + Resource Wait + resourceMs
```

ใช้ตรวจทีละงาน ถ้าไม่ลงตัวแสดงว่ามีค่าใดค่าหนึ่งวัดผิดหรือวัดคนละจังหวะ
เป็นวิธีที่เร็วที่สุดในการหาว่าพลาดตรงไหน

### ใช้นาฬิกาตัวเดียว

ใช้ `logger.now()` ในการวัดผลทั้งหมด **ห้ามเอา `System.currentTimeMillis()` มาปน**
เพราะคนละฐานเวลา ผลคือค่าที่รายงานจะตรวจย้อนกลับกับ log ไม่ได้
ซึ่งเป็นสิ่งที่ผู้สอนจะทำตอนตรวจ

### ชุดงานทดสอบ

| ไฟล์ | ใช้ตรวจอะไร |
|---|---|
| `jobs_single.csv` | งานชิ้นเดียว ระบบเริ่มและปิดได้ครบวงจรหรือไม่ |
| `jobs_same_priority.csv` | priority เท่ากันหมด กติกาตัดสินลำดับทำงานหรือไม่ |
| `jobs_printer.csv` | ทุกงานแย่ง PRINTER ตรวจว่า permit=1 มีได้ครั้งละ 1 งานจริง |
| `jobs_db.csv` | ตรวจว่า DATABASE permit=2 อนุญาตพร้อมกันได้ 2 งาน |
| `jobs_standard.csv` | ชุดหลักสำหรับการทดลองและตารางผล |
| `jobs_aging.csv` | เปรียบเทียบลำดับ Priority เมื่อปิดและเปิด Aging โดยให้ `LOW` รอระหว่างงาน `HIGH` ที่ทยอยเข้ามา |

**เคล็ดลับ:** รัน `jobs_db.csv` ด้วย `database=2` แล้วเทียบกับ `jobs_printer.csv` ด้วย `printer=2`
ตัวเลขที่ได้ควรตรงกัน เพราะสองไฟล์นี้โครงสร้างเหมือนกันต่างแค่ชนิดทรัพยากร
ถ้าไม่ตรงแสดงว่ามีบั๊ก

---

## 6. ตรวจก่อนส่ง

- [ ] `javac *.java` ผ่านโดยไม่มี warning ที่เกี่ยวกับ concurrency
- [ ] รันได้ครบทั้ง 7 แถวของตารางผลในเอกสารโจทย์
- [ ] ทุกครั้งที่รัน JVM ปิดเองโดยไม่ต้องกด Ctrl+C
- [ ] จำนวน `JOB_COMPLETED` ใน log เท่ากับจำนวนงานใน workload เสมอ
- [ ] `RESOURCE_ACQUIRED` กับ `RESOURCE_RELEASED` มีจำนวนเท่ากัน
- [ ] ไม่มีช่วงใดที่ PRINTER ถูกถือเกินจำนวน permit
- [ ] สมการตรวจสอบลงตัวทุกงาน
- [ ] ไม่มี `Thread.stop()` และไม่มีลูปที่วนเช็กเงื่อนไขซ้ำ ๆ โดยไม่พัก
- [ ] สมาชิกทุกคนอธิบายได้ว่าโค้ดทุกส่วนทำอะไร

---

## 7. ข้อควรรู้เรื่องการใช้ AI

ใช้ได้ตามที่เอกสารโจทย์ระบุ แต่คะแนน Demo 35 คะแนนเป็นคะแนนรายบุคคล
และวัดจากการอธิบายโค้ดของกลุ่มตัวเองกับการแก้โจทย์สดหน้างาน

โค้ดที่ generate มาแล้วอธิบายไม่ได้จึงไม่ช่วยอะไร ข้อแนะนำคือ
ถ้าใช้ AI ช่วย ให้ใช้ถามเพื่อทำความเข้าใจ แล้วเขียนเอง
มากกว่าให้มันเขียนแล้วค่อยมาไล่อ่านทีหลัง
