#Requires -Version 5.1

<#
.SYNOPSIS
    Creates verified backups of all Hishob databases before migration.

.DESCRIPTION
    Comprehensive backup strategy:
    - Backs up all 6 Hishob databases
    - Verifies backup file integrity
    - Tests restore capability
    - Creates restore instructions

.PARAMETER PgDumpPath
    Path to pg_dump.exe executable

.PARAMETER PsqlPath
    Path to psql.exe executable

.PARAMETER Host
    Database host (default: localhost)

.PARAMETER Port
    Database port (default: 5432)

.PARAMETER Username
    Database username (default: postgres)

.PARAMETER BackupDir
    Directory for backup files (default: ./backups-TIMESTAMP)

.EXAMPLE
    .\backup-databases.ps1
    
.EXAMPLE
    .\backup-databases.ps1 -BackupDir "D:\Backups\Hishob"

.NOTES
    Version: 2.6 (Clean Summary)
    - Fixed: Total-size summary uses ForEach-Object key access (Measure-Object -Property
      cannot read hashtable keys under StrictMode)
    - Includes: pg_restore --list verification (2.5); stderr-safe native execution, zero-byte failure detection, cleanup
      reporting (2.4); -b removal, corrected restore docs, [ERROR] labels, -BackupDir
      overwrite guard (2.3)
    - Includes: hishob_db in default backup list
    - Includes: Millisecond timestamp prevents directory collisions
    - Includes: Restore errors captured and reported; test restore failure fails the run
    - Includes: Secure password handling from .env file
    - Includes: Comprehensive error handling
    - Includes: Verification and test restore
#>

