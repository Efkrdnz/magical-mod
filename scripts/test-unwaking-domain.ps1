# Run the domain integration test in a fresh disposable world, without editing run/ or production resources.
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$testDirectory = Join-Path $projectDirectory "build\unwaking-domain-gametest-$stamp"
$suffix = 2
while (Test-Path -LiteralPath $testDirectory) {
    $testDirectory = Join-Path $projectDirectory "build\unwaking-domain-gametest-$stamp-$suffix"
    $suffix++
}
$packDirectory = Join-Path $testDirectory 'world\datapacks\unwaking_fixture'
$presetDirectory = Join-Path $packDirectory 'data\minecraft\worldgen\world_preset'
New-Item -ItemType Directory -Path $presetDirectory | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $packDirectory 'pack.mcmeta'), '{"pack":{"pack_format":61,"description":"Isolated Unwaking domain GameTest fixture"}}', $utf8)
$flat = @'
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "minecraft:flat",
        "settings": {
          "biome": "minecraft:plains", "features": false, "lakes": false,
          "layers": [{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],
          "structure_overrides": []
        }
      }
    },
    "minecraft:the_nether": {"type":"minecraft:the_nether","generator":{"type":"minecraft:noise","biome_source":{"type":"minecraft:multi_noise","preset":"minecraft:nether"},"settings":"minecraft:nether"}},
    "minecraft:the_end": {"type":"minecraft:the_end","generator":{"type":"minecraft:noise","biome_source":{"type":"minecraft:the_end"},"settings":"minecraft:end"}}
  }
}
'@ | ConvertFrom-Json
$domain = Get-Content -LiteralPath (Join-Path $projectDirectory 'src\main\resources\data\magical\dimension\chronos_end.json') -Raw | ConvertFrom-Json
$flat.dimensions | Add-Member -MemberType NoteProperty -Name 'magical:chronos_end' -Value $domain
[IO.File]::WriteAllText((Join-Path $presetDirectory 'flat.json'), ($flat | ConvertTo-Json -Depth 20), $utf8)
[IO.File]::WriteAllText((Join-Path $testDirectory 'server.properties'), "online-mode=false`n", $utf8)
$groovyDirectory = $testDirectory.Replace('\','/').Replace("'", "\'")
$initScript = Join-Path $testDirectory 'gametest.gradle'
$init = "allprojects { afterEvaluate { if (extensions.findByName('neoForge') != null) { neoForge.runs.gameTestServer.gameDirectory = file('$groovyDirectory'); neoForge.runs.gameTestServer.systemProperty 'magical.unwaking.domainTests', 'true'; neoForge.runs.gameTestServer.logLevel = org.slf4j.event.Level.INFO } } }"
[IO.File]::WriteAllText($initScript, $init, $utf8)
Write-Output "Isolated test world: $testDirectory"
Push-Location -LiteralPath $projectDirectory
try {
    & .\gradlew.bat -I $initScript runGameTestServer
    if ($LASTEXITCODE -ne 0) { throw "Domain GameTests failed (exit $LASTEXITCODE). Logs: $testDirectory\logs\latest.log" }
} finally { Pop-Location }
