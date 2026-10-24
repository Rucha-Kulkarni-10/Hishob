#Requires -Version 5.1
<#
.SYNOPSIS
    Focused tests for scripts/hishob-startup-lib.ps1.
.DESCRIPTION
    Plain assertion harness (the repository has no Pester convention). Prints one
    line per case and exits non-zero if any case fails, so it can gate a run.

    Every classification branch is exercised from both sides - a check that can
    only observe success is not treated as a test. Process handling is exercised
    against real short-lived and long-lived processes, not stubs.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\test-hishob-startup-lib.ps1
#>

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hishob-startup-lib.ps1')

$script:Pass = 0
$script:Fail = 0
$script:Skipped = 0

function Assert-Equal {
    param([string]$Label, $Expected, $Actual)
    if ($null -eq $Expected -and $null -eq $Actual) { $script:Pass++; Write-Host "  PASS  $Label" -ForegroundColor Green; return }
    if ("$Expected" -eq "$Actual") { $script:Pass++; Write-Host "  PASS  $Label" -ForegroundColor Green }
    else { $script:Fail++; Write-Host "  FAIL  $Label : expected [$Expected] actual [$Actual]" -ForegroundColor Red }
}

function Assert-True {
    param([string]$Label, [bool]$Condition)
    if ($Condition) { $script:Pass++; Write-Host "  PASS  $Label" -ForegroundColor Green }
    else { $script:Fail++; Write-Host "  FAIL  $Label : expected true" -ForegroundColor Red }
}

function Assert-False {
    param([string]$Label, [bool]$Condition)
    if (-not $Condition) { $script:Pass++; Write-Host "  PASS  $Label" -ForegroundColor Green }
    else { $script:Fail++; Write-Host "  FAIL  $Label : expected false" -ForegroundColor Red }
}

function Assert-NotSame {
    param([string]$Label, [string]$Needle, [string]$Haystack)
    if ($Haystack -notlike "*$Needle*") { $script:Pass++; Write-Host "  PASS  $Label" -ForegroundColor Green }
    else { $script:Fail++; Write-Host "  FAIL  $Label : value leaked into output" -ForegroundColor Red }
}

# Cleans up after the cases that launch real processes, including the children
# those processes started (cmd.exe spawns ping.exe, a java.exe stub spawns a JVM).
function Stop-TestProcessTree {
    param([AllowNull()][System.Diagnostics.Process]$Process)
    if (-not $Process) { return }
    foreach ($childPid in @(Get-HishobChildProcessId -ProcessId $Process.Id)) {
        Stop-Process -Id $childPid -Force -ErrorAction SilentlyContinue
    }
    if (-not $Process.HasExited) { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue }
}

$work = Join-Path $env:TEMP "hishob-startup-lib-tests-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')"
New-Item -ItemType Directory -Path $work -Force | Out-Null

