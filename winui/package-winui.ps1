$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj = Join-Path $root "CodePad.WinUI\CodePad.WinUI.csproj"

Write-Host "CodePad WinUI - Mica + Notas" -ForegroundColor Cyan

$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    Write-Host "vswhere nao encontrado. Instale Visual Studio 2022+ com Windows App SDK." -ForegroundColor Yellow
    exit 1
}

$msbuild = & $vswhere -latest -requires Microsoft.Component.MSBuild -find "MSBuild\**\Bin\MSBuild.exe" | Select-Object -First 1
if (-not $msbuild) {
    Write-Host "MSBuild do Visual Studio nao encontrado." -ForegroundColor Yellow
    exit 1
}

Write-Host "MSBuild: $msbuild" -ForegroundColor DarkGray
& $msbuild $proj /restore /p:Configuration=Release /p:Platform=x64

$exe = Join-Path $root "CodePad.WinUI\bin\x64\Release\net8.0-windows10.0.19041.0\CodePad.WinUI.exe"
if (Test-Path $exe) {
    $destDir = Join-Path $root "..\dist"
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $dest = Join-Path $destDir "CodePad-WinUI.exe"
    Copy-Item -Force $exe $dest
    Write-Host "OK: $exe" -ForegroundColor Green
    Write-Host "Copia: $dest" -ForegroundColor Green
} else {
    Write-Host "Build terminou; exe nao encontrado no caminho esperado." -ForegroundColor Yellow
}
