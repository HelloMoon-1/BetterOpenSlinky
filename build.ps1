[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Generator,
    [switch]$JavaOnly,
    [switch]$RestoreDependencies
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot 'build'
New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null

function Invoke-BuildTool {
    param([string]$Tool, [string[]]$ToolArguments)
    & $Tool @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

if (-not $JavaHome) {
    $javacCommand = Get-Command javac.exe -ErrorAction Stop
    $JavaHome = Split-Path (Split-Path $javacCommand.Source -Parent) -Parent
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
foreach ($tool in @($javac, $java, $jar)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing JDK tool: $tool" }
}

Push-Location -LiteralPath $projectRoot
try {
    if ($RestoreDependencies) { & .\tools\download-deps.ps1 }
    else { & .\tools\download-deps.ps1 -Offline }
    New-Item -ItemType Directory -Force -Path 'build\tools' | Out-Null
    Invoke-BuildTool $javac @('--release', '8', '-encoding', 'UTF-8', '-cp', 'deps/tooling/*', '-d', 'build/tools', 'tools/RemapJar.java')
    Invoke-BuildTool $java @('-cp', 'build/tools;deps/tooling/*', 'RemapJar', 'mappings/named-to-official.txt', 'reverse', 'deps/minecraft-1.8.9-official.jar', 'deps/minecraft-1.8.9-named.jar', '--mcp-exceptions')
    Write-Host 'Compiling recovered Java source (Java 8 class format).'
    $classes = Join-Path $buildRoot 'classes'
    if (Test-Path -LiteralPath $classes) {
        $resolvedClasses = (Resolve-Path -LiteralPath $classes).Path
        if (-not $resolvedClasses.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected classes directory' }
        Remove-Item -LiteralPath $resolvedClasses -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $sourceFiles = Get-ChildItem -LiteralPath 'java\src\main\java' -Recurse -Filter '*.java' -File | Sort-Object FullName
    if ($sourceFiles.Count -eq 0) { throw 'No recovered Java sources found' }
    $dependencyClasspath = @('deps/minecraft-1.8.9-named.jar')
    $dependencyClasspath += Get-ChildItem -LiteralPath 'deps/libraries' -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName.Replace('\', '/') }
    $sourceArguments = @('--release', '8', '-encoding', 'UTF-8', '-classpath', ('"' + ($dependencyClasspath -join ';') + '"'), '-d', '"build/classes"')
    $sourceArguments += $sourceFiles | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
    $argFile = Join-Path $buildRoot 'javac-sources.args'
    [IO.File]::WriteAllLines($argFile, $sourceArguments, [Text.UTF8Encoding]::new($false))
    Invoke-BuildTool $javac @("@$argFile")

    $manifest = @('Manifest-Version: 1.0', 'Implementation-Title: Slinky injectable payload', 'Implementation-Version: 0.5.0-settings', '') -join "`r`n"
    [IO.File]::WriteAllText((Join-Path $buildRoot 'MANIFEST.MF'), $manifest, [Text.UTF8Encoding]::new($false))
    Invoke-BuildTool $jar @('cfm', 'build/payload-101.jar', 'build/MANIFEST.MF', '-C', 'build/classes', '.', '-C', 'java/src/main/resources', '.')

    Write-Host 'Compiling the static mapping tool and producing SRG/official payloads.'
    Invoke-BuildTool $java @('-cp', 'build/tools;deps/tooling/*', 'RemapJar', 'mappings/named-to-srg.txt', 'forward', 'build/payload-101.jar', 'build/payload-102.jar', 'deps/minecraft-1.8.9-named.jar')
    Invoke-BuildTool $java @('-cp', 'build/tools;deps/tooling/*', 'RemapJar', 'mappings/named-to-official.txt', 'forward', 'build/payload-101.jar', 'build/payload-103.jar', 'deps/minecraft-1.8.9-named.jar')
    foreach ($variant in @(@('102','srg'), @('103','official'))) {
        $mappingRoot = Join-Path $buildRoot ('mapping-' + $variant[0])
        $mappingDestination = Join-Path $mappingRoot 'dev\slinky\inject'
        New-Item -ItemType Directory -Force -Path $mappingDestination | Out-Null
        Copy-Item -LiteralPath ('java\mappings\' + $variant[1] + '.txt') -Destination (Join-Path $mappingDestination 'mappings.txt') -Force
        Invoke-BuildTool $jar @('ufm', ('build/payload-' + $variant[0] + '.jar'), 'build/MANIFEST.MF', '-C', $mappingRoot, 'dev/slinky/inject/mappings.txt')
    }

    if (-not $JavaOnly) {
        Write-Host 'Compiling the native bridge and launcher, embedding the freshly built payloads.'
        if (-not $Generator) {
            $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
            if (-not (Test-Path -LiteralPath $vswhere)) { throw 'Visual Studio detection unavailable; pass -Generator explicitly.' }
            $installations = (& $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -format json) | ConvertFrom-Json
            if (-not $installations) { throw 'No Visual Studio C++ toolchain found.' }
            $major = ([version]$installations[0].installationVersion).Major
            $Generator = switch ($major) { 18 { 'Visual Studio 18 2026' } 17 { 'Visual Studio 17 2022' } 16 { 'Visual Studio 16 2019' } default { throw "Unsupported Visual Studio version $major; pass -Generator explicitly." } }
        }
        $cmake = (Get-Command cmake.exe -ErrorAction Stop).Source
        Invoke-BuildTool $cmake @('-S', 'native', '-B', 'build/native', '-G', $Generator, '-A', 'x64', "-DPAYLOAD_DIR=$buildRoot", "-DJAVA_HOME=$JavaHome")
        Invoke-BuildTool $cmake @('--build', 'build/native', '--config', 'Release')
    }
    Write-Host 'Build completed. No launcher, native DLL, or payload class was executed.'
}
finally { Pop-Location }
