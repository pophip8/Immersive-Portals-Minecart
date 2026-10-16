param([string]$ShaderDirectory, [string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$taskRepository = Split-Path -Parent $PSScriptRoot
$taskWorkspace = Split-Path -Parent $taskRepository
if (!$env:JAVA_HOME) {
    $env:JAVA_HOME = Join-Path $taskWorkspace 'tools/jdk25/jdk-25.0.4.1+1'
}
if (!(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { throw 'JDK 25 complet requis dans JAVA_HOME.' }
if (!$EvidenceDirectory) { $EvidenceDirectory = Join-Path $taskRepository '.qa/resultats' }
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
$taskRuns = @(@{tag='stable-sodium';shader=$null})
if ($ShaderDirectory) {
    foreach ($taskPair in @(@('complementary-reimagined','ComplementaryReimagined_r5.9.3.zip'),
        @('complementary-unbound','ComplementaryUnbound_r5.9.3.zip'), @('bsl','BSL_v10.1.8.zip'))) {
        $taskPack = Join-Path $ShaderDirectory $taskPair[1]
        if (!(Test-Path -LiteralPath $taskPack)) { throw "Shaderpack absent : $taskPack" }
        $taskRuns += @{tag=$taskPair[0];shader=(Resolve-Path -LiteralPath $taskPack).Path}
    }
}
$taskResults = @()
Push-Location $taskRepository
try {
    foreach ($taskRun in $taskRuns) {
        $taskArguments = @('-p','adapters/fabric','-I','../../qa/windows-client.gradle','runClientGameTest',
            '--console=plain','--configure-on-demand',"-PkbRunTag=$($taskRun.tag)")
        if ($taskRun.shader) { $taskArguments += "-PkbShader=$($taskRun.shader)" }
        $taskLog = Join-Path $EvidenceDirectory "$($taskRun.tag).log"
        & ./gradlew.bat @taskArguments *> $taskLog
        $taskCode = $LASTEXITCODE
        $taskResults += [pscustomobject]@{
            test=$taskRun.tag; exitCode=$taskCode
            commandsPass=[bool](Select-String -LiteralPath $taskLog -SimpleMatch 'KB_COMMANDS_IN_GAME_PASS')
            crossDimensionPass=[bool](Select-String -LiteralPath $taskLog -SimpleMatch 'KB_CROSS_DIMENSION_PASS')
        }
        $taskResults | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $EvidenceDirectory 'resultats-client.json')
        Write-Output "$($taskRun.tag) : code $taskCode"
    }
    if ($taskResults | Where-Object { $_.exitCode -ne 0 -or !$_.commandsPass -or !$_.crossDimensionPass }) {
        throw "Un essai a échoué. Consulter $EvidenceDirectory."
    }
} finally { Pop-Location }
