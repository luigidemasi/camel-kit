#
# camel-kit.ps1 — wrapper script for the Camel Kit standalone JAR
#
# Usage: .\camel-kit.ps1 init my-project --ai bob
#
# Place this script in the same directory as camel-kit-standalone-*.jar,
# or set CAMEL_KIT_HOME to the directory containing the JAR.
#

$ErrorActionPreference = "Stop"

# Resolve the directory where this script lives
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Allow override via CAMEL_KIT_HOME
$CamelKitHome = if ($env:CAMEL_KIT_HOME) { $env:CAMEL_KIT_HOME } else { $ScriptDir }

# Find the JAR
$Jar = Get-ChildItem -Path $CamelKitHome -Filter "camel-kit-standalone-*.jar" -File |
    Where-Object { $_.Name -notlike "original-*" } |
    Select-Object -First 1

if (-not $Jar) {
    Write-Error "camel-kit-standalone-*.jar not found in $CamelKitHome`nDownload it or set CAMEL_KIT_HOME to the directory containing the JAR."
    exit 1
}

# Check Java is available
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java not found. Camel Kit requires Java 17 or later."
    exit 1
}

$JvmOpts = if ($env:CAMEL_KIT_OPTS) { $env:CAMEL_KIT_OPTS -split " " } else { @() }
& java @JvmOpts -jar $Jar.FullName @args
