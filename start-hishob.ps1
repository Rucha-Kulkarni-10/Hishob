# ============================================================
#  start-hishob.ps1  –  HISHOB Backend Startup
#  Project root: D:\Projects\My projects\Hishob
#  Run from: anywhere (uses absolute paths)
# ============================================================

$ErrorActionPreference = "Stop"

$ROOT = "D:\Projects\My projects\Hishob"

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

function Wait-Port {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSec = 120
    )
    Write-Host "  Waiting for $Name on :$Port ..." -ForegroundColor Yellow
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", $Port)
            $tcp.Close()
            Write-OK "$Name ready on :$Port"
            return
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    Write-Fail "$Name did not become ready on :$Port within ${TimeoutSec}s"
    exit 1
}

function Wait-Http {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSec = 120
    )
    Write-Host "  Waiting for $Name at $Url ..." -ForegroundColor Yellow
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and $resp.StatusCode -lt 500) {
                Write-OK "$Name responded HTTP $($resp.StatusCode)"
                return
            }
        } catch { }
        Start-Sleep -Milliseconds 1000
    }
    Write-Fail "$Name did not respond at $Url within ${TimeoutSec}s"
    exit 1
}

# ── Launch helper – uses a temp .ps1 file per service ────────
# This avoids all quoting/escaping issues with spaces in paths and $ROOT expansion.
function Start-HishobService {
    param(
        [string]$Label,
        [string]$JarPath
    )
    $tmpScript = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
    $content = @"
`$env:DB_PASSWORD = 'Abc@1234'
`$env:DB_USERNAME = 'postgres'
`$env:DB_HOST     = 'localhost'
`$env:DB_PORT     = '5432'
Set-Location '$ROOT'
Write-Host "Starting: $Label" -ForegroundColor Cyan
java -jar "$JarPath"
"@
    Set-Content -Path $tmpScript -Value $content -Encoding UTF8
    Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmpScript
    Write-OK "Launched $Label window"
}

# ── JAR paths ────────────────────────────────────────────────
$jarConfigServer    = "$ROOT\infrastructure\config-server\target\config-server-0.0.1-SNAPSHOT.jar"
$jarDiscovery       = "$ROOT\infrastructure\discovery-server\target\discovery-server-0.0.1-SNAPSHOT.jar"
$jarAuth            = "$ROOT\services\auth-service\target\auth-service-0.0.1-SNAPSHOT.jar"
$jarGroup           = "$ROOT\services\group-service\target\group-service-0.0.1-SNAPSHOT.jar"
$jarExpense         = "$ROOT\services\expense-service\target\expense-service-0.0.1-SNAPSHOT.jar"
$jarBalance         = "$ROOT\services\balance-service\target\balance-service-0.0.1-SNAPSHOT.jar"
$jarSettlement      = "$ROOT\services\settlement-service\target\settlement-service-0.0.1-SNAPSHOT.jar"
$jarNotification    = "$ROOT\services\notification-service\target\notification-service-0.0.1-SNAPSHOT.jar"
$jarGateway         = "$ROOT\gateway\api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar"

$allJars = @{
    "Config Server"        = $jarConfigServer
    "Discovery Server"     = $jarDiscovery
    "Auth Service"         = $jarAuth
    "Group Service"        = $jarGroup
    "Expense Service"      = $jarExpense
    "Balance Service"      = $jarBalance
    "Settlement Service"   = $jarSettlement
    "Notification Service" = $jarNotification
    "API Gateway"          = $jarGateway
}

