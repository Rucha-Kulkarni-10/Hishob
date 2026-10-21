# ============================================================
#  test-mailgun.ps1  –  HISHOB Mailgun Manual Test Runner
#  Purpose: Send exactly ONE test email to verify Mailgun integration
#  Safety: Requires BOTH guards to be enabled explicitly
# ============================================================

$ErrorActionPreference = "Stop"

$ROOT = Split-Path -Parent $PSScriptRoot
$ENV_FILE = "$ROOT\services\auth-service\.env"

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

function Write-Warn([string]$msg) {
    Write-Host "  [WARN] $msg" -ForegroundColor Yellow
}

# ── Load .env file helper ────────────────────────────────────
function Load-EnvFile {
    param([string]$Path)
    
    if (-not (Test-Path $Path)) {
        Write-Fail ".env file not found: $Path"
        Write-Host ""
        Write-Host "  Create the file first:" -ForegroundColor Yellow
        Write-Host "    services/auth-service/.env" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Step "Loading environment from: $Path"
    
    $lineNum = 0
    Get-Content $Path | ForEach-Object {
        $lineNum++
        $line = $_.Trim()
        
        # Skip empty lines and comments
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }
        
        # Parse KEY=VALUE
        if ($line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            
            # Remove surrounding quotes if present
            if ($value -match '^"(.*)"$' -or $value -match "^'(.*)'$") {
                $value = $matches[1]
            }
            
            # Set environment variable for child process
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
            
            # Never print secret values
            if ($key -like "*KEY*" -or $key -like "*SECRET*" -or $key -like "*PASSWORD*") {
                Write-Host "  Loaded: $key=***" -ForegroundColor DarkGray
            } else {
                Write-Host "  Loaded: $key=$value" -ForegroundColor DarkGray
            }
        } else {
            Write-Warn "Skipping invalid line $lineNum : $line"
        }
    }
    
    Write-OK "Environment variables loaded"
}

# ── Validate required configuration ──────────────────────────
function Validate-MailgunConfig {
    Write-Step "Validating Mailgun configuration..."
    
    $required = @(
        "MAILGUN_ENABLED",
        "MAILGUN_API_KEY",
        "MAILGUN_DOMAIN",
        "MAILGUN_TEST_RECIPIENT",
        "MAILGUN_MANUAL_TEST_ENABLED"
    )
    
    $missing = @()
    $blank = @()
    
    foreach ($var in $required) {
        $value = [Environment]::GetEnvironmentVariable($var, "Process")
        
        if ($null -eq $value) {
            $missing += $var
        } elseif ($value.Trim() -eq "" -or 
                  $value -like "*your-mailgun-api-key*" -or 
                  $value -like "*your-authorized-email*" -or
                  $value -like "REPLACE_WITH*" -or
                  $value -like "REVOKED_KEY*" -or
                  $value -like "*@example.com") {
            $blank += $var
        } else {
            # Never print secret values
            if ($var -like "*KEY*" -or $var -like "*SECRET*") {
                Write-OK "$var is set (value hidden)"
            } else {
                Write-OK "$var = $value"
            }
        }
    }
    
    if ($missing.Count -gt 0) {
        Write-Host ""
        Write-Fail "Missing required environment variables:"
        foreach ($var in $missing) {
            Write-Host "    - $var" -ForegroundColor Red
        }
        exit 1
    }
    
    if ($blank.Count -gt 0) {
        Write-Host ""
        Write-Fail "Required variables are blank or contain placeholder values:"
        foreach ($var in $blank) {
            Write-Host "    - $var" -ForegroundColor Red
        }
        Write-Host ""
        Write-Host "  Update these values in:" -ForegroundColor Yellow
        Write-Host "    $ENV_FILE" -ForegroundColor Yellow
        exit 1
    }
}

# ── Check safety guards ──────────────────────────────────────
function Check-SafetyGuards {
    Write-Step "Checking safety guards..."
    
    $envEnabled = [Environment]::GetEnvironmentVariable("MAILGUN_MANUAL_TEST_ENABLED", "Process")
    
    if ($envEnabled -ne "true") {
        Write-Host ""
        Write-Fail "Manual test guard is NOT enabled"
        Write-Host ""
        Write-Host "  MAILGUN_MANUAL_TEST_ENABLED must be set to 'true' in:" -ForegroundColor Yellow
        Write-Host "    $ENV_FILE" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  This is a safety mechanism to prevent accidental email sends." -ForegroundColor DarkGray
        exit 1
    }
    
    Write-OK "Environment guard: MAILGUN_MANUAL_TEST_ENABLED=true"
    Write-OK "JVM guard: -Dmailgun.manual.test=true (will be set)"
    Write-Host ""
    Write-Host "  Both safety guards are enabled. Test will proceed." -ForegroundColor Green
}

# ── Run the test ─────────────────────────────────────────────
function Run-MailgunTest {
    Write-Step "Running MailgunManualTest..."
    Write-Host ""
    Write-Host "  Target: MailgunManualTest ONLY" -ForegroundColor White
    Write-Host "  Recipient: $([Environment]::GetEnvironmentVariable('MAILGUN_TEST_RECIPIENT', 'Process'))" -ForegroundColor White
    Write-Host "  Domain: $([Environment]::GetEnvironmentVariable('MAILGUN_DOMAIN', 'Process'))" -ForegroundColor White
    Write-Host ""
    
    # Change to project root
    Set-Location $ROOT
    
    # Run Maven test with both guards enabled
    $mvnArgs = @(
        "-pl", "services/auth-service",
        "test",
        "-Dtest=MailgunManualTest",
        "-Dmailgun.manual.test=true"
    )
    
    Write-Host "  Executing: .\mvnw.cmd $($mvnArgs -join ' ')" -ForegroundColor DarkGray
    Write-Host ""
    
    & .\mvnw.cmd @mvnArgs
    
    $exitCode = $LASTEXITCODE
    
    Write-Host ""
    if ($exitCode -eq 0) {
        Write-Host "  ============================================" -ForegroundColor Green
        Write-Host "       MAILGUN TEST COMPLETED SUCCESSFULLY   " -ForegroundColor Green
        Write-Host "  ============================================" -ForegroundColor Green
    } else {
        Write-Host "  ============================================" -ForegroundColor Red
        Write-Host "       MAILGUN TEST FAILED (Exit: $exitCode)  " -ForegroundColor Red
        Write-Host "  ============================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "  Common failure reasons:" -ForegroundColor Yellow
        Write-Host "    - HTTP 401: Invalid API key or wrong key type" -ForegroundColor Yellow
        Write-Host "    - HTTP 400: Recipient not authorized in sandbox" -ForegroundColor Yellow
        Write-Host "    - HTTP 403: Domain verification pending" -ForegroundColor Yellow
        Write-Host "    - Network error: Check internet connection" -ForegroundColor Yellow
    }
    
    exit $exitCode
}

# ── Main ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "  HISHOB Mailgun Manual Test Runner" -ForegroundColor Magenta
Write-Host "  ==================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "  WARNING: This will send ONE real email" -ForegroundColor Yellow
Write-Host "           to the configured test recipient." -ForegroundColor Yellow
Write-Host ""

Load-EnvFile -Path $ENV_FILE
Validate-MailgunConfig
Check-SafetyGuards
Run-MailgunTest
