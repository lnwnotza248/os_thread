# Multi-threaded File Download Client/Server

โปรเจกต์นี้ทำตาม requirement จาก Assignment: **Multi-threaded File Download Client/Server - Protocol, Concurrency และการเปรียบเทียบ Traditional I/O กับ NIO Native Transfer**

## Requirement ที่รองรับ

- TCP Client/Server และ command-line
- Server รองรับหลาย connection พร้อมกันด้วย fixed thread pool
- Client แบ่งไฟล์เป็น **10 ช่วงเป็นค่าใช้งานปกติ** และค่าเริ่มต้นใช้ **10 workers**; worker แต่ละตัวเปิด TCP connection ของตัวเอง และมี `offset` / `length` ของตัวเอง โดยช่วงไม่ซ้อนกัน
- สำหรับ benchmark รองรับทั้ง **1 worker และ 10 workers** ตาม requirement
- Server รองรับ `LIST`, `INFO <filename>`, `GET <filename> <offset> <length>` และ `ERROR <code> <message>`
- เขียนไฟล์ปลายทางตาม offset ด้วย `FileChannel.write(buffer, position)` และแต่ละ worker เปิด `FileChannel` ของตัวเอง
- Server มี 2 data path: `TRADITIONAL` (buffered I/O) และ `NIO` (`FileChannel.transferTo`)
- ตรวจ output ด้วย size และ SHA-256
- ดาวน์โหลดใช้ temporary `.part` file แล้วค่อย replace final file เมื่อ size/hash ผ่าน เพื่อไม่ publish ไฟล์ที่ดาวน์โหลดไม่ครบ
- ป้องกัน path separator, control character และ symbolic link ใน server file root
- Socket มี read timeout เพื่อไม่ให้ worker/client ค้างรอ indefinitely
- มี benchmark สำหรับ 1 worker และ 10 workers และบังคับอย่างน้อย 3 runs ต่อ case
- มี automated integration/edge-case tests และ protocol fuzz tests

## โครงสร้าง

```text
src/main/java/th/ac/example/download/
  Protocol.java
  FileUtil.java
  DownloadMode.java
  DownloadRange.java
  DownloadResult.java
  RangeSplitter.java
  FileDownloadServer.java
  ServerMain.java
  FileDownloadClient.java
  ClientMain.java
  BenchmarkMain.java
  BenchmarkDriverMain.java

src/test/java/th/ac/example/download/
  IntegrationTest.java

server_files/
  hello.txt
  sample.bin

README.md
TEST_REPORT.md
benchmark_results.csv
scripts/
*.bat
```

## Requirements ที่ตรงกับโจทย์

โจทย์กำหนดให้ client แบ่งไฟล์เป็น 10 ช่วงและใช้ 10 workers โดยแต่ละ worker มี connection, offset และ length ของตัวเอง และช่วงข้อมูลต้องไม่ซ้อนกัน ส่วน benchmark ต้องมี 1 worker และ 10 workers อย่างน้อย 3 ครั้งต่อกรณี โปรเจกต์นี้จึงตั้ง default เป็น 10 workers แต่ยังเปิด 1 worker ได้เพื่อการทดลองตามโจทย์

## Compile (Windows PowerShell)

```powershell
Remove-Item -Recurse -Force bin -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force bin | Out-Null
javac -Xlint:all -encoding UTF-8 -d bin src\main\java\th\ac\example\download\*.java
javac -Xlint:all -encoding UTF-8 -cp bin -d bin src\test\java\th\ac\example\download\IntegrationTest.java
```

หรือ double-click `compile.bat`

## Compile (Linux/macOS)

```bash
rm -rf bin && mkdir -p bin
javac -Xlint:all -encoding UTF-8 -d bin src/main/java/th/ac/example/download/*.java
javac -Xlint:all -encoding UTF-8 -cp bin -d bin src/test/java/th/ac/example/download/IntegrationTest.java
```

## Start Server

Traditional I/O:

```powershell
java -cp bin th.ac.example.download.ServerMain --port 5000 --dir server_files --mode traditional
```

NIO/native transfer:

```powershell
java -cp bin th.ac.example.download.ServerMain --port 5000 --dir server_files --mode nio
```

มีไฟล์ `start-server-traditional.bat` และ `start-server-nio.bat` ให้ใช้บน Windows ด้วย

## Client

ดูรายชื่อไฟล์:

```powershell
java -cp bin th.ac.example.download.ClientMain --host 127.0.0.1 --port 5000 --list
```

ดาวน์โหลดปกติด้วย 10 workers:

```powershell
java -cp bin th.ac.example.download.ClientMain --host 127.0.0.1 --port 5000 --file sample.bin --output output\downloaded.bin --workers 10
```

ทดลอง 1 worker:

```powershell
java -cp bin th.ac.example.download.ClientMain --host 127.0.0.1 --port 5000 --file sample.bin --output output\downloaded-w1.bin --workers 1
```

`--mode-label traditional|nio` เป็นเพียง label สำหรับข้อความผลลัพธ์ของ client ไม่ได้เป็นตัวสั่ง server ให้เปลี่ยน mode; mode จริงถูกเลือกตอน start server

## Automated integration test

