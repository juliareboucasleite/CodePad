# Build completo para release: EXE instalador + CodePad.exe (nome esperado pelo atualizador)
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$version = & (Join-Path $root "scripts\Get-ProjectVersion.ps1")
Write-Host "CodePad release v$version" -ForegroundColor Cyan

$jdk21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
if (-not (Test-Path $jdk21)) {
    $jdk21 = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-21*" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk21 -or -not (Test-Path $jdk21)) {
    Write-Host "JDK 21 não encontrado. Defina JAVA_HOME para um JDK 21+ com jpackage." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk21
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor DarkGray

# Sincroniza app.properties com o pom
$propsFile = "src\main\resources\org\example\app.properties"
"app.version=$version" | Set-Content -Path $propsFile -Encoding UTF8 -NoNewline
Add-Content -Path $propsFile -Value "" -Encoding UTF8
Write-Host "app.properties -> $version" -ForegroundColor DarkGray

Write-Host "`n=== Instalador Windows (.exe) ===" -ForegroundColor Cyan
& .\package-installer.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$installerDir = "dist\installer"
$installer = Get-ChildItem -Path $installerDir -Filter "CodePad-*.exe" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $installer) {
    Write-Host "Instalador não encontrado em $installerDir" -ForegroundColor Red
    exit 1
}

$releaseExe = Join-Path "dist" "CodePad.exe"
Copy-Item -Force -Path $installer.FullName -Destination $releaseExe
Write-Host "Copiado para $releaseExe (atualizador automático)" -ForegroundColor Green

Write-Host "`n=== APK Android ===" -ForegroundColor Cyan
if (Test-Path ".\package-apk.ps1") {
    & .\package-apk.ps1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "APK não gerado (veja mensagens acima)." -ForegroundColor Yellow
    }
} else {
    Write-Host "package-apk.ps1 não encontrado." -ForegroundColor Yellow
}

Write-Host "`nArtefatos para anexar na release GitHub v$version :" -ForegroundColor Green
Write-Host "  - $releaseExe"
if (Test-Path "dist\CodePad.apk") {
    Write-Host "  - dist\CodePad.apk"
}
Write-Host "  - $($installer.FullName)"
Write-Host "`nTag sugerida: v$version"
