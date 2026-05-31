$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj = Join-Path $root "CodePad.WinUI\CodePad.WinUI.csproj"

Write-Host "CodePad WinUI — Mica + Notas" -ForegroundColor Cyan

$msbuild = $null
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (Test-Path $vswhere) {
    $msbuild = & $vswhere -latest -requires Microsoft.Component.MSBuild -find "MSBuild\**\Bin\MSBuild.exe" | Select-Object -First 1
}

if (-not $msbuild -or -not (Test-Path $msbuild)) {
    Write-Host "MSBuild do Visual Studio nao encontrado." -ForegroundColor Yellow
    Write-Host "Instale: Visual Studio 2022+ com 'Desenvolvimento para plataforma Windows' e Windows App SDK." -ForegroundColor Yellow
    exit 1
}

Write-Host "MSBuild: $msbuild" -ForegroundColor DarkGray
& $msbuild $proj /restore /p:Configuration=Release /p:Platform=x64

$exe = Join-Path $root "CodePad.WinUI\bin\x64\Release\net8.0-windows10.0.19041.0\CodePad.WinUI.exe"
if (Test-Path $exe) {
    $dest = Join-Path $root "..\dist\CodePad-WinUI.exe"
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    Copy-Item -Force $exe $dest
    Write-Host "Executavel: $exe" -ForegroundColor Green
    Write-Host "Copia: $dest" -ForegroundColor Green
    Write-Host "Executar: & '$exe'" -ForegroundColor Green
} else {
    Write-Host "Build OK; procure CodePad.WinUI.exe em bin\x64\Release\..." -ForegroundColor Yellow
}
