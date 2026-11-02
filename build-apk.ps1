param(
    [switch]$Release
)
# Build helper for this machine.
# Uses the local toolchain under <repo-root>/environment (JDK, Gradle, Android SDK).
# Project sits at <repo-root>/tv-translate, so the environment is one level up.
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$repoRoot = Split-Path -Parent $root
$envBase = Join-Path $repoRoot "environment"
$tools = Join-Path $envBase ".tools"

$jdk = Get-ChildItem (Join-Path $tools "jdk") -Directory | Select-Object -First 1
if (-not $jdk) { throw "JDK not found under $tools\jdk." }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $tools "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
# The shared environment tree is read-only for this session, so use a
# project-local Gradle home copied from it (see README).
$env:GRADLE_USER_HOME = Join-Path $root ".gradle-home"
$env:TMP = Join-Path $root ".tmp"
$env:TEMP = $env:TMP
$env:ANDROID_USER_HOME = Join-Path $root ".android"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
New-Item -ItemType Directory -Force -Path $env:TMP | Out-Null

$gradle = Join-Path $tools "gradle\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) { throw "Gradle not found at $gradle" }

Set-Location $root
$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
Write-Output "Building: gradle $task (offline)"
& $gradle $task --offline --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Release) {
    Write-Output "APK: $root\app\build\outputs\apk\release\app-release-unsigned.apk"
} else {
    Write-Output "APK: $root\app\build\outputs\apk\debug\app-debug.apk"
}
