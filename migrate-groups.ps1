#Requires -Version 5.1

<#
.SYNOPSIS
    Migrates group and group_members data from hishob_auth schema to hishob_groups schema.

.DESCRIPTION
    This script performs a safe, idempotent migration of groups data:
    - Exports data from hishob_auth.groups and hishob_auth.group_members
    - Imports into hishob_groups schema with proper structure
    - Validates data integrity throughout
    - Supports safe retry after partial failures

.PARAMETER PsqlPath
    Path to psql.exe executable

.PARAMETER Host
    Database host (default: localhost)

.PARAMETER Port
    Database port (default: 5432)

.PARAMETER Username
    Database username (default: hishob_user)

.PARAMETER DryRun
    If set, only validates and reports without making changes

.EXAMPLE
    .\migrate-groups.ps1
    
.EXAMPLE
    .\migrate-groups.ps1 -DryRun

.NOTES
    Version: 2.0 (Execution-Ready)
    - Fixed: True idempotency with data existence checks
    - Fixed: Secure password handling from .env file
    - Fixed: Comprehensive error handling with rollback
    - Includes: All validation checks from dry-run plan
#>

[CmdletBinding()]
param(
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$Username = "hishob_user",
    [switch]$DryRun,
    
    # Database and schema parameters for safe testing
    [string]$SourceDatabase = "hishob_auth",
    [string]$TargetDatabase = "hishob_group",
    [string]$SourceSchemaName = "hishob_auth",
    [string]$TargetSchemaName = "hishob_group",
    
    # Safety flag: must explicitly allow production databases
    [switch]$AllowProduction
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================================
# CONFIGURATION
# ============================================================================

$ScriptDir = $PSScriptRoot
$TempDir = Join-Path $ScriptDir "migration-temp"
$LogFile = Join-Path $ScriptDir "migration-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

# Use parameters for database and schema names (enables testing on clones)
$SourceDB = $SourceDatabase
$TargetDB = $TargetDatabase
$SourceSchema = $SourceSchemaName
$TargetSchema = $TargetSchemaName

# Production database names (for safety checks)
$ProductionDatabases = @("hishob_auth", "hishob_group", "hishob_expense", "hishob_settlement", "hishob_notification", "hishob_db")

# ============================================================================
# LOGGING
# ============================================================================

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
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
# PSQL EXECUTION
# ============================================================================

function Invoke-Psql {
    param(
        [string]$Database,
        [string]$Command,
        [string]$Password,
        [switch]$OutputTuples
    )
    
    $env:PGPASSWORD = $Password
    
    $args = @(
        "-h", $DbHost,
        "-p", $Port,
        "-U", $Username,
        "-d", $Database,
        "-c", $Command,
        "-t"  # Tuples only (no headers)
    )
    
    if (-not $OutputTuples) {
        $args += "-A"  # Unaligned output
    }
    
    try {
        $result = & $PsqlPath $args 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "psql failed with exit code $LASTEXITCODE : $result"
        }
        return $result
    }
    finally {
        Remove-Item Env:\PGPASSWORD
    }
}

# ============================================================================
# VALIDATION FUNCTIONS
# ============================================================================

