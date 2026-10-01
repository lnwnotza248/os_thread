param(
  [string]$File = 'sample.bin',
  [int]$Runs = 3
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot
& "$PSScriptRoot\compile.ps1"
New-Item -ItemType Directory -Force 'output\benchmark' | Out-Null
java -cp bin th.ac.example.download.BenchmarkDriverMain --dir server_files --file $File --runs $Runs --output-dir output\benchmark
