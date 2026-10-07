# ============================================================
#  stop-hishob.ps1
#  Stops all HISHOB backend Java processes.
#  Project root: D:\Projects\My projects\Hishob
# ============================================================

$hishobPorts = @(8888, 8761, 8081, 8082, 8083, 8084, 8085, 8086, 8080)

Write-Host ""
Write-Host "Stopping HISHOB backend services..." -ForegroundColor Cyan
Write-Host ""

$stopped = 0

foreach ($port in $hishobPorts) {
    # Find PIDs listening on this port
    $netstatLines = netstat -ano 2>$null | Where-Object { $_ -match "LISTENING" -and $_ -match ":$port\s" }
    foreach ($line in $netstatLines) {
        # Extract the PID (last column)
        $procId = ($line.Trim() -split '\s+')[-1]
        if ($procId -match '^\d+$') {
            $procId = [int]$procId
            try {
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                if ($proc -and $proc.ProcessName -match "java") {
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    Write-Host "  Stopped java PID $procId on port $port" -ForegroundColor Green
                    $stopped++
                }
            } catch {
                Write-Host "  Could not stop PID $procId on port $port : $_" -ForegroundColor Yellow
            }
        }
    }
}

# Also kill any java process whose cmdline references HISHOB JARs
$hishobJarPhrases = @("auth-service", "group-service", "expense-service", "balance-service", "settlement-service", "notification-service", "api-gateway", "config-server", "discovery-server")
$allJavaProcs = Get-Process java -ErrorAction SilentlyContinue
foreach ($jproc in $allJavaProcs) {
    try {
        $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $($jproc.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdline) {
            foreach ($phrase in $hishobJarPhrases) {
                if ($cmdline -like "*$phrase*") {
                    Stop-Process -Id $jproc.Id -Force -ErrorAction SilentlyContinue
                    Write-Host "  Stopped java PID $($jproc.Id) [$phrase]" -ForegroundColor Green
                    $stopped++
                    break
                }
            }
        }
    } catch { }
}

if ($stopped -eq 0) {
    Write-Host "  No HISHOB Java processes found." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "  Stopped $stopped service(s)." -ForegroundColor Green
}

Write-Host ""