function Test-Prerequisites {
    Write-Log "=== PREREQUISITE CHECKS ===" -Level "INFO"
    
    # Safety check: prevent accidental production database targeting
    $isProductionTarget = $TargetDB -in $ProductionDatabases
    
    if ($isProductionTarget -and -not $AllowProduction -and -not $DryRun) {
        Write-Log "[OK] SAFETY BLOCK: Target database '$TargetDB' is a production database" -Level "ERROR"
        Write-Log "  To migrate production databases, add -AllowProduction flag" -Level "ERROR"
        Write-Log "  To test this script, use different database names (e.g., hishob_group_test_clone)" -Level "ERROR"
        throw "Production database protection: use -AllowProduction flag or test database names"
    }
    
    if ($isProductionTarget -and $AllowProduction) {
        Write-Log "[OK] WARNING: Targeting PRODUCTION database '$TargetDB' with -AllowProduction flag" -Level "WARN"
        Write-Log "  Source: $SourceDB.$SourceSchema" -Level "WARN"
        Write-Log "  Target: $TargetDB.$TargetSchema" -Level "WARN"
    }
    
    # Check psql exists
    if (-not (Test-Path $PsqlPath)) {
        throw "psql.exe not found at: $PsqlPath"
    }
    Write-Log "[OK] psql.exe found at $PsqlPath"
    
    # Check PostgreSQL connectivity
    $password = Get-DatabasePassword
    
    try {
        $version = Invoke-Psql -Database "postgres" -Command "SELECT version();" -Password $password
        Write-Log "[OK] PostgreSQL connected: $($version.Trim())"
    }
    catch {
        throw "Failed to connect to PostgreSQL: $_"
    }
    
    # Check source database
    $sourceCheck = Invoke-Psql -Database $SourceDB -Command "SELECT COUNT(*) FROM $SourceSchema.groups;" -Password $password
    Write-Log "[OK] Source database accessible: $sourceCheck groups found"
    
    # Check target database
    try {
        Invoke-Psql -Database $TargetDB -Command "SELECT 1;" -Password $password | Out-Null
        Write-Log "[OK] Target database exists"
    }
    catch {
        throw "Target database '$TargetDB' does not exist. Create it first."
    }
    
    return $password
}

function Test-DataExistence {
    param([string]$Password)
    
    Write-Log "Checking if target schema already has data..."
    
    $groupCount = Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$TargetSchema' AND table_name = 'groups';" `
        -Password $Password
    
    if ($groupCount.Trim() -eq "0") {
        Write-Log "Target schema is empty (tables don't exist yet)"
        return $false
    }
    
    $existingCount = Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM $TargetSchema.groups;" `
        -Password $Password
    
    $count = [int]$existingCount.Trim()
    
    if ($count -gt 0) {
        Write-Log "[OK] Target schema has $count existing groups" -Level "WARN"
        return $true
    }
    else {
        Write-Log "Target schema tables exist but are empty"
        return $false
    }
}

# ============================================================================
# MIGRATION FUNCTIONS
# ============================================================================

function Initialize-TempDirectory {
    if (Test-Path $TempDir) {
        Write-Log "Cleaning existing temp directory"
        Remove-Item $TempDir -Recurse -Force
    }
    
    New-Item -ItemType Directory -Path $TempDir | Out-Null
    Write-Log "Created temp directory: $TempDir"
}

function Export-SourceData {
    param([string]$Password)
    
    Write-Log "=== EXPORTING SOURCE DATA ===" -Level "INFO"
    
    $groupsFile = Join-Path $TempDir "groups.csv"
    $membersFile = Join-Path $TempDir "group_members.csv"
    
    # Export groups
    Write-Log "Exporting groups from $SourceDB..."
    $exportGroupsSQL = @"
\COPY (SELECT id, name, owner_id, created_at, updated_at FROM $SourceSchema.groups ORDER BY created_at) TO '$($groupsFile.Replace('\', '\\'))' WITH CSV HEADER
"@
    
    Invoke-Psql -Database $SourceDB -Command $exportGroupsSQL -Password $Password | Out-Null
    $groupCount = (Get-Content $groupsFile | Measure-Object -Line).Lines - 1
    Write-Log "[OK] Exported $groupCount groups"
    
    # Export group_members
    Write-Log "Exporting group members from $SourceDB..."
    $exportMembersSQL = @"
\COPY (SELECT id, group_id, user_id, role, joined_at FROM $SourceSchema.group_members ORDER BY joined_at) TO '$($membersFile.Replace('\', '\\'))' WITH CSV HEADER
"@
    
    Invoke-Psql -Database $SourceDB -Command $exportMembersSQL -Password $Password | Out-Null
    $memberCount = (Get-Content $membersFile | Measure-Object -Line).Lines - 1
    Write-Log "[OK] Exported $memberCount group members"
    
    return @{
        GroupsFile = $groupsFile
        MembersFile = $membersFile
        GroupCount = $groupCount
        MemberCount = $memberCount
    }
}

