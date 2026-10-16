$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME) {
    $taskLocalJdk = Join-Path (Split-Path -Parent $PSScriptRoot) 'tools/jdk25/jdk-25.0.4.1+1'
    if (Test-Path -LiteralPath (Join-Path $taskLocalJdk 'bin/javac.exe')) {
        $env:JAVA_HOME = $taskLocalJdk
    } else {
        throw 'Configurer JAVA_HOME avec un JDK 25 complet (pas seulement un JRE).'
    }
}
Push-Location $PSScriptRoot
try {
    & ./gradlew.bat -p adapters/fabric :assemble :verifyModdedArtifact --console=plain --configure-on-demand
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du JAR échouée.' }
    Write-Output 'JAR : adapters/fabric/build/libs/portails-immersifs-3.1.0-bilingual.1-mc26.3-fabric.jar'
} finally {
    Pop-Location
}
