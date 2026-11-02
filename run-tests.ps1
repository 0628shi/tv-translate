param(
    [string]$TestArgs = "selftest"
)
# Local test runner. No phone, no emulator: runs the JVM harness in :devtools.
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
$env:GRADLE_USER_HOME = Join-Path $root ".gradle-home"
$env:TMP = Join-Path $root ".tmp"
$env:TEMP = $env:TMP
$env:ANDROID_USER_HOME = Join-Path $root ".android"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$gradle = Join-Path $tools "gradle\gradle-8.7\bin\gradle.bat"
Set-Location $root
& $gradle :devtools:run "--args=$TestArgs" --offline --no-daemon --console=plain
exit $LASTEXITCODE
