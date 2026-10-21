# ============================================================
#  start-hishob.ps1  –  HISHOB Backend Startup
#  Project root: derived from $PSScriptRoot
#  Run from: anywhere (resolves repository root automatically)
# ============================================================

$ErrorActionPreference = "Stop"

$ROOT = $PSScriptRoot

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
        [string]$JarPath,
        [string]$ServiceName  # e.g., "auth-service", "expense-service"
    )
    $tmpScript = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
    $content = @"
# Load .env file for this service if it exists
`$envFile = '$ROOT\services\$ServiceName\.env'
if (Test-Path `$envFile) {
    Write-Host "Loading environment from `$envFile" -ForegroundColor DarkGray
    Get-Content `$envFile | ForEach-Object {
        if (`$_ -match '^\s*([^#][^=]+)\s*=\s*(.*)$') {
            `$name = `$matches[1].Trim()
            `$value = `$matches[2].Trim()
            [Environment]::SetEnvironmentVariable(`$name, `$value, 'Process')
        }
    }
} else {
    Write-Host "[FAIL] Missing environment file: `$envFile" -ForegroundColor Red
    Write-Host "       This service requires a .env with DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD (and DB_SCHEMA where the service yml uses it)." -ForegroundColor Red
    Write-Host "       Startup aborted - no default credentials are used." -ForegroundColor Red
    exit 1
}
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
$jarGateway         = "$ROOT\gateway\api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar"

$allJars = @{
    "Config Server"        = $jarConfigServer
    "Discovery Server"     = $jarDiscovery
    "Auth Service"         = $jarAuth
    "Expense Service"      = $jarExpense
    "Settlement Service"   = $jarSettlement
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
# [1/6] Config Server — MUST run from $ROOT so the native
#       file: search-location (./infrastructure/config-server/config/)
#       resolves correctly.
# ════════════════════════════════════════════════════════════
Write-Step "[1/6] Starting Config Server :8888"
$tmp1 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp1 -Value @"
Set-Location '$ROOT'
Write-Host 'Starting Config Server...' -ForegroundColor Cyan
java -jar '$jarConfigServer'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp1
Wait-Port -Name "Config Server" -Port 8888 -TimeoutSec 120

# ── [2/6] Discovery Server (Eureka) ─────────────────────────
Write-Step "[2/6] Starting Discovery Server (Eureka) :8761"
$tmp2 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp2 -Value @"
Set-Location '$ROOT'
Write-Host 'Starting Discovery Server...' -ForegroundColor Cyan
java -jar '$jarDiscovery'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp2
Wait-Http -Name "Eureka" -Url "http://localhost:8761/" -TimeoutSec 120

# ── [3/6] Auth Service ───────────────────────────────────────
Write-Step "[3/6] Starting Auth Service :8081"
Start-HishobService -Label "Auth Service" -JarPath $jarAuth -ServiceName "auth-service"
Wait-Port -Name "Auth Service" -Port 8081 -TimeoutSec 120

# ── [4/6] Expense Service (includes Groups) ──────────────────
Write-Step "[4/6] Starting Expense Service :8083"
Start-HishobService -Label "Expense Service" -JarPath $jarExpense -ServiceName "expense-service"
Wait-Port -Name "Expense Service" -Port 8083 -TimeoutSec 120

# ── [5/6] Settlement Service (includes Balances) ─────────────
Write-Step "[5/6] Starting Settlement Service :8085"
Start-HishobService -Label "Settlement Service" -JarPath $jarSettlement -ServiceName "settlement-service"
Wait-Port -Name "Settlement Service" -Port 8085 -TimeoutSec 120

# ── [6/6] API Gateway ────────────────────────────────────────
Write-Step "[6/6] Starting API Gateway :8087"
$tmp9 = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.ps1'
Set-Content -Path $tmp9 -Value @"
`$env:PORT = '8087'
Set-Location '$ROOT'
Write-Host 'Starting API Gateway...' -ForegroundColor Cyan
java -jar '$jarGateway'
"@ -Encoding UTF8
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $tmp9
Wait-Port -Name "API Gateway" -Port 8087 -TimeoutSec 120

# ── Summary ──────────────────────────────────────────────────
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "       HISHOB BACKEND IS FULLY RUNNING        " -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Consolidated 3-domain architecture:" -ForegroundColor Cyan
Write-Host "    • Auth Service (authentication, users, friendships)" -ForegroundColor DarkGray
Write-Host "    • Expense Service (groups, expenses, analytics)" -ForegroundColor DarkGray
Write-Host "    • Settlement Service (balances, settlements)" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Config Server    : http://localhost:8888" -ForegroundColor White
Write-Host "  Eureka           : http://localhost:8761" -ForegroundColor White
Write-Host "  API Gateway      : http://localhost:8087" -ForegroundColor White
Write-Host "  Auth Service     : http://localhost:8081" -ForegroundColor White
Write-Host "  Expense Service  : http://localhost:8083  (includes Groups)" -ForegroundColor White
Write-Host "  Settlement Svc   : http://localhost:8085  (includes Balances)" -ForegroundColor White
Write-Host "  Notifications    : served by Auth Service :8081" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Stop all services: .\stop-hishob.ps1" -ForegroundColor DarkGray
Write-Host ""