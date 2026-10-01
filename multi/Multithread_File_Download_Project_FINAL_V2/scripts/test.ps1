$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot
& "$PSScriptRoot\compile.ps1"
java -cp bin th.ac.example.download.IntegrationTest
