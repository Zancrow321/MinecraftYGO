# Windows counterpart of build.sh. Run from a shell with the MSVC environment loaded
# (a "Developer PowerShell for VS", or ilammy/msvc-dev-cmd in CI).
$ErrorActionPreference = 'Stop'

$Root = Resolve-Path "$PSScriptRoot/.."
$Src = Join-Path $Root 'native/ocgcore'
$Build = Join-Path $Root 'native/build'

if (-not (Test-Path (Join-Path $Src 'ocgapi.h'))) {
    throw 'ocgcore submodule missing; run: git submodule update --init --recursive'
}

$Arch = switch ($env:PROCESSOR_ARCHITECTURE) {
    'AMD64' { 'x86_64' }
    'ARM64' { 'aarch64' }
    default { throw "unsupported arch $env:PROCESSOR_ARCHITECTURE" }
}

$MesonArgs = @('--buildtype=release', '-Ddefault_library=shared', '-Db_vscrt=mt')
if (Test-Path $Build) { $MesonArgs += '--wipe' }
meson setup $Build $Src @MesonArgs
if ($LASTEXITCODE -ne 0) { throw 'meson setup failed' }
ninja -C $Build
if ($LASTEXITCODE -ne 0) { throw 'ninja failed' }

$Out = Join-Path $Root "engine/src/main/resources/natives/windows-$Arch"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
Copy-Item (Join-Path $Build 'ocgcore.dll') (Join-Path $Out 'ocgcore.dll')
Write-Host "Built $Out/ocgcore.dll"