# ── Validate JARs ────────────────────────────────────────────
Write-Step "Validating JAR files..."
$missing = @()
foreach ($entry in $allJars.GetEnumerator()) {
    if (-not (Test-Path $entry.Value)) {
        $missing += $entry.Key
        Write-Fail "Missing JAR for $($entry.Key)"
        Write-Host "         Expected: $($entry.Value)" -ForegroundColor DarkRed
    } else {
        Write-OK "Found JAR: $($entry.Key)"
    }
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "  Build missing services first:" -ForegroundColor Red
    Write-Host "    cd `"$ROOT`"" -ForegroundColor Yellow
    Write-Host "    .\mvnw.cmd package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# ── Banner ───────────────────────────────────────────────────
Write-Host ""
Write-Host "  HISHOB Backend Startup" -ForegroundColor Magenta
Write-Host "  ======================" -ForegroundColor Magenta
Write-Host ""

# ════════════════════════════════════════════════════════════
# [1/9] Config Server — MUST run from $ROOT so the native
#       file: search-location (./infrastructure/config-server/config/)
#       resolves correctly.
# ════════════════════════════════════════════════════════════
Write-Step "[1/9] Starting Config Server :8888"
$tmp1 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp1 -Value @"
Set-Location '$ROOT'
Write-Host 'Starting Config Server...' -ForegroundColor Cyan
java -jar '$jarConfigServer'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp1
Wait-Port -Name "Config Server" -Port 8888 -TimeoutSec 120

# ── [2/9] Discovery Server (Eureka) ─────────────────────────
Write-Step "[2/9] Starting Discovery Server (Eureka) :8761"
$tmp2 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp2 -Value @"
Set-Location '$ROOT'
Write-Host 'Starting Discovery Server...' -ForegroundColor Cyan
java -jar '$jarDiscovery'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp2
Wait-Http -Name "Eureka" -Url "http://localhost:8761/" -TimeoutSec 120

# ── [3/9] Auth Service ───────────────────────────────────────
Write-Step "[3/9] Starting Auth Service :8081"
Start-HishobService -Label "Auth Service" -JarPath $jarAuth
Wait-Port -Name "Auth Service" -Port 8081 -TimeoutSec 120

# ── [4/9] Group Service ──────────────────────────────────────
Write-Step "[4/9] Starting Group Service :8082"
Start-HishobService -Label "Group Service" -JarPath $jarGroup
Wait-Port -Name "Group Service" -Port 8082 -TimeoutSec 120

# ── [5/9] Expense Service ────────────────────────────────────
Write-Step "[5/9] Starting Expense Service :8083"
Start-HishobService -Label "Expense Service" -JarPath $jarExpense
Wait-Port -Name "Expense Service" -Port 8083 -TimeoutSec 120

# ── [6/9] Balance Service ────────────────────────────────────
Write-Step "[6/9] Starting Balance Service :8084"
Start-HishobService -Label "Balance Service" -JarPath $jarBalance
Wait-Port -Name "Balance Service" -Port 8084 -TimeoutSec 120

# ── [7/9] Settlement Service ─────────────────────────────────
Write-Step "[7/9] Starting Settlement Service :8085"
Start-HishobService -Label "Settlement Service" -JarPath $jarSettlement
Wait-Port -Name "Settlement Service" -Port 8085 -TimeoutSec 120

# ── [8/9] Notification Service ───────────────────────────────
Write-Step "[8/9] Starting Notification Service :8086"
Start-HishobService -Label "Notification Service" -JarPath $jarNotification
Wait-Port -Name "Notification Service" -Port 8086 -TimeoutSec 120

# ── [9/9] API Gateway ────────────────────────────────────────
Write-Step "[9/9] Starting API Gateway :8080"
$tmp9 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp9 -Value @"
Set-Location '$ROOT'
Write-Host 'Starting API Gateway...' -ForegroundColor Cyan
java -jar '$jarGateway'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp9
Wait-Port -Name "API Gateway" -Port 8080 -TimeoutSec 120

# ── Summary ──────────────────────────────────────────────────
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "       HISHOB BACKEND IS FULLY RUNNING        " -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Config Server    : http://localhost:8888" -ForegroundColor White
Write-Host "  Eureka           : http://localhost:8761" -ForegroundColor White
Write-Host "  API Gateway      : http://localhost:8080" -ForegroundColor White
Write-Host "  Auth Service     : http://localhost:8081" -ForegroundColor White
Write-Host "  Group Service    : http://localhost:8082" -ForegroundColor White
Write-Host "  Expense Service  : http://localhost:8083" -ForegroundColor White
Write-Host "  Balance Service  : http://localhost:8084" -ForegroundColor White
Write-Host "  Settlement Svc   : http://localhost:8085" -ForegroundColor White
Write-Host "  Notification Svc : http://localhost:8086" -ForegroundColor White
Write-Host ""
Write-Host "  Stop all services: .\stop-hishob.ps1" -ForegroundColor DarkGray
Write-Host ""