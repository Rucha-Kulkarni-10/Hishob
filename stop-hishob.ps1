# ============================================================
#  stop-hishob.ps1
#  Stops the HISHOB backend Java processes owned by this repository.
#  Project: HISHOB
#
#  Ownership is required, not assumed: a PID is only stopped when its
#  command line names both this repository and a HISHOB module. Java
#  processes from other projects are reported and left running.
#
#  Run .\stop-hishob.ps1 -DryRun first to see what would be stopped.
# ============================================================

param(
    [int]$KillTimeoutSec = 20,
    [string]$LogDirectory,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$ROOT = $PSScriptRoot
. (Join-Path $ROOT "scripts\hishob-startup-lib.ps1")

if ([string]::IsNullOrWhiteSpace($LogDirectory)) { $LogDirectory = Join-Path $ROOT "logs\stack" }
$stateFile = Join-Path $LogDirectory "stack-state.json"

# Ports of the services launched by start-hishob.ps1 (config, eureka, auth,
# expense, settlement, gateway). group-service (:8082), balance-service (:8084)
# and the legacy notification-service (:8086, now served by Auth) are not
# launched by default; they are still checked here if started manually, but only
# a HISHOB-owned command line makes a process stoppable.
$hishobPorts = @(8888, 8761, 8081, 8082, 8083, 8084, 8085, 8086, 8087)
$hishobJarPhrases = @("config-server", "discovery-server", "auth-service", "group-service", "expense-service", "balance-service", "settlement-service", "notification-service", "api-gateway")

Write-Host ""
if ($DryRun) {
    Write-Host "Inspecting HISHOB backend services (dry run, nothing will be stopped)..." -ForegroundColor Cyan
} else {
    Write-Host "Stopping HISHOB backend services..." -ForegroundColor Cyan
}
Write-Host ""

# ── Gather candidate PIDs from three independent sources ─────
# Keyed by the PID as text: an integer key into an ordered dictionary is read by
# PowerShell as a positional index, not as a key.
$candidates = [ordered]@{}
$addCandidate = {
    param([int]$ProcessId, [string]$How)
    if ($ProcessId -le 0) { return }
    $key = [string]$ProcessId
    if ($candidates.Contains($key)) { $candidates[$key] = "$($candidates[$key]), $How" }
    else { $candidates.Add($key, $How) }
}

# 1. The state file written by start-hishob.ps1 - the authoritative record.
#    javaPids are the JVMs that own the ports; pid is the launched parent, which
#    can be a java.exe stub in front of them.
foreach ($entry in @(Read-HishobStackState -Path $stateFile)) {
    $recordedPid = 0
    if ($entry.PID -and [int]::TryParse([string]$entry.PID, [ref]$recordedPid)) {
        & $addCandidate $recordedPid "state:$($entry.key)"
    }
    foreach ($recordedJvmPid in @($entry.javaPids)) {
        $childPid = 0
        if ([int]::TryParse([string]$recordedJvmPid, [ref]$childPid)) {
            & $addCandidate $childPid "state:$($entry.key) jvm"
        }
    }
}

# 2. Every HISHOB port that is currently listening.
$netstat = @(netstat -ano)
foreach ($port in $hishobPorts) {
    foreach ($listeningPid in @(Get-HishobListeningPid -NetstatOutput $netstat -Port $port)) {
        & $addCandidate ([int]$listeningPid) "port:$port"
    }
}

# 3. Any remaining JVM, in case one died before binding its port.
foreach ($jvm in @(Get-Process -Name "java", "javaw" -ErrorAction SilentlyContinue)) {
    & $addCandidate $jvm.Id "process-list"
}

if ($candidates.Count -eq 0) {
    Write-Host "  No Java processes found." -ForegroundColor Yellow
    exit 0
}

# ── Classify by ownership, then act ─────────────────────────
$stopped = 0
$skipped = @()

foreach ($candidateKey in @($candidates.Keys)) {
    $processId = [int]$candidateKey
    $how = $candidates[[string]$candidateKey]
    if (Test-HishobOwnedJava -ProcessId $processId -RootPath $ROOT -JarPhrases $hishobJarPhrases) {
        if ($DryRun) {
            Write-Host "  Would stop HISHOB-owned PID $processId ($how)" -ForegroundColor DarkGray
            $stopped++
            continue
        }
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            $null = Wait-Process -Id $processId -Timeout $KillTimeoutSec -ErrorAction SilentlyContinue
            Write-Host "  Stopped HISHOB-owned PID $processId ($how)" -ForegroundColor Green
            $stopped++
        } catch {
            Write-Host "  Could not stop PID $processId ($how): $($_.Exception.Message)" -ForegroundColor Yellow
        }
    } else {
        # Deliberately left alone: it is not a JVM launched from this repository.
        $skipped += $processId
    }
}

if (-not $DryRun -and $stopped -gt 0 -and (Test-Path -LiteralPath $stateFile)) {
    # Clear the record so a later run never acts on PIDs that no longer exist.
    Write-HishobStackState -Path $stateFile -RootPath $ROOT -Entries @()
}

Write-Host ""
if ($stopped -eq 0) {
    Write-Host "  No HISHOB-owned Java processes found." -ForegroundColor Yellow
} elseif ($DryRun) {
    Write-Host "  $stopped service(s) would be stopped." -ForegroundColor Cyan
} else {
    Write-Host "  Stopped $stopped service(s)." -ForegroundColor Green
}

if ($skipped.Count -gt 0) {
    Write-Host "  Left running (not started from this repository): PID $($skipped -join ', ')" -ForegroundColor Yellow
}

Write-Host ""
