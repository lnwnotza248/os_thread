@echo off
cd /d "%~dp0"
setlocal
call compile.bat
if errorlevel 1 exit /b 1
java -cp bin th.ac.example.download.BenchmarkDriverMain --dir server_files --file sample.bin --runs 3 --output-dir output\benchmark
