$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $root "CodePad.WinUI")

Write-Host "CodePad WinUI — Mica + Notas" -ForegroundColor Cyan
Write-Host "Requisito: Visual Studio 2022 com Windows App SDK" -ForegroundColor DarkGray

dotnet restore
dotnet build -c Release -p:Platform=x64

$exe = Get-ChildItem -Recurse -Filter "CodePad.WinUI.exe" "bin\Release" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($exe) {
    Write-Host "Executavel: $($exe.FullName)" -ForegroundColor Green
    Write-Host "Executar: dotnet run -c Release -p:Platform=x64" -ForegroundColor Green
} else {
    Write-Host "Build concluido; execute pelo Visual Studio se dotnet falhar no PriGen." -ForegroundColor Yellow
}
