# Final Test Report - Deep Recheck

ตรวจสอบโปรเจกต์ Java ตาม Assignment: **Multi-threaded File Download Client/Server - Protocol, Concurrency และการเปรียบเทียบ Traditional I/O กับ NIO Native Transfer** โดยใช้ source code ใน package นี้เป็นฐาน แล้ว compile/run จริงหลายรอบ รวมทั้ง stress, protocol fuzz และ process-level tests

## 1. Requirement baseline

โจทย์กำหนดให้:

- Client และ Server ทำงานผ่าน TCP และ command line
- Server รองรับหลาย connection พร้อมกัน
- Client แบ่งไฟล์เป็น 10 ช่วง ใช้ 10 workers พร้อมกัน โดยแต่ละ worker มี connection, offset และ length ของตัวเอง และช่วงไม่ซ้อนกัน
- เขียนไฟล์ปลายทางด้วย part files + merge หรือ positional write เช่น `FileChannel.write(buffer, position)`
- Protocol ต้องรองรับอย่างน้อย `LIST`, `INFO`, `GET` และ `ERROR`
- Server ต้องอ่านเฉพาะช่วงที่ร้องขอ
- เปรียบเทียบ Traditional I/O กับ NIO/native-transfer
- Benchmark ต้องมี 1 worker และ 10 workers แต่ละกรณีอย่างน้อย 3 runs
- ตรวจ size/hash และอธิบายข้อจำกัดของ localhost เช่น page cache, loopback และ storage cache

## 2. Build / compiler check

Environment ที่ใช้ final recheck:

```text
Java: OpenJDK 21.0.11
Compiler: javac -Xlint:all -encoding UTF-8
```

ผล:

```text
Main sources: PASS
Test sources: PASS
Compiler warnings: 0
```

Compile ใหม่จาก source สดก่อน test ทุกชุด ไม่ใช้ class files เก่า

## 3. Integration / edge-case regression

`IntegrationTest` final version = **731 passed / 0 failed**

รันซ้ำ 5 รอบ:

```text
Run 1: 731/731 passed
Run 2: 731/731 passed
Run 3: 731/731 passed
Run 4: 731/731 passed
Run 5: 731/731 passed
```

รวม **3,655 checks passed / 0 failed** จาก regression suite 5 รอบ

ครอบคลุม:

- range splitting: file size 0..64 bytes x workers 1..10 ครบทุกคู่
- invalid file size / worker count / port / timeout / max connections
- CRLF และ line length limit
- `LIST`
- `INFO` ไฟล์ปกติ, filename มี space และ missing file
- `GET` range ปกติ, zero-length, range ท้ายไฟล์ และ range เกินขอบเขต
- malformed command และ invalid numeric field
- path traversal / slash / backslash / control character
- symbolic link filename
- empty file / 1-byte file
- workers 1, 2, 3, 10 ใน Traditional และ NIO
- output size / byte count / SHA-256
- concurrent clients
- failed download ต้องไม่ publish partial output ทับไฟล์เดิม
- server ต้องตอบ `ERROR BAD_REQUEST` เมื่อ request line ยาวเกิน limit

## 4. Deep process-level tests

ทดสอบ `ServerMain` + `ClientMain` เป็น process จริงแยกกัน ไม่ใช่เฉพาะการเรียก class ภายใน test JVM

### File-size boundaries

ทดสอบไฟล์ขนาด:

```text
0, 1, 2, 3, 9, 10, 11, 23, 31, 32, 33,
63, 64, 65, 99, 100, 101, 1023, 1024, 1025,
65535, 65536, 65537, 1048576, 1048577, 3145852, 8388745 bytes
```

ทั้ง Traditional และ NIO ใช้ 1 และ 10 workers

ผล:

```text
108 size/mode/worker cases: PASS
```

รวมถึง filename ที่มี space และ `LIST` แบบ process จริง

### Connection saturation

Server จำกัด handler threads = 1 แล้วเปิดหลาย clients พร้อมกัน

```text
6 simultaneous clients: PASS
```

### High concurrency

ทดสอบ 50 clients พร้อมกัน โดยแต่ละ client ใช้ 10 workers:

```text
Traditional: PASS
NIO:         PASS
```

ก่อน final benchmark-order patch ยังได้ทดสอบ 100 simultaneous clients x 10 workers แล้วผ่านทั้ง Traditional และ NIO เช่นกัน

### Port conflict

เปิด server ตัวแรกแล้วพยายาม bind server ตัวที่สองเข้าพอร์ตเดียวกัน:

```text
Second server failed as expected
First server remained usable
PASS
```

### Timeout / cleanup

ใช้ server จำลองที่ไม่ตอบกลับเพื่อบังคับ client timeout และตรวจว่าไฟล์เดิมยังไม่ถูกทับและไม่มี `.part` ค้าง:

```text
PASS
```

## 5. Protocol fuzz / malformed requests

ทดสอบกับ server process จริงทั้ง Traditional และ NIO:

```text
NOPE
INFO
GET
GET <missing arguments>
invalid numeric field
negative offset/length
number overflow
path traversal
extra tokens / ambiguous filename cases
request line > 64 KiB
CRLF request
```

ผล:

```text
Traditional: PASS
NIO:         PASS
```

กรณี number overflow ถูกจัดเป็น `ERROR BAD_REQUEST invalid number` ซึ่งเป็นการปฏิเสธ input ที่ parse ไม่ได้อย่างถูกต้อง

กรณี request line ยาวเกิน 64 KiB หลังแก้ไข server ตอบ:

```text
ERROR BAD_REQUEST request line too long
```