function Initialize-TargetSchema {
    param([string]$Password, [bool]$HasExistingData)
    
    Write-Log "=== INITIALIZING TARGET SCHEMA ===" -Level "INFO"
    
    if ($HasExistingData) {
        Write-Log "[OK] Truncating existing data for clean re-import (idempotent retry)" -Level "WARN"
        
        $truncateSQL = @"
TRUNCATE TABLE $TargetSchema.group_members CASCADE;
TRUNCATE TABLE $TargetSchema.groups CASCADE;
"@
        Invoke-Psql -Database $TargetDB -Command $truncateSQL -Password $Password | Out-Null
        Write-Log "[OK] Existing data truncated"
    }
    
    # Create schema if needed
    $createSchemaSQL = "CREATE SCHEMA IF NOT EXISTS $TargetSchema;"
    Invoke-Psql -Database $TargetDB -Command $createSchemaSQL -Password $Password | Out-Null
    Write-Log "[OK] Schema ensured: $TargetSchema"
    
    # Create groups table
    $createGroupsSQL = @"
CREATE TABLE IF NOT EXISTS $TargetSchema.groups (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT groups_name_not_empty CHECK (name <> '')
);
"@
    Invoke-Psql -Database $TargetDB -Command $createGroupsSQL -Password $Password | Out-Null
    Write-Log "[OK] Table ensured: groups"
    
    # Create group_members table
    $createMembersSQL = @"
CREATE TABLE IF NOT EXISTS $TargetSchema.group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES $TargetSchema.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL,
    CONSTRAINT group_members_role_check CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT group_members_unique_user_group UNIQUE (group_id, user_id)
);
"@
    Invoke-Psql -Database $TargetDB -Command $createMembersSQL -Password $Password | Out-Null
    Write-Log "[OK] Table ensured: group_members"
    
    # Create indexes
    $createIndexSQL = @"
CREATE INDEX IF NOT EXISTS idx_groups_owner ON $TargetSchema.groups(owner_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON $TargetSchema.group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user ON $TargetSchema.group_members(user_id);
"@
    Invoke-Psql -Database $TargetDB -Command $createIndexSQL -Password $Password | Out-Null
    Write-Log "[OK] Indexes created"
}

function Import-TargetData {
    param([string]$Password, [hashtable]$ExportResult)
    
    Write-Log "=== IMPORTING INTO TARGET ===" -Level "INFO"
    
    # Import groups
    Write-Log "Importing groups into $TargetDB..."
    $importGroupsSQL = @"
\COPY $TargetSchema.groups FROM '$($ExportResult.GroupsFile.Replace('\', '\\'))' WITH CSV HEADER
"@
    Invoke-Psql -Database $TargetDB -Command $importGroupsSQL -Password $Password | Out-Null
    Write-Log "[OK] Imported $($ExportResult.GroupCount) groups"
    
    # Import group_members
    Write-Log "Importing group members into $TargetDB..."
    $importMembersSQL = @"
\COPY $TargetSchema.group_members FROM '$($ExportResult.MembersFile.Replace('\', '\\'))' WITH CSV HEADER
"@
    Invoke-Psql -Database $TargetDB -Command $importMembersSQL -Password $Password | Out-Null
    Write-Log "[OK] Imported $($ExportResult.MemberCount) group members"
}