[CmdletBinding()]
param(
    [string]$PgDumpPath = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$Username = "postgres",
    [string]$BackupDir = "",
    
    # Allow custom database list for testing clones
    [string[]]$Databases = @(
        "hishob_auth",
        "hishob_expense",
        "hishob_group",
        "hishob_settlement",
        "hishob_notification",
        "hishob_db"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================================
# CONFIGURATION
# ============================================================================

$ScriptDir = $PSScriptRoot

if ([string]::IsNullOrEmpty($BackupDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
    $BackupDir = Join-Path $ScriptDir "backups-$timestamp"
    
    # Additional safety: ensure unique directory name
    $counter = 0
    $originalBackupDir = $BackupDir
    while ((Test-Path $BackupDir) -and $counter -lt 100) {
        $counter++
        $BackupDir = "${originalBackupDir}-$counter"
    }
    
    if (Test-Path $BackupDir) {
        throw "Cannot create unique backup directory after 100 attempts"
    }
}
else {
    # Explicit -BackupDir: refuse to reuse a directory that already holds backups
    if (Test-Path $BackupDir) {
        $existingBackups = @(Get-ChildItem -Path $BackupDir -Filter "*.backup" -File -ErrorAction SilentlyContinue)
        if ($existingBackups.Count -gt 0) {
            throw "Refusing to use backup directory '$BackupDir': it already contains $($existingBackups.Count) .backup file(s). Choose a new directory so existing backups are not overwritten."
        }
    }
}

$LogFile = Join-Path $BackupDir "backup.log"

# Note: Databases parameter now passed as script parameter
# Default list: hishob_auth, hishob_expense, hishob_group, hishob_settlement, hishob_notification, hishob_db

# ============================================================================
# LOGGING
# ============================================================================

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    
    # Ensure log directory exists
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] [$Level] $Message"
    Write-Host $logMessage
    Add-Content -Path $LogFile -Value $logMessage
}

# ============================================================================
# PASSWORD RETRIEVAL
# ============================================================================

function Get-DatabasePassword {
    Write-Log "Reading database password from auth-service .env file"
    
    $envFile = Join-Path $ScriptDir "services\auth-service\.env"
    
    if (-not (Test-Path $envFile)) {
        throw "Auth service .env file not found at: $envFile"
    }
    
    $password = Get-Content $envFile | 
        Where-Object { $_ -match "^DB_PASSWORD\s*=\s*(.+)$" } | 
        ForEach-Object { $Matches[1].Trim() }
    
    if ([string]::IsNullOrEmpty($password)) {
        throw "DB_PASSWORD not found in $envFile"
    }
    
    Write-Log "Password retrieved successfully from .env file"
    return $password
}

# ============================================================================
# NATIVE COMMAND EXECUTION
# ============================================================================

# Windows PowerShell 5.1 converts native stderr (2>&1) into terminating errors
# when $ErrorActionPreference = 'Stop'. This helper runs a native command with
# non-terminating stderr capture and returns output + real exit code, so
# $LASTEXITCODE is the single pass/fail gate.
function Invoke-Native {
    param(
        [string]$Exe,
        [array]$Arguments
    )

    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Exe @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    return @{
        Output   = $output
        ExitCode = $code
    }
}

function Get-NativeErrorDetail {
    param($Result)
    ($Result.Output | Select-Object -Last 5 | Out-String).Trim()
}

# ============================================================================
# BACKUP FUNCTIONS
# ============================================================================

function Backup-Database {
    param(
        [string]$DatabaseName,
        [string]$Password
    )
    
    $backupFile = Join-Path $BackupDir "$DatabaseName.backup"
    
    Write-Log "Backing up $DatabaseName..."
    
    $env:PGPASSWORD = $Password
    
    try {
        $args = @(
            "-h", $DbHost,
            "-p", $Port,
            "-U", $Username,
            "-F", "c",  # Custom format (compressed)
            "-v",       # Verbose
            "-f", $backupFile,
            $DatabaseName
        )
        
        $dumpResult = Invoke-Native -Exe $PgDumpPath -Arguments $args
        foreach ($line in $dumpResult.Output) {
            Write-Log "  $line" -Level "DEBUG"
        }

        if ($dumpResult.ExitCode -ne 0) {
            throw "pg_dump failed for $DatabaseName with exit code $($dumpResult.ExitCode): $(Get-NativeErrorDetail $dumpResult)"
        }

        # Verify file was created
        if (-not (Test-Path $backupFile)) {
            throw "Backup file was not created: $backupFile"
        }

        $fileSize = (Get-Item $backupFile).Length
        if ($fileSize -eq 0) {
            throw "Backup file is zero bytes (failed dump): $backupFile"
        }
        $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
        
        Write-Log "[OK] Backup completed: $backupFile ($fileSizeMB MB)"
        
        return @{
            Database = $DatabaseName
            BackupFile = $backupFile
            SizeBytes = $fileSize
            SizeMB = $fileSizeMB
        }
    }
    finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Test-BackupIntegrity {
    param(
        [string]$BackupFile,
        [string]$Password
    )
    
    Write-Log "Verifying backup integrity: $(Split-Path $BackupFile -Leaf)"
    
    $env:PGPASSWORD = $Password
    
    try {
        $args = @(
            "-h", $DbHost,
            "-p", $Port,
            "-U", $Username,
            "--list",
            $BackupFile
        )
        
        # --list is a pg_restore option, not a pg_dump option
        $pgRestorePath = $PgDumpPath -replace 'pg_dump\.exe$', 'pg_restore.exe'

        if (-not (Test-Path $pgRestorePath)) {
            throw "pg_restore.exe not found at: $pgRestorePath"
        }

        $listResult = Invoke-Native -Exe $pgRestorePath -Arguments $args

        if ($listResult.ExitCode -ne 0) {
            throw "Backup verification failed with exit code $($listResult.ExitCode): $(Get-NativeErrorDetail $listResult)"
        }
        
        Write-Log "[OK] Backup is valid and readable"
        return $true
    }
    catch {
        Write-Log "[ERROR] Backup verification failed: $_" -Level "ERROR"
        return $false
    }
    finally {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}

function Test-BackupRestore {
    param(
        [string]$BackupFile,
        [string]$Password
    )
    
    $testDbName = "hishob_restore_test_$(Get-Random)"
    
    Write-Log "Testing restore capability with temporary database: $testDbName"
    
    $env:PGPASSWORD = $Password
    
    try {
        # Create test database
        Write-Log "  Creating test database..."
        $createArgs = @(
            "-h", $DbHost,
            "-p", $Port,
            "-U", $Username,
            "-d", "postgres",
            "-c", "CREATE DATABASE $testDbName;"
        )
        
        $createResult = Invoke-Native -Exe $PsqlPath -Arguments $createArgs

        if ($createResult.ExitCode -ne 0) {
            throw "Failed to create test database (exit code $($createResult.ExitCode)): $(Get-NativeErrorDetail $createResult)"
        }
        
        # Restore backup
        Write-Log "  Restoring backup to test database..."
        
        # Use pg_restore (not pg_dump) for restoring custom format backups
        $pgRestorePath = $PgDumpPath -replace 'pg_dump\.exe$', 'pg_restore.exe'
        
        if (-not (Test-Path $pgRestorePath)) {
            throw "pg_restore.exe not found at: $pgRestorePath"
        }
        
        $restoreArgs = @(
            "-h", $DbHost,
            "-p", $Port,
            "-U", $Username,
            "-d", $testDbName,
            "-v",
            $BackupFile
        )
        
        # Capture output for error reporting
        $restoreResult = Invoke-Native -Exe $pgRestorePath -Arguments $restoreArgs

        if ($restoreResult.ExitCode -ne 0) {
            # Extract meaningful error from output (last few lines typically contain the error)
            throw "Failed to restore backup (exit code $($restoreResult.ExitCode)): $(Get-NativeErrorDetail $restoreResult)"
        }
        
        Write-Log "[OK] Test restore successful"
        return $true
    }
    catch {
        Write-Log "[ERROR] Test restore failed: $_" -Level "ERROR"
        return $false
    }
    finally {
        # Clean up test database
        if ($testDbName) {
            Write-Log "  Cleaning up test database..."
            $dropArgs = @(
                "-h", $DbHost,
                "-p", $Port,
                "-U", $Username,
                "-d", "postgres",
                "-c", "DROP DATABASE IF EXISTS $testDbName;"
            )
            
            $dropResult = Invoke-Native -Exe $PsqlPath -Arguments $dropArgs
            if ($dropResult.ExitCode -ne 0) {
                Write-Log "[ERROR] Cleanup FAILED: test database $testDbName may still exist (exit code $($dropResult.ExitCode)). Drop it manually: $(Get-NativeErrorDetail $dropResult)" -Level "ERROR"
            }
        }
        
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ============================================================================
# RESTORE INSTRUCTIONS
# ============================================================================

function New-RestoreInstructions {
    param([array]$BackupResults)
    
    $instructionsFile = Join-Path $BackupDir "RESTORE_INSTRUCTIONS.md"
    
    $instructions = @"
# Hishob Database Restore Instructions

**Backup Date:** $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
**Backup Location:** $BackupDir

## Backup Contents

| Database | Backup File | Size (MB) |
|----------|-------------|-----------|
"@

    foreach ($result in $BackupResults) {
        $instructions += "`n| $($result.Database) | $($result.BackupFile) | $($result.SizeMB) |"
    }

    $instructions += @"


## Full System Restore

To restore all databases:

``````powershell
`$BackupDir = "$BackupDir"
`$Password = "YOUR_PASSWORD"
`$env:PGPASSWORD = `$Password

"@

    foreach ($result in $BackupResults) {
        $instructions += @"

# Restore $($result.Database)
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" ``
    -h localhost ``
    -p 5432 ``
    -U postgres ``
    -d $($result.Database) ``
    -c ``
    --if-exists ``
    -v ``
    "$($result.BackupFile)"

"@
    }

    $instructions += @"

Remove-Item Env:\PGPASSWORD
``````

## Single Database Restore

To restore a single database:

``````powershell
`$Password = "YOUR_PASSWORD"
`$env:PGPASSWORD = `$Password

& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" ``
    -h localhost ``
    -p 5432 ``
    -U postgres ``
    -d DATABASE_NAME ``
    -c ``
    --if-exists ``
    -v ``
    "BACKUP_FILE_PATH"

Remove-Item Env:\PGPASSWORD
``````

## Important Notes

- The `-c` flag drops existing objects before restoring (REPLACES data in the target database)
- The `--if-exists` flag prevents errors if objects don't exist
- `-1` (single transaction) CANNOT be combined with `-c`; use `-1` alone only when restoring into a fresh, empty database
- Restoring with `-c` into a database that already has data overwrites it - use an empty test database to verify a backup without touching real data
- Use `-d postgres` with `-C` flag to recreate the entire database
- Verify restore with: `SELECT COUNT(*) FROM schema.table;`
- Test in a non-production environment first

## Rollback After Migration

If migration fails and you need to rollback:

1. Drop the hishob_group database
2. Recreate it: ``CREATE DATABASE hishob_group;``
3. Restore from backup using instructions above
4. Restart all services

## Verification Queries

After restore, verify data:

``````sql
-- Auth database
SELECT COUNT(*) FROM hishob_auth.groups;
SELECT COUNT(*) FROM hishob_auth.group_members;

-- Group database (if migrated)
SELECT COUNT(*) FROM hishob_group.groups;
SELECT COUNT(*) FROM hishob_group.group_members;
``````
"@

    Set-Content -Path $instructionsFile -Value $instructions
    Write-Log "Restore instructions saved: $instructionsFile"
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

function Start-Backup {
    Write-Log "================================================================"
    Write-Log "HISHOB DATABASE BACKUP - Version 2.6 (Clean Summary)"
    Write-Log "================================================================"
    Write-Log "Backup directory: $BackupDir"
    Write-Log "Databases to backup: $($Databases -join ', ')"
    Write-Log "================================================================"
    
    try {
        # Create backup directory
        if (-not (Test-Path $BackupDir)) {
            New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
            Write-Log "Created backup directory: $BackupDir"
        }
        
        # Check prerequisites
        if (-not (Test-Path $PgDumpPath)) {
            throw "pg_dump.exe not found at: $PgDumpPath"
        }
        
        if (-not (Test-Path $PsqlPath)) {
            throw "psql.exe not found at: $PsqlPath"
        }
        
        # Get password
        $password = Get-DatabasePassword
        
        # Backup all databases
        Write-Log "=== BACKING UP DATABASES ==="
        $backupResults = @()
        
        foreach ($db in $Databases) {
            $result = Backup-Database -DatabaseName $db -Password $password
            $backupResults += $result
        }
        
        # Verify backups
        Write-Log "=== VERIFYING BACKUPS ==="
        $allValid = $true
        
        foreach ($result in $backupResults) {
            $isValid = Test-BackupIntegrity -BackupFile $result.BackupFile -Password $password
            if (-not $isValid) {
                $allValid = $false
                Write-Log "[ERROR] Backup verification failed for $($result.Database)" -Level "ERROR"
            }
        }
        
        if (-not $allValid) {
            throw "One or more backups failed verification"
        }
        
        # Test restore (sample one database)
        Write-Log "=== TESTING RESTORE CAPABILITY ==="
        $sampleBackup = $backupResults | Where-Object { $_.Database -eq "hishob_auth" } | Select-Object -First 1
        
        if ($sampleBackup) {
            $restoreSuccess = Test-BackupRestore -BackupFile $sampleBackup.BackupFile -Password $password
            
            if (-not $restoreSuccess) {
                throw "Test restore failed - backups created but restore capability not verified. Review log for details."
            }
        }
        
        # Generate restore instructions
        New-RestoreInstructions -BackupResults $backupResults
        
        # Summary
        $totalSizeMB = ($backupResults | ForEach-Object { $_.SizeMB } | Measure-Object -Sum).Sum
        
        Write-Log "================================================================"
        Write-Log "[OK] BACKUP COMPLETED SUCCESSFULLY"
        Write-Log "================================================================"
        Write-Log "Databases backed up: $($backupResults.Count)"
        Write-Log "Total backup size: $([math]::Round($totalSizeMB, 2)) MB"
        Write-Log "Backup location: $BackupDir"
        Write-Log "Log file: $LogFile"
        Write-Log "================================================================"
        
    }
    catch {
        Write-Log "BACKUP FAILED: $_" -Level "ERROR"
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level "ERROR"
        throw
    }
}

# Execute
Start-Backup