และ server ยังรับ request ใหม่ได้ตามปกติ

## 6. Standalone benchmark validation

ใช้ `BenchmarkMain` ต่อ server ที่เปิดเป็น process แยกจริง:

- Traditional: 3 runs x workers 1/10
- NIO: 3 runs x workers 1/10
- ตรวจ source file hash จริง
- ทดสอบ expected SHA-256 ตัวพิมพ์ใหญ่ด้วย

ผล:

```text
Traditional: 6/6 PASS
NIO:         6/6 PASS
Uppercase expected hash: PASS
```

## 7. Final benchmark

Final benchmark ใช้ `BenchmarkDriverMain` หลังปรับให้สลับลำดับ Traditional/NIO ในแต่ละ run เพื่อลด bias จาก page cache เช่น:

```text
Run 1: Traditional -> NIO
Run 2: NIO -> Traditional
Run 3: Traditional -> NIO
Run 4: NIO -> Traditional
Run 5: Traditional -> NIO
```

Configuration:

```text
File: server_files/sample.bin
Size: 8,388,745 bytes
Runs per case: 5
Workers: 1 and 10
Modes: Traditional and NIO
Measured cases: 20
Host: 127.0.0.1
```

ทุก result:

```text
SIZE_OK=true
HASH_OK=true
```

SHA-256:

```text
51e09bbafb4530a3a7a6e757171842a049a0ec587ab427656655d48347c55d9c
```

### Average results from the latest 5-run set

| Mode | Workers | Average MB/s | Min MB/s | Max MB/s | Average seconds |
|---|---:|---:|---:|---:|---:|
| Traditional | 1 | 512.053 | 248.206 | 671.070 | 0.018063 |
| Traditional | 10 | 425.492 | 344.153 | 487.376 | 0.019149 |
| NIO | 1 | 496.684 | 407.084 | 583.658 | 0.016332 |
| NIO | 10 | 452.572 | 372.312 | 499.501 | 0.017859 |

หมายเหตุ: ตัวเลขนี้เป็น measurement บน localhost ของ environment ที่ใช้ทดสอบในครั้งนี้ ไม่ใช่ค่าคงที่ของเครื่องหรือ network ทุกระบบ

## 8. Fixes made during this recheck

### Fix 1 - Overlong protocol request

ก่อนแก้ `LineReader` ที่เจอ line เกิน 64 KiB จะโยน exception แล้ว server ปิด connection โดยไม่ตอบ error protocol

แก้ให้ server ตอบ:

```text
ERROR BAD_REQUEST request line too long
```

### Fix 2 - Race robustness ของ LIST

ปรับให้ server snapshot ชื่อไฟล์และขนาดก่อนเริ่มส่ง response เพื่อไม่ให้ response ถูกเขียนออกไปบางส่วนแล้วค่อยเจอ I/O error กลางทางโดยไม่ตั้งใจ

### Fix 3 - Benchmark CSV locale

ใช้ `Locale.ROOT` ใน formatted numeric output เพื่อให้ decimal separator ใน CSV เป็น `.` แม้เครื่องจะใช้ locale ที่มี decimal separator ต่างจากนั้น

### Fix 4 - Benchmark hash comparison

`BenchmarkMain` ใช้ comparison แบบ case-insensitive สำหรับ SHA-256 เพราะ input hex สามารถเป็น uppercase ได้เช่นกัน

### Fix 5 - Client parser validation

ตรวจว่า LIST size และ INFO size ต้องเป็น non-negative และจัดการ `NumberFormatException` เป็น `IOException` ที่เหมาะสม

### Fix 6 - Benchmark order

ปรับลำดับ server mode ให้สลับ Traditional/NIO ในแต่ละ run เพื่อลดอคติจาก page cache ที่อาจเกิดจากการรัน mode หนึ่งครบทั้งหมดก่อนอีก mode

## 9. Final requirement mapping

| Requirement | Implementation / evidence |
|---|---|
| TCP Client/Server | `ServerSocketChannel`, `Socket/SocketChannel`, `ClientMain`, `ServerMain` |
| Multiple connections | fixed thread pool on server |
| 10 workers / 10 ranges | default client = 10 workers; `RangeSplitter` creates 10 ranges |
| Per-worker connection | each `downloadRange()` creates its own socket |
| Per-worker offset/length | `DownloadRange` passed to each worker |
| No overlap | sequential offset/length construction + exhaustive size tests |
| Positional output | `FileChannel.write(buffer, position)` |
| LIST | implemented and tested |
| INFO | implemented and tested before GET workers |
| GET | implemented with offset/length and exact payload length |
| ERROR | implemented for invalid/missing/range errors and malformed input |
| Traditional path | buffered read from `FileChannel` to socket `OutputStream` |
| NIO/native path | `FileChannel.transferTo()` |
| Size/hash check | output size checked; SHA-256 computed and compared in benchmark/test validation |
| Benchmark 1 worker | 5 runs |
| Benchmark 10 workers | 5 runs |
| Traditional vs NIO | same file/environment, 20 measured cases |
| Localhost limitation | documented in README/report |
| Compile/run commands | README + `.bat` + PowerShell scripts |

## 10. Final status

หลังการแก้ไขรอบนี้ source ถูก compile ใหม่และ regression suite ผ่าน **731/731 x 5 รอบ**, process/edge-case tests ผ่าน, protocol fuzz ผ่าน, concurrent-load tests ผ่าน และ benchmark 20 cases ผ่าน size/hash ทั้งหมด

ชุด source ใน package ไม่มี `bin/` หรือ output build เก่าที่ต้องพึ่งพาในการ compile ใหม่
