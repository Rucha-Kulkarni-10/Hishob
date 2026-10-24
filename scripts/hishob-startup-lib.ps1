# ============================================================
#  hishob-startup-lib.ps1  –  HISHOB local stack startup support
#  Dot-sourced by start-hishob.ps1 and stop-hishob.ps1.
#  Unit-tested by scripts/test-hishob-startup-lib.ps1.
#
#  Contains definitions only: dot-sourcing this file must not touch the
#  filesystem, start processes, or write output.
# ============================================================

# Placeholder values that must never be accepted as real credentials.
# Kept in sync with the @PostConstruct guards in each service's JwtService and
# the InternalAuthFilter constructors; unanchored so a placeholder anywhere in
# the value is rejected.
$script:HishobPlaceholderPattern = '(?i)(your-|replace-with|changeme|change-me|placeholder|example-)'

# Environment variable names whose values are secrets, used to redact log tails.
$script:HishobSecretKeyPattern = '(?i)(password|secret|token|api[_-]?key)'

# Minimum length before a value is redacted, so short incidental substrings
# (e.g. a "token=true" style flag) are not mangled across a whole log file.
$script:HishobMinSecretLength = 8

<#
.SYNOPSIS
    Parses a service .env file into an ordered name -> value map.
.REMARK
    Returns $null when the file does not exist, so callers can distinguish
    "no environment file" from "environment file with no entries".
    Values are never written to the console by this function.
#>
function Read-HishobEnvFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return $null }

    $values = [ordered]@{}
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        if ($line -match '^\s*([^#][^=]+)\s*=\s*(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            if ($values.Contains($name)) { $values[$name] = $value } else { $values.Add($name, $value) }
        }
    }
    return $values
}

<#
.SYNOPSIS
    Validates a parsed .env map against the names a service requires.
.RETURN
    Array of problem strings. Messages name variables only - never values -
    so they are safe to print.
#>
function Find-HishobEnvProblem {
    param(
        [Parameter(Mandatory)][string]$ServiceName,
        [AllowNull()]$Values,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$RequiredNames
    )

    if ($null -eq $Values) {
        return @("$ServiceName : no services\$ServiceName\.env file")
    }

    $problems = @()
    foreach ($name in $RequiredNames) {
        if (-not $Values.Contains($name)) {
            $problems += "$ServiceName : $name is not set"
        }
        elseif ([string]::IsNullOrWhiteSpace([string]$Values[$name])) {
            $problems += "$ServiceName : $name is empty"
        }
        elseif ([string]$Values[$name] -match $script:HishobPlaceholderPattern) {
            $problems += "$ServiceName : $name still contains a template placeholder"
        }
    }
    return $problems
}

<#
.SYNOPSIS
    Extracts the secret values from a parsed .env map, for log redaction.
#>
function Get-HishobSecretValue {
    param([AllowNull()]$EnvValues)

    $secrets = @()
    if ($null -eq $EnvValues) { return $secrets }

    foreach ($name in @($EnvValues.Keys)) {
        if ([string]$name -match $script:HishobSecretKeyPattern) {
            $value = [string]$EnvValues[[string]$name]
            if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Length -ge $script:HishobMinSecretLength) {
                $secrets += $value
            }
        }
    }
    return $secrets
}

<#
.SYNOPSIS
    Replaces every known secret value in a block of text with [REDACTED].
.REMARK
    Longest values first so a secret that contains another is fully masked.
    Comparison is case-sensitive, matching how the credentials are compared.
#>
function Protect-HishobSecretText {
    param(
        [AllowNull()][string]$Text,
        [AllowNull()][AllowEmptyCollection()][string[]]$Secrets
    )

    if ([string]::IsNullOrEmpty($Text)) { return '' }

    $redacted = $Text
    $ordered = @($Secrets | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object { $_.Length } -Descending)
    foreach ($secret in $ordered) {
        $redacted = $redacted.Replace([string]$secret, '[REDACTED]')
    }
    return $redacted
}

<#
.SYNOPSIS
    PIDs listening on a TCP port, parsed from netstat output.
.REMARK
    Matches the local-address column only, so a remote endpoint on the same
    port cannot be mistaken for a listener, and 18081 cannot match a query for
    8081. Duplicates (IPv4 + IPv6 rows for one process) collapse to one PID.
#>
function Get-HishobListeningPid {
    param(
        [AllowNull()][AllowEmptyCollection()][string[]]$NetstatOutput,
        [Parameter(Mandatory)][int]$Port
    )

    $pids = @()
    foreach ($line in @($NetstatOutput)) {
        if ([string]$line -notmatch 'LISTENING') { continue }
        $columns = @(([string]$line -split '\s+') | Where-Object { $_ })
        if ($columns.Count -lt 5) { continue }
        if ($columns[1] -notmatch ":$Port`$") { continue }
        if ($columns[-1] -match '^\d+$') { $pids += [int]$columns[-1] }
    }
    return @($pids | Select-Object -Unique)
}

