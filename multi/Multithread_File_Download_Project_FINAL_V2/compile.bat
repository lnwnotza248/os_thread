@echo off
cd /d "%~dp0"
setlocal
if exist bin rmdir /s /q bin
mkdir bin
javac -Xlint:all -encoding UTF-8 -d bin src\main\java\th\ac\example\download\*.java
if errorlevel 1 exit /b 1
javac -Xlint:all -encoding UTF-8 -cp bin -d bin src\test\java\th\ac\example\download\IntegrationTest.java
if errorlevel 1 exit /b 1
echo Compile OK
