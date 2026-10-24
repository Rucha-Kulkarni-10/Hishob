# ============================================================
#  start-hishob.ps1  –  HISHOB Backend Startup
#  Project root: derived from $PSScriptRoot
#  Run from: anywhere (resolves repository root automatically)
#
#  Each service is launched as a hidden java.exe child process so the
#  stack outlives this launcher, and every service writes an owned log
#  file under logs\stack. Readiness is bounded and configurable; a
#  failure names the service, shows a redacted log tail and stops only
#  the processes this invocation started. java.exe comes from JAVA_HOME
#  when set, because a java.exe found through PATH can be a stub whose
#  child is the JVM that really owns the port.
#
#  Requires: scripts\hishob-startup-lib.ps1
# ============================================================

param(
    # Per-service budget. Eureka cold-starts in roughly 80s while the machine is
    # also building, so the default leaves headroom rather than failing the run.
    [int]$StartupTimeoutSec = 240,
    [int]$ProbeIntervalSec = 2,
    [int]$HttpProbeTimeoutSec = 10,
    [int]$SoakSec = 3,
    [int]$ShutdownTimeoutSec = 30,
    [string]$LogDirectory
)

$ErrorActionPreference = "Stop"

$ROOT = $PSScriptRoot
. (Join-Path $ROOT "scripts\hishob-startup-lib.ps1")

if ([string]::IsNullOrWhiteSpace($LogDirectory)) { $LogDirectory = Join-Path $ROOT "logs\stack" }
$stateFile = Join-Path $LogDirectory "stack-state.json"

# ── Helpers ──────────────────────────────────────────────────
function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host $msg -ForegroundColor Cyan
}

