@echo off
cd /d "%~dp0"
java -cp bin th.ac.example.download.ServerMain --port 5000 --dir server_files --mode traditional
