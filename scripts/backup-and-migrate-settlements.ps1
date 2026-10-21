# ============================================================================
#  backup-and-migrate-settlements.ps1
#  Safe Backup and Migration Runner for HISHOB Settlement Service Database
# ============================================================================

[CmdletBinding()]
param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = "postgres",
    [string]$DbName = "hishob_settlement",
    [string]$DbPassword = ""
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
$repoRoot = (Resolve-Path "$scriptDir\..").Path
$migrationFile = "$repoRoot\migrations\settlement-service\V1__allow_null_group_id_in_settlements.sql"
$backupDir = "$repoRoot\backups"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " HISHOB Settlement Database Backup & Migration" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Locate pg_dump and psql
$pgBin = $null
$possiblePaths = @(
    "C:\Program Files\PostgreSQL\18\bin",
    "C:\Program Files\PostgreSQL\17\bin",
    "C:\Program Files\PostgreSQL\16\bin",
    "C:\Program Files\PostgreSQL\15\bin"
)
foreach ($p in $possiblePaths) {
    if (Test-Path "$p\psql.exe") {
        $pgBin = $p
        break
    }
}

if (-not $pgBin) {
    # Check PATH
    $psqlCmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($psqlCmd) {
        $pgBin = Split-Path $psqlCmd.Source
    } else {
        Write-Error "PostgreSQL tools (psql.exe, pg_dump.exe) not found. Please install PostgreSQL client tools or ensure they are on PATH."
        exit 1
    }
}

$psqlExe = "$pgBin\psql.exe"
$pgDumpExe = "$pgBin\pg_dump.exe"

# 2. Check Password securely (never printed or logged)
if (-not $env:PGPASSWORD) {
    if ($DbPassword) {
        $env:PGPASSWORD = $DbPassword
    } elseif ([Environment]::UserInteractive) {
        $securePass = Read-Host -Prompt "Enter PostgreSQL password for user '$DbUser'" -AsSecureString
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
        $env:PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    } else {
        Write-Error "Database password must be provided via PGPASSWORD environment variable or -DbPassword parameter."
        exit 1
    }
}

# 3. Create Backup Directory
if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = "$backupDir\hishob_settlement_backup_$timestamp.sql"

Write-Host "`n[Step 1/3] Creating backup of '$DbName'..." -ForegroundColor Yellow
Write-Host "  Destination: $backupFile" -ForegroundColor Gray

if (Test-Path $backupFile) {
    Remove-Item $backupFile -Force
}

& $pgDumpExe -h $DbHost -p $DbPort -U $DbUser -d $DbName -F p -f $backupFile
$pgDumpExitCode = $LASTEXITCODE

if ($pgDumpExitCode -ne 0) {
    Write-Error "Backup failed! pg_dump exited with error code $pgDumpExitCode. Migration aborted. Database was NOT modified."
    if (Test-Path $backupFile) { Remove-Item $backupFile -Force }
    exit 1
}

if (-not (Test-Path $backupFile)) {
    Write-Error "Backup failed! Backup file was not created. Migration aborted. Database was NOT modified."
    exit 1
}

$backupItem = Get-Item $backupFile
$backupSize = $backupItem.Length

if ($backupSize -le 50) {
    Write-Error "Backup failed! Backup file is empty or invalid (size: $backupSize bytes). Migration aborted. Database was NOT modified."
    Remove-Item $backupFile -Force
    exit 1
}

$backupHeader = Get-Content -Path $backupFile -TotalCount 10 -ErrorAction SilentlyContinue | Out-String
if ($backupHeader -notmatch "PostgreSQL database dump") {
    Write-Error "Backup verification failed! Backup file does not contain a valid PostgreSQL dump header. Migration aborted. Database was NOT modified."
    exit 1
}

Write-Host "  Backup completed and validated successfully ($backupSize bytes)." -ForegroundColor Green

# 4. Apply Migration with atomic transaction and immediate stop on error
Write-Host "`n[Step 2/3] Applying migration '$migrationFile'..." -ForegroundColor Yellow
if (-not (Test-Path $migrationFile)) {
    Write-Error "Migration file not found: $migrationFile. Migration aborted."
    exit 1
}

& $psqlExe -h $DbHost -p $DbPort -U $DbUser -d $DbName -v "ON_ERROR_STOP=1" -f $migrationFile
$psqlExitCode = $LASTEXITCODE

if ($psqlExitCode -ne 0) {
    Write-Error "Migration execution failed with exit code $psqlExitCode! Transaction was rolled back by PostgreSQL. Inspect errors and restore from $backupFile if necessary."
    exit 1
}
Write-Host "  Migration applied successfully." -ForegroundColor Green

# 5. Verify Updated Nullability of group_id column
Write-Host "`n[Step 3/3] Verifying settlements.group_id nullability in '$DbName'..." -ForegroundColor Yellow
$verifySql = "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'settlements' AND column_name = 'group_id';"
$isNullable = (& $psqlExe -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -A -c $verifySql).Trim()
$verifyExitCode = $LASTEXITCODE

if ($verifyExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($isNullable)) {
    Write-Error "Verification failed! Could not retrieve nullability for 'group_id' in '$DbName'."
    exit 1
}

Write-Host "  Retrieved settlements.group_id is_nullable: $isNullable" -ForegroundColor Gray

if ($isNullable -ne "YES") {
    Write-Error "Nullability verification FAILED! Expected is_nullable = 'YES', got '$isNullable'."
    exit 1
}

Write-Host "  Verification PASSED: settlements.group_id is now nullable (is_nullable = YES)." -ForegroundColor Green

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " Migration and Verification Completed Successfully!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