function Write-OK([string]$msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Write-Fail([string]$msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
}

# Stops only the JVMs this run started, in reverse dependency order, including
# any child process of them (a java.exe stub runs the real JVM as its child).
# Never sweeps by port or by process name, so an unrelated Java process is untouched.
function Stop-StartedServices {
    param([array]$Entries, [int]$TimeoutSec)

    for ($i = $Entries.Count - 1; $i -ge 0; $i--) {
        $entry = $Entries[$i]
        $process = $entry.Process
        if (-not $process) { continue }

        $tree = @()
        try { $tree = @((@(Get-HishobChildProcessId -ProcessId $entry.Pid) + @($entry.JvmPids) + @($entry.Pid)) | Select-Object -Unique) } catch { $tree = @($entry.Pid) }
        [Array]::Reverse($tree)

        # Deepest first, so the tracked parent is the last thing to go. Only JVMs
        # are stopped here: the launcher owns java.exe, whatever else the service
        # spawned belongs to the service.
        foreach ($childPid in @($tree | Where-Object { $_ -gt 0 -and $_ -ne $entry.Pid })) {
            try {
                $child = Get-Process -Id $childPid -ErrorAction Stop
                if ($child.HasExited) { continue }
                if ($child.ProcessName -notmatch '^(java|javaw)$') { continue }
                Write-Host "  Stopping $($entry.Label) child PID $childPid ($($child.ProcessName))..." -ForegroundColor Yellow
                Stop-Process -Id $childPid -Force -ErrorAction Stop
            } catch { }
        }

        try {
            if ($process.HasExited) { continue }
            Write-Host "  Stopping $($entry.Label) (PID $($process.Id))..." -ForegroundColor Yellow
            Stop-Process -Id $process.Id -Force -ErrorAction Stop
            $null = $process.WaitForExit($TimeoutSec * 1000)
        } catch {
            Write-Host "  Could not stop $($entry.Label) (PID $($process.Id)): $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

# ── Dependency order: Config -> Eureka -> Auth -> Expense -> Settlement -> Gateway
# EnvDir is the services\<name>\.env holding that service's configuration; the
# infrastructure jars and the gateway take no credential file.
$stack = @(
    [pscustomobject]@{ Label = "Config Server"; Key = "config-server"; Port = 8888; EnvDir = $null; ProbeUrl = $null; Jar = "$ROOT\infrastructure\config-server\target\config-server-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{} },
    [pscustomobject]@{ Label = "Discovery Server (Eureka)"; Key = "discovery-server"; Port = 8761; EnvDir = $null; ProbeUrl = "http://localhost:8761/"; Jar = "$ROOT\infrastructure\discovery-server\target\discovery-server-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{} },
    [pscustomobject]@{ Label = "Auth Service"; Key = "auth-service"; Port = 8081; EnvDir = "auth-service"; ProbeUrl = $null; Jar = "$ROOT\services\auth-service\target\auth-service-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{} },
    [pscustomobject]@{ Label = "Expense Service"; Key = "expense-service"; Port = 8083; EnvDir = "expense-service"; ProbeUrl = $null; Jar = "$ROOT\services\expense-service\target\expense-service-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{} },
    [pscustomobject]@{ Label = "Settlement Service"; Key = "settlement-service"; Port = 8085; EnvDir = "settlement-service"; ProbeUrl = $null; Jar = "$ROOT\services\settlement-service\target\settlement-service-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{} },
    [pscustomobject]@{ Label = "API Gateway"; Key = "api-gateway"; Port = 8087; EnvDir = $null; ProbeUrl = $null; Jar = "$ROOT\gateway\api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar"; ServiceEnv = @{ PORT = "8087" } }
)

# Names each service must have before its JVM is allowed to start. The service
# ymls carry no credential fallbacks, so a missing variable would otherwise fail
# deep inside the JVM.
$serviceRequirements = [ordered]@{
    "auth-service"       = @("DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN")
    "expense-service"    = @("DB_HOST", "DB_PORT", "DB_NAME", "DB_SCHEMA", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN")
    "settlement-service" = @("DB_HOST", "DB_PORT", "DB_NAME", "DB_SCHEMA", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN")
}

$jarPhrases = @($stack | ForEach-Object { $_.Key })

# ── Banner ───────────────────────────────────────────────────
Write-Host ""
Write-Host "  HISHOB Backend Startup" -ForegroundColor Magenta
Write-Host "  ======================" -ForegroundColor Magenta

# ── Pre-flight: everything is checked before any JVM starts ──
Write-Step "Pre-flight: java, JARs, environment files, ports..."
$problems = @()

$javaPath = Resolve-HishobJavaExe
if (-not $javaPath) {
    $problems += "java.exe was not found through JAVA_HOME or PATH; install or expose the JDK before starting the stack"
    $javaExe = $null
} else {
    $javaExe = $javaPath
    Write-OK "Using java.exe from $javaExe"
}

foreach ($service in $stack) {
    if (Test-Path -LiteralPath $service.Jar) {
        Write-OK "Found JAR: $($service.Label)"
    } else {
        $problems += "$($service.Label) : no jar at $($service.Jar)"
    }
}

$redactSecrets = @()
foreach ($service in $stack) {
    if (-not $service.EnvDir) { continue }
    $envFile = "$ROOT\services\$($service.EnvDir)\.env"
    $values = Read-HishobEnvFile -Path $envFile
    $problems += @(Find-HishobEnvProblem -ServiceName $service.EnvDir -Values $values -RequiredNames $serviceRequirements[$service.EnvDir])
    if ($values) {
        $service.ServiceEnv = $values
        $redactSecrets += @(Get-HishobSecretValue -EnvValues $values)
    }
}
if ($problems.Count -eq 0) {
    Write-OK "Environment ready for: $($serviceRequirements.Keys -join ', ')"
}

# A port already in use means something is already running here. Report it and
# stop, rather than starting a second stack or killing what is there.
$occupied = @()
$netstat = @(netstat -ano)
foreach ($service in $stack) {
    $pids = @(Get-HishobListeningPid -NetstatOutput $netstat -Port $service.Port)
    if ($pids.Count -gt 0) {
        $ownership = foreach ($ownerPid in $pids) {
            if (Test-HishobOwnedJava -ProcessId $ownerPid -RootPath $ROOT -JarPhrases $jarPhrases) { "HISHOB-owned" } else { "not HISHOB-owned" }
        }
        $occupied += "$($service.Label) : port $($service.Port) is already listening (PID $($pids -join ', '); $($ownership -join ', '))"
    }
}
$problems += $occupied

if ($problems.Count -gt 0) {
    foreach ($problem in $problems) { Write-Fail $problem }
    Write-Host "         Only variable names are printed, never values." -ForegroundColor DarkGray
    Write-Host "         Copy services\<service>\.env.example and fill in every required entry." -ForegroundColor Yellow
    if ($occupied.Count -gt 0) {
        Write-Host "         Nothing was started. Free those ports (for example .\stop-hishob.ps1) and run this again." -ForegroundColor Yellow
    }
    exit 1
}

New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
Write-OK "Logging to $LogDirectory"

# ── Start, wait, and verify one service at a time ────────────
$started = @()
$number = 0
foreach ($service in $stack) {
    $number++
    Write-Step "[$number/$($stack.Count)] Starting $($service.Label) :$($service.Port)"

    # The JVM inherits this launcher's process environment, so load this
    # service's variables, launch, then remove them again - otherwise the next
    # service would be started with the previous service's database name.
    foreach ($name in @($service.ServiceEnv.Keys)) {
        [Environment]::SetEnvironmentVariable([string]$name, [string]$service.ServiceEnv[[string]$name], "Process")
    }

    $stdoutLog = Join-Path $LogDirectory "$($service.Key).out.log"
    $stderrLog = Join-Path $LogDirectory "$($service.Key).err.log"

    $process = $null
    try {
        $process = Start-Process -FilePath $javaExe `
            -ArgumentList @("-jar", "`"$($service.Jar)`"") `
            -WorkingDirectory $ROOT `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdoutLog `
            -RedirectStandardError $stderrLog `
            -PassThru
        Write-OK "Launched $($service.Label) as PID $($process.Id)"
    } catch {
        Write-Fail "Could not launch $($service.Label): $($_.Exception.Message)"
    } finally {
        # Remove every name this service put into the launcher's environment.
        foreach ($name in @($service.ServiceEnv.Keys)) { [Environment]::SetEnvironmentVariable([string]$name, $null, "Process") }
    }
    if (-not $process) {
        Stop-StartedServices -Entries $started -TimeoutSec $ShutdownTimeoutSec
        Write-HishobStackState -Path $stateFile -RootPath $ROOT -Entries @()
        Write-Fail "$($service.Label) did not start; removed the $($started.Count) service(s) started by this run."
        exit 1
    }

    $entry = [pscustomobject]@{
        Label   = $service.Label
        Key     = $service.Key
        Port    = $service.Port
        Pid     = $process.Id
        Jar     = $service.Jar
        Stdout  = $stdoutLog
        Stderr  = $stderrLog
        JvmPids = @()
        Process = $process
    }
    $started += $entry

    $ready = Wait-HishobService -Name $service.Label -Port $service.Port -Process $process `
        -ProbeUrl $service.ProbeUrl -TimeoutSec $StartupTimeoutSec -IntervalSec $ProbeIntervalSec `
        -HttpTimeoutSec $HttpProbeTimeoutSec -SoakSec $SoakSec

    if ($ready.Result -ne "Ready") {
        Write-Fail "$($service.Label) is not ready on :$($service.Port) ($($ready.Result) after $($ready.Seconds)s): $($ready.Detail)"
        Write-Host (Get-HishobLogTail -Paths @($stdoutLog, $stderrLog) -Lines 25 -Secrets $redactSecrets) -ForegroundColor DarkGray
        Stop-StartedServices -Entries $started -TimeoutSec $ShutdownTimeoutSec
        Write-HishobStackState -Path $stateFile -RootPath $ROOT -Entries @()
        Write-Host "         Services started by this run were stopped. Nothing else was touched." -ForegroundColor Yellow
        Write-Host "         Logs kept for inspection: $stdoutLog" -ForegroundColor Yellow
        exit 1
    }

    Write-OK "$($service.Label) ready on :$($service.Port) in $($ready.Seconds)s$(if ($ready.StatusCode) { " (HTTP $($ready.StatusCode))" })"

    # Record which PIDs actually own the port. When java.exe is a stub in front of
    # the real JVM these differ, and an operator (and stop-hishob.ps1) needs the
    # JVM, not the parent. Descendants are deliberately not recorded: they are
    # transient at startup, and a stale PID could later be recycled onto a process
    # this stack does not own. Cleanup scans the tree live instead.
    $entry.JvmPids = @(Get-HishobListeningPid -NetstatOutput @(netstat -ano) -Port $service.Port)

    # Record progress so an interrupted run still leaves PIDs a stop script can use.
    Write-HishobStackState -Path $stateFile -RootPath $ROOT -Entries @($started | ForEach-Object {
        [pscustomobject]@{ label = $_.Label; key = $_.Key; port = $_.Port; pid = $_.Pid; javaPids = @($_.JvmPids); jar = $_.Jar; stdout = $_.Stdout; stderr = $_.Stderr }
    })
}

# ── Summary ──────────────────────────────────────────────────
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "       HISHOB BACKEND IS FULLY RUNNING        " -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Consolidated 3-domain architecture:" -ForegroundColor Cyan
Write-Host "    * Auth Service (authentication, users, friendships, notifications)" -ForegroundColor DarkGray
Write-Host "    * Expense Service (groups, expenses, analytics)" -ForegroundColor DarkGray
Write-Host "    * Settlement Service (balances, settlements)" -ForegroundColor DarkGray
Write-Host ""
foreach ($entry in $started) {
    $jvmPids = @($entry.JvmPids)
    $jvmNote = ""
    if ($jvmPids.Count -gt 0 -and -not ($jvmPids.Count -eq 1 -and $jvmPids[0] -eq $entry.Pid)) {
        $jvmNote = "  (jvm PID $($jvmPids -join ', '))"
    }
    Write-Host ("  {0,-26} :{1,-5} PID {2}{3}" -f $entry.Label, $entry.Port, $entry.Pid, $jvmNote) -ForegroundColor White
}
Write-Host ""
Write-Host "  Config Server    : http://localhost:8888" -ForegroundColor White
Write-Host "  Eureka           : http://localhost:8761" -ForegroundColor White
Write-Host "  API Gateway      : http://localhost:8087" -ForegroundColor White
Write-Host ""
Write-Host "  These JVMs are independent of this window; it can close now." -ForegroundColor DarkGray
Write-Host "  Logs (secrets never written to them): $LogDirectory" -ForegroundColor DarkGray
Write-Host "  PIDs + ports: $stateFile" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Stop all services: .\stop-hishob.ps1" -ForegroundColor DarkGray
Write-Host ""
