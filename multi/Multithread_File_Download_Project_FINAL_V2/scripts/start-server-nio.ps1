$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot
java -cp bin th.ac.example.download.ServerMain --port 5000 --dir server_files --mode nio