<#
.SYNOPSIS
    TCP connect probe with an explicit bound on the wait.
#>
function Test-HishobPortOpen {
    param(
        [Parameter(Mandatory)][int]$Port,
        [string]$ComputerName = '127.0.0.1',
        [int]$TimeoutSec = 2
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $wait = $client.BeginConnect($ComputerName, $Port, $null, $null)
        if (-not $wait.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds($TimeoutSec))) { return $false }
        $client.EndConnect($wait)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

<#
.SYNOPSIS
    HTTP probe that separates "answered" from "did not answer".
.RETURN
    [pscustomobject] with Reachable, StatusCode, Error.
.REMARK
    Uses HttpWebRequest because Invoke-WebRequest throws on 4xx in Windows
    PowerShell 5.1, which makes an authenticated or not-yet-mapped endpoint
    indistinguishable from a stopped service and burns the whole readiness
    budget. Any HTTP status - including 4xx - means the service is up.
#>
function Get-HishobHttpProbe {
    param(
        [Parameter(Mandatory)][string]$Url,
        [int]$TimeoutSec = 10
    )

    $result = [pscustomobject]@{ Reachable = $false; StatusCode = $null; Error = $null }
    try {
        $request = [System.Net.HttpWebRequest]::Create($Url)
        $request.Timeout = $TimeoutSec * 1000
        $request.AllowReadStreamBuffering = $false
        $response = $request.GetResponse()
        $result.StatusCode = [int]$response.StatusCode
        $result.Reachable = $true
        $response.Close()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($response) {
            $result.StatusCode = [int]$response.StatusCode
            $result.Reachable = $true
        } else {
            $result.Error = $_.Exception.Message
        }
    } catch {
        $result.Error = $_.Exception.Message
    }
    return $result
}

<#
.SYNOPSIS
    Waits for one service to become ready, or explains why it will not.
.RETURN
    [pscustomobject] with Result ('Ready', 'Exited', 'ExitedAfterBind',
    'PortClosedAfterBind', 'Timeout'), Seconds, StatusCode, Detail.
.REMARK
    The port is checked before the process, because a java.exe on PATH can be a
    stub that spawns the real JVM as a child: the tracked process may exit while
    the service it started is healthy. A process that has gone is only fatal when
    nothing is accepting connections.
    The soak after the first accepted connection catches a service that binds a
    port and then fails.
#>
function Wait-HishobService {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][int]$Port,
        [AllowNull()][System.Diagnostics.Process]$Process,
        [AllowNull()][string]$ProbeUrl,
        [Parameter(Mandatory)][int]$TimeoutSec,
        [int]$IntervalSec = 2,
        [int]$HttpTimeoutSec = 10,
        [int]$SoakSec = 3
    )

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $deadline = [TimeSpan]::FromSeconds($TimeoutSec)

    while ($watch.Elapsed -lt $deadline) {
        if (Test-HishobPortOpen -Port $Port -TimeoutSec 2) {
            if ([string]::IsNullOrWhiteSpace($ProbeUrl)) {
                return (Complete-HishobReadiness -Process $Process -Port $Port -Watch $watch -SoakSec $SoakSec -Result 'Ready' -StatusCode $null)
            }

            $probe = Get-HishobHttpProbe -Url $ProbeUrl -TimeoutSec $HttpTimeoutSec
            if ($probe.Reachable) {
                return (Complete-HishobReadiness -Process $Process -Port $Port -Watch $watch -SoakSec $SoakSec -Result 'Ready' -StatusCode $probe.StatusCode)
            }
        }

        if ($Process -and $Process.HasExited) {
            return [pscustomobject]@{
                Result     = 'Exited'
                Seconds    = [int]$watch.Elapsed.TotalSeconds
                StatusCode = $null
                Detail     = "process exited with code $($Process.ExitCode) before anything accepted connections on :$Port"
            }
        }

        Start-Sleep -Seconds $IntervalSec
    }

    $stillBooting = -not ($Process -and $Process.HasExited)
    if ($stillBooting) {
        $detail = "still not accepting connections on :$Port after ${TimeoutSec}s; process is alive, so this is slow startup rather than a crash"
    } else {
        $detail = "process exited and :$Port is not accepting connections after ${TimeoutSec}s"
    }
    return [pscustomobject]@{
        Result     = 'Timeout'
        Seconds    = [int]$watch.Elapsed.TotalSeconds
        StatusCode = $null
        Detail     = $detail
    }
}

