# Multi-threaded File Transfer

โปรเจกต์ตัวอย่างสำหรับรับส่งไฟล์ผ่าน TCP โดย Client ดาวน์โหลดไฟล์แบบแบ่งช่วงและทำงานพร้อมกันหลาย worker รองรับโหมด `traditional` และ `zerocopy` พร้อมตรวจสอบ SHA-256

## สิ่งที่ต้องมี

- JDK 17 ขึ้นไป
- เปิดเทอร์มินัลที่โฟลเดอร์หลักของโปรเจกต์ ซึ่งมีโฟลเดอร์ `src` และ `shared_files`

ตรวจสอบ Java ที่ติดตั้ง:

```powershell
java -version
javac -version
```

## คอมไพล์

รันจากโฟลเดอร์หลักของโปรเจกต์:

```powershell
javac -d out src\*.java
```

## เริ่ม Server

เปิดเทอร์มินัลแรกและรัน:

```powershell
java -cp out FileServer
```

ค่าเริ่มต้นคือพอร์ต `9000` และแชร์ไฟล์ในโฟลเดอร์ `shared_files` ให้เปิดเทอร์มินัลนี้ค้างไว้ระหว่างใช้งาน หากต้องการกำหนดพอร์ตและโฟลเดอร์เอง:

```powershell
java -cp out FileServer 9000 shared_files
```

## ใช้ Client

เปิดเทอร์มินัลที่สองจากโฟลเดอร์หลักของโปรเจกต์

แสดงรายชื่อไฟล์บน Server:

```powershell
java -cp out FileClient localhost 9000 list
```

ดูขนาดและ SHA-256 ของไฟล์:

```powershell
java -cp out FileClient localhost 9000 info testfile.bin
```

ดาวน์โหลดด้วย 4 workers ในโหมด traditional:

```powershell
java -cp out FileClient localhost 9000 download testfile.bin downloads\testfile.bin 4 traditional
```

ดาวน์โหลดด้วย 4 workers ในโหมด zerocopy:

```powershell
java -cp out FileClient localhost 9000 download testfile.bin downloads\testfile-zerocopy.bin 4 zerocopy
```

Client จะแสดงเวลา ความเร็ว และผลตรวจสอบ SHA-256 เมื่อดาวน์โหลดเสร็จ

รูปแบบคำสั่งดาวน์โหลด:

```text
java -cp out FileClient <host> <port> download <remoteFilename> <destinationPath> <workers> <traditional|zerocopy>
```

## รัน Benchmark

เมื่อ Server กำลังทำงาน ให้รันจากเทอร์มินัลที่สอง:

```powershell
java -cp out Benchmark localhost 9000 testfile.bin benchmark-output
```

Benchmark จะทดสอบทั้งสองโหมด ใช้ 1 และ 10 workers และทำซ้ำกรณีละ 3 รอบ ผลลัพธ์จะแสดงเวลา ความเร็ว และ SHA-256

หากไม่มีไฟล์ทดสอบใน `shared_files` สามารถสร้างไฟล์ขนาด 50 MiB ได้ด้วย:

```powershell
java -cp out GenerateTestFile shared_files\testfile.bin 52428800
```

## หยุด Server

กลับไปที่เทอร์มินัลของ Server แล้วกด `Ctrl+C`