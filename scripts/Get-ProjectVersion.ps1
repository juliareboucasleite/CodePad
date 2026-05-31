# Lê a versão do pom.xml (fonte única para empacotamento)
param(
    [string]$PomPath = (Join-Path (Split-Path $PSScriptRoot -Parent) "pom.xml")
)

if (-not (Test-Path $PomPath)) {
    throw "pom.xml não encontrado: $PomPath"
}

[xml]$pom = Get-Content -Path $PomPath -Encoding UTF8
$version = $pom.project.version
if (-not $version) {
    throw "Não foi possível ler project/version em $PomPath"
}
return $version.Trim()