function Complete-HishobReadiness {
    param(
        [AllowNull()][System.Diagnostics.Process]$Process,
        [int]$Port,
        [System.Diagnostics.Stopwatch]$Watch,
        [int]$SoakSec,
        [string]$Result,
        $StatusCode
    )

    Start-Sleep -Seconds $SoakSec
    if (Test-HishobPortOpen -Port $Port -TimeoutSec 2) {
        return [pscustomobject]@{
            Result     = $Result
            Seconds    = [int]$Watch.Elapsed.TotalSeconds
            StatusCode = $StatusCode
            Detail     = "accepting connections on :$Port"
        }
    }

    if ($Process -and $Process.HasExited) {
        return [pscustomobject]@{
            Result     = 'ExitedAfterBind'
            Seconds    = [int]$Watch.Elapsed.TotalSeconds
            StatusCode = $StatusCode
            Detail     = "bound :$Port then exited with code $($Process.ExitCode)"
        }
    }
    return [pscustomobject]@{
        Result     = 'PortClosedAfterBind'
        Seconds    = [int]$Watch.Elapsed.TotalSeconds
        StatusCode = $StatusCode
        Detail     = "bound :$Port then stopped accepting connections"
    }
}

<#
.SYNOPSIS
    Path to the java.exe that should run the jars, or $null when there is none.
.REMARK
    Prefers JAVA_HOME because the java.exe found through PATH can be the Oracle
    javapath stub, which execs a second java.exe as its child. Launching the stub
    means the launcher tracks a parent process while the child JVM owns the port.
#>
function Resolve-HishobJavaExe {
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME')
    if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
        $candidate = Join-Path $javaHome 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    }

    $onPath = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

<#
.SYNOPSIS
    Direct and indirect child process IDs of a process.
.REMARK
    Used to stop a service completely when java.exe is a stub in front of the
    real JVM. Only descendants of a process the caller started are returned.
#>
function Get-HishobChildProcessId {
    param([Parameter(Mandatory)][int]$ProcessId)

    $descendants = @()
    $children = @(Get-CimInstance -ClassName Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        $childPid = [int]$child.ProcessId
        $descendants += $childPid
        $descendants += @(Get-HishobChildProcessId -ProcessId $childPid)
    }
    return $descendants
}

<#
.SYNOPSIS
    Last lines of a service's log files, with every known secret masked.
#>
function Get-HishobLogTail {
    param(
        [AllowNull()][AllowEmptyCollection()][string[]]$Paths,
        [int]$Lines = 25,
        [AllowNull()][AllowEmptyCollection()][string[]]$Secrets
    )

    $chunks = @()
    foreach ($path in @($Paths)) {
        if ($path -and (Test-Path -LiteralPath $path)) {
            $tail = @(Get-Content -LiteralPath $path -Tail $Lines -ErrorAction SilentlyContinue)
            if ($tail.Count -gt 0) {
                $chunks += "--- $(Split-Path $path -Leaf) : last $($tail.Count) line(s) ---"
                $chunks += $tail
            }
        }
    }
    if ($chunks.Count -eq 0) { return '(no log output captured yet)' }
    return Protect-HishobSecretText -Text ($chunks -join [Environment]::NewLine) -Secrets $Secrets
}

<#
.SYNOPSIS
    Whether a PID is a HISHOB-owned JVM started from this repository.
.RETURN
    Boolean only. The command line is inspected but never returned or printed.
.REMARK
    Requires both the repository root and a HISHOB module name in the command
    line, so a same-named jar launched from another project is not claimed.
#>
function Test-HishobOwnedJava {
    param(
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][string]$RootPath,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$JarPhrases
    )

    $info = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if (-not $info) { return $false }
    if ($info.Name -notmatch '^(java|javaw)\.exe$') { return $false }
    $commandLine = [string]$info.CommandLine
    if ([string]::IsNullOrWhiteSpace($commandLine)) { return $false }

    $rootWithSlashes = $RootPath.TrimEnd('\', '/')
    $rootMatches = ($commandLine -replace '/', '\') -like "*$rootWithSlashes*"
    if (-not $rootMatches) { return $false }

    foreach ($phrase in $JarPhrases) {
        if ($commandLine -like "*$phrase*") { return $true }
    }
    return $false
}

<#
.SYNOPSIS
    Writes the stack state file: one entry per service started and tracked.
.REMARK
    Records PIDs so a later stop can act on ownership rather than on a sweep of
    every java process. Contains no credential values.
#>
function Write-HishobStackState {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RootPath,
        [AllowEmptyCollection()][array]$Entries
    )

    $payload = [pscustomobject]@{
        writtenAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        root      = $RootPath
        services  = @($Entries)
    }
    $payload | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Path -Encoding ASCII
}

<#
.SYNOPSIS
    Reads the stack state file, or returns an empty array when absent/damaged.
#>
function Read-HishobStackState {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    try {
        $json = Get-Content -LiteralPath $Path -Raw -ErrorAction Stop
        if ([string]::IsNullOrWhiteSpace($json)) { return @() }
        $parsed = $json | ConvertFrom-Json
        if ($null -eq $parsed.services) { return @() }
        return @($parsed.services)
    } catch {
        Write-Host "  Stack state file is unreadable, falling back to port discovery: $($_.Exception.Message)" -ForegroundColor Yellow
        return @()
    }
}
