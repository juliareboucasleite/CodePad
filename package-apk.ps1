# Gera CodePad.apk (app Android nativo em /android)
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$version = & (Join-Path $root "scripts\Get-ProjectVersion.ps1")
Write-Host "CodePad APK v$version" -ForegroundColor Cyan

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path $sdk)) {
    Write-Host "Android SDK não encontrado em $sdk" -ForegroundColor Red
    Write-Host "Instale o Android Studio e o SDK Platform 34." -ForegroundColor Yellow
    exit 1
}

$androidDir = Join-Path $root "android"
if (-not (Test-Path $androidDir)) {
    Write-Host "Pasta android/ não encontrada." -ForegroundColor Red
    exit 1
}

$wrapperJar = Join-Path $androidDir "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "Baixando Gradle Wrapper..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path (Split-Path $wrapperJar) | Out-Null
    $url = "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
    Invoke-WebRequest -Uri $url -OutFile $wrapperJar -UseBasicParsing
}

$localProps = Join-Path $androidDir "local.properties"
$sdkEscaped = ($sdk -replace '\\', '\\')
"sdk.dir=$sdkEscaped" | Set-Content -Path $localProps -Encoding ASCII

Set-Location $androidDir

$gradlew = Join-Path $androidDir "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "gradlew.bat ausente. Copie o wrapper do Android Studio ou execute:" -ForegroundColor Yellow
    Write-Host "  cd android && gradle wrapper" -ForegroundColor Yellow
    exit 1
}

Write-Host "Compilando APK (release)..." -ForegroundColor Cyan
& $gradlew "-PappVersion=$version" :app:assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "Tentando assembleDebug..." -ForegroundColor Yellow
    & $gradlew "-PappVersion=$version" :app:assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $apk = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" -Recurse |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
} else {
    $apk = Get-ChildItem -Path "app\build\outputs\apk\release" -Filter "*.apk" -Recurse |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if (-not $apk) {
    Write-Host "APK não encontrado após o build." -ForegroundColor Red
    exit 1
}

$destDir = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir "CodePad.apk"
Copy-Item -Force -Path $apk.FullName -Destination $dest

Set-Location $root
Write-Host "APK pronto: $dest ($([math]::Round($apk.Length / 1MB, 2)) MB)" -ForegroundColor Green
