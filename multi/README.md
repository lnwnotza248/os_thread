# TCP File Transfer

โปรเจกต์ Java นี้ประกอบด้วย Server สำหรับแชร์ไฟล์, Client สำหรับดูและดาวน์โหลดไฟล์แบบแบ่งช่วง, และเครื่องมือสร้างไฟล์/วัดผลการดาวน์โหลด เปรียบเทียบการส่งแบบ `traditional` กับ `zerocopy`

## สิ่งที่ต้องมี

- JDK 17 ขึ้นไป
- PowerShell หรือ terminal ที่ใช้คำสั่ง `java` และ `javac` ได้

## คอมไพล์

เปิด terminal ที่โฟลเดอร์หลักของโปรเจกต์ แล้วรัน:

```powershell
cd 'C:\Users\not24\OneDrive\Desktop\multi'
javac -d out src\*.java
```

## เริ่มใช้งาน

### 1. เตรียมไฟล์ที่แชร์

นำไฟล์ที่ต้องการให้ดาวน์โหลดไปไว้ใน `shared_files` หรือสร้างไฟล์ตัวอย่างสำหรับทดสอบ:

```powershell
java -cp out GenerateTestFile shared_files\sample.bin 52428800
```

ตัวอย่างนี้สร้างไฟล์ขนาด 50 MiB ชื่อ `sample.bin`

### 2. เปิด Server

เปิด terminal หนึ่งหน้าต่างแล้วรัน:

```powershell
java -cp out FileServer
```

ค่าเริ่มต้นคือ port `9000` และโฟลเดอร์ `shared_files` โดยอ้างอิงจากโฟลเดอร์ที่ใช้รันคำสั่ง หากต้องการกำหนดเอง:

```powershell
java -cp out FileServer 9000 shared_files
```

### 3. ใช้ Client

เปิด terminal อีกหน้าต่าง:

```powershell
# ดูรายการไฟล์ใน Server
java -cp out FileClient localhost 9000 list

# ดูขนาดไฟล์
java -cp out FileClient localhost 9000 info sample.bin

# ตรวจ SHA-256 ของไฟล์บน Server
java -cp out FileClient localhost 9000 hash sample.bin

# ดาวน์โหลดด้วย 4 workers และโหมด traditional
java -cp out FileClient localhost 9000 download sample.bin downloads\sample.bin 4 traditional

# ดาวน์โหลดด้วย 4 workers และโหมด zerocopy
java -cp out FileClient localhost 9000 download sample.bin downloads\sample-zerocopy.bin 4 zerocopy
```

Client แบ่งไฟล์เป็น byte ranges ที่ไม่ซ้อนกัน ให้แต่ละ worker ดาวน์โหลดผ่าน connection ของตัวเอง จากนั้นเขียนแต่ละช่วงลงตำแหน่งที่ถูกต้องในไฟล์ปลายทาง พร้อมรายงานเวลา ความเร็ว และตรวจ SHA-256 ของไฟล์ที่ดาวน์โหลดเทียบกับต้นฉบับบน Server

## รัน benchmark

หลังเปิด Server และมีไฟล์ให้ดาวน์โหลดแล้ว ให้รัน:

```powershell
java -cp out Benchmark localhost 9000 sample.bin benchmark-output [keep|delete]
```

Benchmark ทดสอบทั้งสองโหมด (`traditional`, `zerocopy`) และจำนวน worker 1 กับ 10 โดยทำซ้ำชุดละ 3 ครั้ง จะแสดงผล SHA-256 ว่าตรงกัน (`MATCH`) หรือไม่ (`MISMATCH`) และหยุดพร้อมแจ้งข้อผิดพลาดเมื่อ hash ไม่ตรง เมื่อรันครบ Benchmark จะถามว่าต้องการลบไฟล์ทดสอบที่ดาวน์โหลดไว้หรือไม่ (`[Y/n]` ค่าเริ่มต้นคือลบ) หรือใส่ `keep`/`delete` เป็นอาร์กิวเมนต์สุดท้ายเพื่อข้ามคำถาม หากเกิดข้อผิดพลาดจะลบไฟล์ชั่วคราวเสมอ ส่วนไฟล์ต้นฉบับใน `shared_files` จะไม่ถูกลบ เพื่อให้ใช้รันทดสอบซ้ำได้

## คำสั่งที่ Server รองรับ

- `LIST` — ส่งรายชื่อและขนาดของไฟล์ปกติในโฟลเดอร์แชร์
- `INFO <filename>` — ส่งขนาดไฟล์
- `HASH <filename>` — ส่งค่า SHA-256 ของไฟล์
- `GET <filename> <offset> <length> [mode]` — ส่งข้อมูลตามช่วง byte ที่ระบุ โดย `mode` (ไม่บังคับ ค่าเริ่มต้น `traditional`) เป็น `traditional` หรือ `zerocopy` ค่าอื่นจะได้ ERROR 400

Server จำกัด path ที่ Client ขอให้อยู่ภายในโฟลเดอร์แชร์ และจะส่ง error response เมื่อคำสั่งหรือช่วง byte ไม่ถูกต้อง
