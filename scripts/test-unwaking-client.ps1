$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$fixture = Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'build') -Directory | Where-Object Name -Like 'unwaking-domain-gametest-*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$fixture -or !(Test-Path -LiteralPath (Join-Path $fixture.FullName 'world\level.dat'))) { throw 'Run test-unwaking-domain.ps1 first to create the isolated dimension fixture.' }
$testDirectory = Join-Path $projectDirectory ('build\unwaking-client-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $testDirectory) { throw 'Client test directory already exists; run again with a fresh timestamp.' }
New-Item -ItemType Directory -Path (Join-Path $testDirectory 'saves') | Out-Null
Copy-Item -LiteralPath (Join-Path $fixture.FullName 'world') -Destination (Join-Path $testDirectory 'saves\unwaking-visuals') -Recurse
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $testDirectory 'options.txt'), "onboardAccessibility:false`nonboardAccessibilityFinished:true`npauseOnLostFocus:false`nrenderDistance:20`nsimulationDistance:6`nguiScale:3`nmaxFps:60`n", $utf8)
$groovyDirectory = $testDirectory.Replace('\','/').Replace("'", "\'")
$initScript = Join-Path $testDirectory 'client-probe.gradle'
$init = "allprojects { afterEvaluate { if (extensions.findByName('neoForge') != null) { sourceSets.main.java.srcDir 'scripts/client-probe'; neoForge.runs.client.gameDirectory = file('$groovyDirectory'); neoForge.runs.client.systemProperty 'magical.unwaking.clientProbe', 'true'; neoForge.runs.client.programArguments.addAll('--quickPlaySingleplayer', 'unwaking-visuals', '--width', '1600', '--height', '900'); neoForge.runs.client.logLevel = org.slf4j.event.Level.INFO } } }"
[IO.File]::WriteAllText($initScript, $init, $utf8)
Write-Output "Isolated client world: $testDirectory"
Push-Location -LiteralPath $projectDirectory
try {
    & .\gradlew.bat -I $initScript runClient --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Client probe failed (exit $LASTEXITCODE). Logs: $testDirectory\logs\latest.log" }
    if (!(Select-String -LiteralPath (Join-Path $testDirectory 'logs\latest.log') -SimpleMatch 'all twelve live attack views captured' -Quiet)) { throw 'Client exited without completing the visual probe.' }
    Write-Output "Client probe complete: $testDirectory\screenshots"
} finally { Pop-Location }