try {
    # ── 1. Read-HishobEnvFile ───────────────────────────────────────────
    Write-Host "`n[1] Read-HishobEnvFile" -ForegroundColor Cyan
    $envPath = Join-Path $work 'sample.env'
    @(
        '# a comment line'
        ''
        'DB_PASSWORD=Sup3rS3cretSentinel'
        'JDBC_URL=jdbc:postgresql://localhost:5432/hishob_db?currentSchema=public'
        'JWT_SECRET=    spaced_value_here   '
        'EMPTY_VALUE='
        "WINDOWS_CR=`r"
    ) | Set-Content -LiteralPath $envPath -Encoding ASCII

    $map = Read-HishobEnvFile -Path $envPath
    Assert-True 'returns an ordered map, not an unrolled collection' ($map -is [System.Collections.Specialized.OrderedDictionary])
    Assert-True 'comment line ignored' (-not $map.Contains('a comment line'))
    Assert-Equal 'value containing = preserved whole' 'jdbc:postgresql://localhost:5432/hishob_db?currentSchema=public' ([string]$map['JDBC_URL'])
    Assert-Equal 'surrounding spaces trimmed' 'spaced_value_here' ([string]$map['JWT_SECRET'])
    Assert-Equal 'empty value parsed as empty' '' ([string]$map['EMPTY_VALUE'])
    Assert-Equal 'secret value parsed' 'Sup3rS3cretSentinel' ([string]$map['DB_PASSWORD'])
    Assert-True 'missing file returns null' ($null -eq (Read-HishobEnvFile -Path (Join-Path $work 'absent.env')))

    # ── 2. Find-HishobEnvProblem ────────────────────────────────────────
    Write-Host "`n[2] Find-HishobEnvProblem (fail-closed validation)" -ForegroundColor Cyan
    $required = @('DB_PASSWORD', 'JWT_SECRET', 'INTERNAL_SERVICE_TOKEN')

    $clean = [ordered]@{ DB_PASSWORD = 'Sup3rS3cretSentinel'; JWT_SECRET = 'anotherRealValue123'; INTERNAL_SERVICE_TOKEN = 'thirdRealValue456789' }
    Assert-Equal 'complete env produces zero problems' 0 (@(Find-HishobEnvProblem -ServiceName 'auth-service' -Values $clean -RequiredNames $required).Count)

    $missingKey = [ordered]@{ DB_PASSWORD = 'Sup3rS3cretSentinel'; JWT_SECRET = 'anotherRealValue123' }
    $problems = @(Find-HishobEnvProblem -ServiceName 'auth-service' -Values $missingKey -RequiredNames $required)
    Assert-Equal 'absent key reported' 1 $problems.Count
    Assert-True 'absent key names the variable' ($problems[0] -like '*INTERNAL_SERVICE_TOKEN is not set')

    $blank = [ordered]@{ DB_PASSWORD = 'Sup3rS3cretSentinel'; JWT_SECRET = '   '; INTERNAL_SERVICE_TOKEN = 'thirdRealValue456789' }
    $problems = @(Find-HishobEnvProblem -ServiceName 'auth-service' -Values $blank -RequiredNames $required)
    Assert-Equal 'blank value reported' 1 $problems.Count
    Assert-True 'blank value labelled empty' ($problems[0] -like '*JWT_SECRET is empty')

    $placeholder = [ordered]@{ DB_PASSWORD = 'Sup3rS3cretSentinel'; JWT_SECRET = 'your-secret-here'; INTERNAL_SERVICE_TOKEN = 'prefix-CHANGE-ME-suffix' }
    $problems = @(Find-HishobEnvProblem -ServiceName 'auth-service' -Values $placeholder -RequiredNames $required)
    Assert-Equal 'two placeholders reported' 2 $problems.Count
    Assert-True 'placeholder match is unanchored' (($problems | Where-Object { $_ -like '*prefix-CHANGE-ME-suffix*' -or $_ -like '*INTERNAL_SERVICE_TOKEN still contains*'}).Count -eq 1)

    $noFile = @(Find-HishobEnvProblem -ServiceName 'group-service' -Values $null -RequiredNames $required)
    Assert-Equal 'missing env file reported as one problem' 1 $noFile.Count
    Assert-True 'missing env file names the service' ($noFile[0] -like 'group-service : no services*')

    $joined = (@(Find-HishobEnvProblem -ServiceName 'auth-service' -Values $placeholder -RequiredNames $required) -join ' | ')
    Assert-NotSame 'no credential value ever printed in a problem message' 'Sup3rS3cretSentinel' $joined

    # ── 3. Get-HishobSecretValue + Protect-HishobSecretText ─────────────
    Write-Host "`n[3] Secret extraction and log redaction" -ForegroundColor Cyan
    $envMap = [ordered]@{
        DB_HOST               = 'localhost'
        DB_PASSWORD           = 'passwordValueA'
        MAILGUN_API_KEY       = 'mailgunKeyValueB'
        JWT_SECRET            = 'jwtSecretValueC'
        INTERNAL_SERVICE_TOKEN = 'internalTokenValueD'
        SHORT_FLAG            = 'tiny'
    }
    $secrets = @(Get-HishobSecretValue -EnvValues $envMap)
    Assert-Equal 'four secret-bearing values collected' 4 $secrets.Count
    Assert-False 'non-secret key excluded' ($secrets -contains 'localhost')
    Assert-False 'value below minimum length excluded' ($secrets -contains 'tiny')

    $logLine = "Connecting to jdbc:postgresql://localhost:5432/hishob_db with passwordValueA and mailgunKeyValueB"
    $redacted = Protect-HishobSecretText -Text $logLine -Secrets $secrets
    Assert-False 'secret 1 removed from log text' ($redacted -like '*passwordValueA*')
    Assert-False 'secret 2 removed from log text' ($redacted -like '*mailgunKeyValueB*')
    Assert-True 'redaction marker present' ($redacted -like '*[REDACTED]*')
    Assert-True 'diagnostic context preserved' ($redacted -like '*Connecting to jdbc:postgresql*' -and $redacted -like '*hishob_db*')
    $untouched = Protect-HishobSecretText -Text 'Started AuthServiceApplication in 18.3 seconds' -Secrets $secrets
    Assert-Equal 'text without secrets unchanged (negative control)' 'Started AuthServiceApplication in 18.3 seconds' $untouched

    $overlap = @('abc123456789', 'abc123456789XYZ')
    $redacted = Protect-HishobSecretText -Text 'token=abc123456789XYZ tail' -Secrets $overlap
    Assert-False 'longer secret masked before its own prefix (no residue)' ($redacted -like '*abc123*')

    # ── 4. Get-HishobListeningPid ───────────────────────────────────────
    Write-Host "`n[4] Get-HishobListeningPid (netstat parsing)" -ForegroundColor Cyan
    $lines = @(
        '  Proto Local Address          Foreign Address        State           PID'
        '  TCP    0.0.0.0:8081           0.0.0.0:0              LISTENING       111'
        '  TCP    [::]:8081              [::]:0                 LISTENING       111'
        '  TCP    0.0.0.0:18081          0.0.0.0:0              LISTENING       222'
        '  TCP    0.0.0.0:9999           127.0.0.1:8081         ESTABLISHED     333'
        '  TCP    127.0.0.1:52000        93.184.216.34:8081     ESTABLISHED     444'
        '  TCP    0.0.0.0:8085           0.0.0.0:0              LISTENING       notanumber'
    )
    $pids = @(Get-HishobListeningPid -NetstatOutput $lines -Port 8081)
    Assert-Equal 'one pid after IPv4/IPv6 collapse' 1 $pids.Count
    Assert-Equal 'correct pid found' 111 $pids[0]
    Assert-Equal 'adjacent port 18081 not matched for 8081' 0 (@(Get-HishobListeningPid -NetstatOutput $lines -Port 8081 | Where-Object { $_ -eq 222 }).Count)
    Assert-Equal 'pid found only via remote address not matched' 0 (@(Get-HishobListeningPid -NetstatOutput $lines -Port 8081 | Where-Object { $_ -eq 444 }).Count)
    Assert-Equal 'established row not treated as listener' 0 (@(Get-HishobListeningPid -NetstatOutput $lines -Port 9999).Count)
    Assert-Equal 'non-numeric pid column ignored' 0 (@(Get-HishobListeningPid -NetstatOutput $lines -Port 8085).Count)
    Assert-Equal 'port with no listener yields empty' 0 (@(Get-HishobListeningPid -NetstatOutput $lines -Port 7777).Count)
    Assert-Equal 'null input yields empty' 0 (@(Get-HishobListeningPid -NetstatOutput $null -Port 8081).Count)
    $real = @(Get-HishobListeningPid -NetstatOutput (@(netstat -ano)) -Port 8081)
    Assert-True 'live netstat parse returns an int array (empty stack => 0, and 0 is a real answer here)' ($real -is [int[]] -or $real.Count -eq 0)

    # ── 5. Test-HishobPortOpen + Get-HishobHttpProbe ────────────────────
    Write-Host "`n[5] Port and HTTP probes" -ForegroundColor Cyan
    # Two ports: one toggled open/closed by this runspace, one served by the
    # responder. Closed listening sockets leave their accepted connections in
    # TIME_WAIT, so the HTTP probe gets a port of its own.
    function New-FreeLoopbackPort {
        $tmp = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
        $tmp.Start()
        $port = ([System.Net.IPEndPoint]$tmp.LocalEndpoint).Port
        $tmp.Stop()
        return $port
    }
    $freePort = New-FreeLoopbackPort
    $httpPort = New-FreeLoopbackPort

    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $freePort)
    try {
        Assert-False 'closed port reports not open (negative control)' (Test-HishobPortOpen -Port $freePort -TimeoutSec 2)
        $listener.Start()
        Assert-True 'open port reports open' (Test-HishobPortOpen -Port $freePort -TimeoutSec 2)
    } finally {
        $listener.Stop()
    }

    # Minimal HTTP responder on a second runspace, so the probe is a real client
    # round-trip against a real socket. It accepts exactly two connections (the
    # bind handshake, then the probe) and releases the port, which also makes the
    # refused case below real rather than assumed.
    $responder = {
        param($port, $status, $connections)
        $server = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
        $server.Start()
        try {
            for ($i = 0; $i -lt $connections; $i++) {
                try {
                    $client = $server.AcceptTcpClient()
                    $stream = $client.GetStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $null = $reader.ReadLine()
                    $body = 'fixture'
                    $head = "HTTP/1.1 $status X`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n$body"
                    $bytes = [System.Text.Encoding]::ASCII.GetBytes($head)
                    $stream.Write($bytes, 0, $bytes.Length)
                    $client.Close()
                } catch { }
            }
        } finally {
            $server.Stop()
        }
    }
    $runner = [powershell]::Create()
    $null = $runner.AddScript($responder).AddArgument($httpPort).AddArgument('404 Not Found').AddArgument(2)
    $async = $runner.BeginInvoke()
    try {
        $bound = $false
        for ($i = 0; $i -lt 40 -and -not $bound; $i++) {
            $bound = Test-HishobPortOpen -Port $httpPort -TimeoutSec 1
            if (-not $bound) { Start-Sleep -Milliseconds 250 }
        }
        Assert-True 'responder runspace bound its port' $bound

        $http = Get-HishobHttpProbe -Url "http://127.0.0.1:$httpPort/" -TimeoutSec 10
        Assert-True 'HTTP 404 counted as reachable (the readiness bug this fixes)' ($http.Reachable -eq $true)
        Assert-Equal '404 status surfaced' 404 $http.StatusCode

        # Let the responder finish and release the port, so the next probe meets
        # a genuine refusal rather than a silent timeout.
        $null = $async.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds(20))

        $refused = Get-HishobHttpProbe -Url "http://127.0.0.1:$httpPort/" -TimeoutSec 3
        Assert-False 'connection refused is NOT reachable (negative control)' $refused.Reachable
        Assert-True 'refused probe carries an error string' (-not [string]::IsNullOrWhiteSpace($refused.Error))
    } finally {
        $null = $async.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds(20))
        $runner.Dispose()
    }

    # ── 6. Wait-HishobService classification ────────────────────────────
    Write-Host "`n[6] Wait-HishobService (real processes, all outcomes)" -ForegroundColor Cyan
    $deadExe = Join-Path $env:WINDIR 'System32\cmd.exe'
    $crasher = Start-Process -FilePath $deadExe -ArgumentList '/c', 'exit 7' -WindowStyle Hidden -PassThru
    $result = Wait-HishobService -Name 'Crasher' -Port $freePort -Process $crasher -TimeoutSec 60 -IntervalSec 1 -SoakSec 1
    Assert-Equal 'process that exits is reported as Exited' 'Exited' $result.Result
    Assert-True 'exit code surfaced in detail' ($result.Detail -like '*code 7*')
    Assert-True 'crash detected fast, not after the full budget' ($result.Seconds -lt 20)

    $sleeper = Start-Process -FilePath $deadExe -ArgumentList '/c', 'ping -n 60 127.0.0.1 >nul' -WindowStyle Hidden -PassThru
    try {
        $result = Wait-HishobService -Name 'Sleeper' -Port $freePort -Process $sleeper -TimeoutSec 4 -IntervalSec 1 -SoakSec 1
        Assert-Equal 'live process that never binds times out' 'Timeout' $result.Result
        Assert-True 'timeout detail says slow startup, not crash' ($result.Detail -like '*slow startup rather than a crash*')
        Assert-False 'sleeper still alive => readiness did not kill it' $sleeper.HasExited
    } finally {
        Stop-TestProcessTree -Process $sleeper
    }

    $readyListener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $freePort)
    $readyListener.Start()
    $readyProc = Start-Process -FilePath $deadExe -ArgumentList '/c', 'ping -n 30 127.0.0.1 >nul' -WindowStyle Hidden -PassThru
    try {
        $result = Wait-HishobService -Name 'Ready' -Port $freePort -Process $readyProc -TimeoutSec 20 -IntervalSec 1 -SoakSec 1
        Assert-Equal 'bound port + alive process is Ready' 'Ready' $result.Result
        Assert-True 'readiness detail names the port' ($result.Detail -like "*:$freePort*")
        Assert-False 'Ready path leaves the tracked process running' $readyProc.HasExited
    } finally {
        Stop-TestProcessTree -Process $readyProc
        $readyListener.Stop()
    }

    # Stub semantics: java.exe on PATH can be a launcher whose child is the JVM
    # that owns the port, so an exited tracked process must not hide a service
    # that is listening. The listener lives in another runspace, exactly as the
    # JVM lives in another process.
    $stubPort = New-FreeLoopbackPort
    $holdPort = {
        param($port)
        $server = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
        $server.Start()
        Start-Sleep -Seconds 60
        $server.Stop()
    }
    $holder = [powershell]::Create()
    $null = $holder.AddScript($holdPort).AddArgument($stubPort)
    $holderAsync = $holder.BeginInvoke()
    $gone = $null
    try {
        $gone = Start-Process -FilePath $deadExe -ArgumentList '/c', 'exit 5' -WindowStyle Hidden -PassThru
        $null = $gone.WaitForExit(15000)
        Assert-True 'tracked process really has exited' $gone.HasExited

        $bound = $false
        for ($i = 0; $i -lt 40 -and -not $bound; $i++) {
            $bound = Test-HishobPortOpen -Port $stubPort -TimeoutSec 1
            if (-not $bound) { Start-Sleep -Milliseconds 250 }
        }
        Assert-True 'the port holder is listening' $bound

        $result = Wait-HishobService -Name 'Stub' -Port $stubPort -Process $gone -TimeoutSec 20 -IntervalSec 1 -SoakSec 1
        Assert-Equal 'exited parent with a listening port is Ready, not Exited' 'Ready' $result.Result
    } finally {
        $null = $holder.Stop()
        $holder.Dispose()
        Stop-TestProcessTree -Process $gone
    }

    # A service that binds its port and then loses it must not be reported Ready.
    $flakyPort = New-FreeLoopbackPort
    $holdThenDrop = {
        param($port)
        $server = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
        $server.Start()
        Start-Sleep -Seconds 8
        $server.Stop()
        Start-Sleep -Seconds 60
    }
    $flaky = [powershell]::Create()
    $null = $flaky.AddScript($holdThenDrop).AddArgument($flakyPort)
    $flakyAsync = $flaky.BeginInvoke()
    $survivor = $null
    try {
        $survivor = Start-Process -FilePath $deadExe -ArgumentList '/c', 'ping -n 30 127.0.0.1 >nul' -WindowStyle Hidden -PassThru
        $result = Wait-HishobService -Name 'Flaky' -Port $flakyPort -Process $survivor -TimeoutSec 25 -IntervalSec 1 -SoakSec 8
        Assert-Equal 'port that closes during the soak is not Ready' 'PortClosedAfterBind' $result.Result
        Assert-True 'closed-port detail explains it' ($result.Detail -like '*stopped accepting connections*')
    } finally {
        $null = $flaky.Stop()
        $flaky.Dispose()
        Stop-TestProcessTree -Process $survivor
    }

    # ── 7. Test-HishobOwnedJava ─────────────────────────────────────────
    Write-Host "`n[7] Test-HishobOwnedJava (ownership scoping)" -ForegroundColor Cyan
    $phrases = @('auth-service', 'expense-service', 'api-gateway')
    $root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

    $ownPid = $PID
    Assert-False 'own powershell PID is not a HISHOB java process' (Test-HishobOwnedJava -ProcessId $ownPid -RootPath $root -JarPhrases $phrases)
    Assert-False 'unknown PID reported as not owned' (Test-HishobOwnedJava -ProcessId 999999 -RootPath $root -JarPhrases $phrases)

    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $javac) {
        $script:Skipped++
        Write-Host "  SKIP  java ownership cases: javac not on PATH" -ForegroundColor Yellow
    } else {
        $src = Join-Path $work 'HishobDecoySleeper.java'
        Set-Content -LiteralPath $src -Encoding ASCII -Value @(
            'public class HishobDecoySleeper {',
            '  public static void main(String[] a) throws Exception { Thread.sleep(120000); }',
            '}'
        )
        $compile = & javac -d $work $src 2>&1
        Assert-Equal 'decoy class compiles' 0 $LASTEXITCODE

        # Foreign: a java process with no link to this repository.
        $foreign = Start-Process -FilePath 'java.exe' -ArgumentList '-cp', $work, 'HishobDecoySleeper', 'unrelated-project' -WindowStyle Hidden -PassThru
        # Owned-looking: same binary, command line names this repository and a HISHOB module.
        $claimed = Start-Process -FilePath 'java.exe' -ArgumentList '-cp', $work, 'HishobDecoySleeper', (Join-Path $root 'services\auth-service\target\auth-service.jar') -WindowStyle Hidden -PassThru
        Start-Sleep -Seconds 3
        try {
            Assert-True 'java process naming repo root + module is owned' (Test-HishobOwnedJava -ProcessId $claimed.Id -RootPath $root -JarPhrases $phrases)
            Assert-False 'java process from another directory is NOT owned (stop must spare it)' (Test-HishobOwnedJava -ProcessId $foreign.Id -RootPath $root -JarPhrases $phrases)
        } finally {
            foreach ($p in @($foreign, $claimed)) { Stop-TestProcessTree -Process $p }
        }
    }

    # ── 8. Stack state file round-trip ──────────────────────────────────
    Write-Host "`n[8] Stack state round-trip" -ForegroundColor Cyan
    $statePath = Join-Path $work 'stack-state.json'
    $entries = @([pscustomobject]@{ name = 'Auth Service'; port = 8081; pid = 4242; javaPids = @(4243, 4244); log = 'auth.log' })
    Write-HishobStackState -Path $statePath -RootPath $root -Entries $entries
    Assert-True 'state file written' (Test-Path -LiteralPath $statePath)
    $readBack = @(Read-HishobStackState -Path $statePath)
    Assert-Equal 'one service recorded' 1 $readBack.Count
    Assert-Equal 'pid survives the round-trip' 4242 $readBack[0].pid
    Assert-Equal 'jvm pids survive the round-trip' '4243 4244' (@($readBack[0].javaPids) -join ' ')
    Assert-Equal 'absent state file yields empty list' 0 (@(Read-HishobStackState -Path (Join-Path $work 'nope.json')).Count)
    $broken = Join-Path $work 'broken.json'
    Set-Content -LiteralPath $broken -Value '{ this is not json' -Encoding ASCII
    Assert-Equal 'damaged state file degrades to empty list' 0 (@(Read-HishobStackState -Path $broken).Count)
    Assert-NotSame 'state file holds no credential material' 'Sup3rS3cretSentinel' (Get-Content -LiteralPath $statePath -Raw)

    # ── 9. Resolve-HishobJavaExe ────────────────────────────────────────
    Write-Host "`n[9] Resolve-HishobJavaExe (JAVA_HOME beats the PATH stub)" -ForegroundColor Cyan
    $savedJavaHome = $env:JAVA_HOME
    try {
        $ambient = Resolve-HishobJavaExe
        Assert-True 'a java.exe is resolvable on this host' ($ambient -and (Test-Path -LiteralPath $ambient))

        $jdkHome = Split-Path (Split-Path $ambient -Parent) -Parent
        if (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\java.exe')) {
            $env:JAVA_HOME = $jdkHome
            Assert-Equal 'JAVA_HOME bin\java.exe is preferred' $ambient (Resolve-HishobJavaExe)
        } else {
            $script:Skipped++
            Write-Host "  SKIP  JAVA_HOME preference: the resolved java.exe is not under <home>\bin" -ForegroundColor Yellow
        }

        $env:JAVA_HOME = $root
        $fallback = Resolve-HishobJavaExe
        Assert-True 'JAVA_HOME without bin\java.exe falls back to PATH' ($fallback -and $fallback -ne (Join-Path $root 'bin\java.exe'))
        Assert-True 'fallback is an existing java.exe' (Test-Path -LiteralPath $fallback)
    } finally {
        $env:JAVA_HOME = $savedJavaHome
    }

    # ── 10. Get-HishobChildProcessId ────────────────────────────────────
    Write-Host "`n[10] Get-HishobChildProcessId (tree cleanup)" -ForegroundColor Cyan
    $treeParent = Start-Process -FilePath (Join-Path $env:WINDIR 'System32\cmd.exe') -ArgumentList '/c', 'ping -n 30 127.0.0.1 >nul' -WindowStyle Hidden -PassThru
    try {
        Start-Sleep -Seconds 2
        $children = @(Get-HishobChildProcessId -ProcessId $treeParent.Id)
        Assert-True 'a real spawned child is found' ($children.Count -ge 1)
        Assert-False 'the parent is not listed as its own child' ($children -contains $treeParent.Id)
    } finally {
        Stop-TestProcessTree -Process $treeParent
    }
    Assert-Equal 'a process that does not exist has no descendants' 0 (@(Get-HishobChildProcessId -ProcessId 999999).Count)
}
finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host ""
Write-Host "  passed=$($script:Pass) failed=$($script:Fail) skipped=$($script:Skipped)" -ForegroundColor ($(if ($script:Fail -gt 0) { 'Red' } else { 'Green' }))
if ($script:Fail -gt 0) { exit 1 }
exit 0
