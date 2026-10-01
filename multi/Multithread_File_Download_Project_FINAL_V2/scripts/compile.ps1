$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot
Remove-Item -Recurse -Force bin -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force bin | Out-Null
javac -Xlint:all -encoding UTF-8 -d bin src\main\java\th\ac\example\download\*.java
javac -Xlint:all -encoding UTF-8 -cp bin -d bin src\test\java\th\ac\example\download\IntegrationTest.java
Write-Host 'Compile OK'