```powershell
java -cp bin th.ac.example.download.IntegrationTest
```

ชุด test ครอบคลุม:

- range splitting ขนาดไฟล์ 0..64 bytes กับ worker 1..10 ครบทุกคู่
- invalid range/worker arguments
- LIST / INFO / GET / ERROR
- filename ที่มี space
- empty file และ 1-byte file
- range 0 bytes, range ท้ายไฟล์ และ range เกินขอบเขต
- missing file, path traversal, control characters และ symbolic link
- malformed protocol command และ line ที่ยาวเกิน limit
- download ด้วย 1, 2, 3 และ 10 workers ใน Traditional และ NIO
- size / byte count / SHA-256
- concurrent clients หลายตัวพร้อมกัน
- failure case ที่ worker ได้ข้อมูลไม่ครบและต้องไม่ publish ไฟล์ปลายทางเก่าทับด้วยไฟล์เสีย

## Benchmark ตามโจทย์

วิธีหลักที่แนะนำคือ `BenchmarkDriverMain` เพราะโปรแกรมตัวเดียวเป็นคนสลับ server ระหว่าง Traditional และ NIO ทำให้ลดความผิดพลาดเรื่องการติด label ผิด mode

```powershell
java -cp bin th.ac.example.download.BenchmarkDriverMain --dir server_files --file sample.bin --runs 3 --output-dir output\benchmark
```

ตัวอย่างด้านบนเป็นขั้นต่ำตามโจทย์ สามารถใช้ 5 runs แบบที่ใช้ใน final validation ได้ โดย benchmark สลับลำดับ Traditional/NIO ในแต่ละรอบเพื่อลด bias จาก page cache:

```powershell
java -cp bin th.ac.example.download.BenchmarkDriverMain --dir server_files --file sample.bin --runs 5 --output-dir output\benchmark
```

CSV มีคอลัมน์:

```text
MODE,WORKERS,RUN,BYTES,SECONDS,MBPS,SHA256,SIZE_OK,HASH_OK
```

## BenchmarkMain แบบแยก server process

ถ้าต้องการ benchmark กับ server ที่เปิดเองก่อน `BenchmarkMain` จะบังคับให้มี hash ต้นทางจริง เพื่อไม่ให้เอา hash จากผลรันแรกมาใช้เป็น baseline โดยไม่รู้ว่าข้อมูลถูกต้องหรือไม่

ตัวอย่าง:

```powershell
java -cp bin th.ac.example.download.BenchmarkMain --host 127.0.0.1 --port 5000 --file sample.bin --mode traditional --runs 3 --source-file server_files\sample.bin
```

หรือใช้ `--expected-sha256 <64-hex>` แทน `--source-file`

## Protocol

### LIST
Request:

```text
LIST
```

Response:

```text
OK LIST <count>
FILE <name> <size>
...
END
```

### INFO
Request:

```text
INFO <filename>
```

Response:

```text
OK SIZE <bytes>
```

หรือ:

```text
ERROR <code> <message>
```

### GET
Request:

```text
GET <filename> <offset> <length>
```

Response header:

```text
OK DATA <length>
```

แล้วตามด้วย binary payload ตามจำนวน byte ที่ตกลงกันไว้ เมื่อส่งครบแล้ว worker connection จะจบ

ตัวอย่าง error:

```text
ERROR NOT_FOUND file not found
ERROR BAD_REQUEST invalid command
ERROR RANGE_INVALID offset/length outside file
```

## Range splitting

ถ้า file size = `N` และ workers = `W`:

```text
base = N / W
remainder = N % W
```

worker 0 ถึง W-2 ได้ `base` bytes และ worker สุดท้ายได้ `base + remainder` bytes

ตัวอย่าง `N = 23, W = 10`:

```text
offset 0  length 2
offset 2  length 2
offset 4  length 2
...
offset 16 length 2
offset 18 length 5
```

ทำให้ทุกช่วงต่อกันพอดี ไม่มี overlap และรวมกันได้ `N` bytes

## Localhost benchmark notes

โจทย์กำหนดให้ใช้ไฟล์เดียวกันและ environment เดียวกัน เปรียบเทียบเวลาและ throughput พร้อมตรวจ size/hash และอธิบายข้อจำกัดของ localhost

การทดลองนี้วัดบน `127.0.0.1` ดังนั้น page cache, loopback network, CPU scheduling, filesystem และ storage cache สามารถทำให้แต่ละ run ต่างกันได้ ตัวเลข MB/s ในโปรเจกต์นี้จึงควรอธิบายว่าเป็นผลจาก environment ที่ใช้ทดสอบ ไม่ใช่ throughput ของ network จริงโดยทั่วไป

10 workers ไม่ได้หมายความว่าจะเร็วขึ้น 10 เท่า เพราะ workers ใช้ bandwidth, server thread pool, CPU และ storage ร่วมกัน และมี overhead ของ connection/scheduling/positional writes

NIO `transferTo` ใช้ native-transfer API ตามโจทย์ แต่ผลจริงไม่ได้รับประกันว่าจะเร็วกว่า buffered I/O ทุกครั้ง เพราะขึ้นกับ OS, filesystem, socket/loopback และ cache