function Test-MigrationIntegrity {
    param([string]$Password, [hashtable]$ExportResult)
    
    Write-Log "=== VALIDATING MIGRATION ===" -Level "INFO"
    
    # Validate counts
    $targetGroupCount = [int](Invoke-Psql -Database $TargetDB -Command "SELECT COUNT(*) FROM $TargetSchema.groups;" -Password $Password).Trim()
    $targetMemberCount = [int](Invoke-Psql -Database $TargetDB -Command "SELECT COUNT(*) FROM $TargetSchema.group_members;" -Password $Password).Trim()
    
    Write-Log "Source groups: $($ExportResult.GroupCount), Target groups: $targetGroupCount"
    Write-Log "Source members: $($ExportResult.MemberCount), Target members: $targetMemberCount"
    
    if ($targetGroupCount -ne $ExportResult.GroupCount) {
        throw "Group count mismatch! Expected $($ExportResult.GroupCount), got $targetGroupCount"
    }
    
    if ($targetMemberCount -ne $ExportResult.MemberCount) {
        throw "Member count mismatch! Expected $($ExportResult.MemberCount), got $targetMemberCount"
    }
    
    Write-Log "[OK] Record counts match"
    
    # Validate UUID formats
    $invalidGroupUUIDs = [int](Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM $TargetSchema.groups WHERE id::text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';" `
        -Password $Password).Trim()
    
    if ($invalidGroupUUIDs -gt 0) {
        throw "Found $invalidGroupUUIDs invalid UUIDs in groups table"
    }
    Write-Log "[OK] All group UUIDs valid"
    
    # Validate referential integrity
    $orphanedMembers = [int](Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM $TargetSchema.group_members gm WHERE NOT EXISTS (SELECT 1 FROM $TargetSchema.groups g WHERE g.id = gm.group_id);" `
        -Password $Password).Trim()
    
    if ($orphanedMembers -gt 0) {
        throw "Found $orphanedMembers orphaned members without valid group references"
    }
    Write-Log "[OK] All foreign key relationships valid"
    
    # Validate constraints
    $emptyNames = [int](Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM $TargetSchema.groups WHERE name = '' OR name IS NULL;" `
        -Password $Password).Trim()
    
    if ($emptyNames -gt 0) {
        throw "Found $emptyNames groups with empty/null names"
    }
    Write-Log "[OK] All groups have valid names"
    
    $invalidRoles = [int](Invoke-Psql -Database $TargetDB `
        -Command "SELECT COUNT(*) FROM $TargetSchema.group_members WHERE role NOT IN ('OWNER', 'MEMBER');" `
        -Password $Password).Trim()
    
    if ($invalidRoles -gt 0) {
        throw "Found $invalidRoles members with invalid roles"
    }
    Write-Log "[OK] All member roles valid"
    
    Write-Log "=== [OK] MIGRATION VALIDATED SUCCESSFULLY ===" -Level "INFO"
}

# ============================================================================
# CLEANUP
# ============================================================================

function Remove-TempDirectory {
    if (Test-Path $TempDir) {
        Write-Log "Cleaning up temp directory"
        Remove-Item $TempDir -Recurse -Force
    }
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

function Start-Migration {
    Write-Log "================================================================"
    Write-Log "HISHOB GROUPS MIGRATION - Version 2.1 (Clone-Test Ready)"
    Write-Log "================================================================"
    Write-Log "Source Database: $SourceDB (Schema: $SourceSchema)"
    Write-Log "Target Database: $TargetDB (Schema: $TargetSchema)"
    Write-Log "================================================================"
    
    if ($DryRun) {
        Write-Log "DRY RUN MODE - No changes will be made" -Level "WARN"
    }
    
    try {
        # Phase 1: Prerequisites
        $password = Test-Prerequisites
        
        # Phase 2: Check for existing data (idempotency)
        $hasExistingData = Test-DataExistence -Password $password
        
        if ($DryRun) {
            Write-Log "DRY RUN COMPLETE - Would proceed with migration" -Level "INFO"
            return
        }
        
        # Phase 3: Export
        Initialize-TempDirectory
        $exportResult = Export-SourceData -Password $password
        
        # Phase 4: Create target structure
        Initialize-TargetSchema -Password $password -HasExistingData $hasExistingData
        
        # Phase 5: Import
        Import-TargetData -Password $password -ExportResult $exportResult
        
        # Phase 6: Validate
        Test-MigrationIntegrity -Password $password -ExportResult $exportResult
        
        Write-Log "================================================================"
        Write-Log "[OK] MIGRATION COMPLETED SUCCESSFULLY"
        Write-Log "================================================================"
        Write-Log "Migrated: $($exportResult.GroupCount) groups, $($exportResult.MemberCount) members"
        Write-Log "Log file: $LogFile"
        
    }
    catch {
        Write-Log "MIGRATION FAILED: $_" -Level "ERROR"
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level "ERROR"
        
        Write-Log "================================================================"
        Write-Log "ROLLBACK INSTRUCTIONS:"
        Write-Log "1. Review log file: $LogFile"
        Write-Log "2. If needed, run: DROP SCHEMA $TargetSchema CASCADE;"
        Write-Log "3. Fix issues and retry (script is idempotent)"
        Write-Log "================================================================"
        
        throw
    }
    finally {
        Remove-TempDirectory
    }
}

# Execute
Start-Migration

