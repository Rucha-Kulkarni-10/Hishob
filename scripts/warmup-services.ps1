<#
.SYNOPSIS
    Concurrent warm-up script for HISHOB Render Free-Tier Web Services.

.DESCRIPTION
    Pings the /health endpoints of all four deployed services concurrently
    to wake them up from cold spin-down without sending credentials or altering data.

.PARAMETER GatewayUrl
    Public URL for API Gateway. Default: https://hishob-gateway.onrender.com

.PARAMETER AuthServiceUrl
    Public URL for Auth Service. Default: https://hishob-auth-service.onrender.com

.PARAMETER ExpenseServiceUrl
    Public URL for Expense Service (also serves groups). Default: https://hishob-expense-service.onrender.com

.PARAMETER SettlementServiceUrl
    Public URL for Settlement Service (also serves balances). Default: https://hishob-settlement-service.onrender.com

.PARAMETER TimeoutSec
    Per-request timeout in seconds. Free tier cold starts may take up to 60-90s. Default: 90

.EXAMPLE
    .\scripts\warmup-services.ps1
    .\scripts\warmup-services.ps1 -GatewayUrl "https://my-gateway.onrender.com" -TimeoutSec 120
#>

[CmdletBinding()]
param (
    [string]$GatewayUrl           = "https://hishob-gateway.onrender.com",
    [string]$AuthServiceUrl       = "https://hishob-auth-service.onrender.com",
    [string]$ExpenseServiceUrl    = "https://hishob-expense-service.onrender.com",
    [string]$SettlementServiceUrl = "https://hishob-settlement-service.onrender.com",
    [int]$TimeoutSec              = 90
)

$services = @(
    @{ Name = "API Gateway";        Url = "$($GatewayUrl.TrimEnd('/'))/health" },
    @{ Name = "Auth Service";       Url = "$($AuthServiceUrl.TrimEnd('/'))/health" },
    @{ Name = "Expense Service";    Url = "$($ExpenseServiceUrl.TrimEnd('/'))/health" },
    @{ Name = "Settlement Service"; Url = "$($SettlementServiceUrl.TrimEnd('/'))/health" }
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " HISHOB Render Free-Tier Concurrent Warm-Up Script" -ForegroundColor Cyan
Write-Host " Target services: $($services.Count) | Request Timeout: ${TimeoutSec}s" -ForegroundColor Cyan
Write-Host " Notice: Render free instances spin down after 15 min of inactivity." -ForegroundColor Yellow
Write-Host " First request may take 30-60s to wake up the container." -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

$jobs = @()

foreach ($svc in $services) {
    $scriptBlock = {
        param($name, $url, $timeout)
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $result = [PSCustomObject]@{
            Service    = $name
            Url        = $url
            StatusCode = $null
            DurationMs = 0
            Status     = "UNKNOWN"
            Error      = ""
        }

        try {
            $req = [System.Net.HttpWebRequest]::Create($url)
            $req.Method = "GET"
            $req.Timeout = $timeout * 1000
            $req.UserAgent = "HishobWarmupScript/1.0"

            $response = $req.GetResponse()
            $stopwatch.Stop()
            $httpResponse = [System.Net.HttpWebResponse]$response
            $result.StatusCode = [int]$httpResponse.StatusCode
            $result.DurationMs = $stopwatch.ElapsedMilliseconds

            if ($result.StatusCode -eq 200) {
                $result.Status = "HEALTHY"
            } else {
                $result.Status = "DEGRADED"
            }
            $response.Close()
        } catch [System.Net.WebException] {
            $stopwatch.Stop()
            $result.DurationMs = $stopwatch.ElapsedMilliseconds
            if ($_.Exception.Response) {
                $webResp = [System.Net.HttpWebResponse]$_.Exception.Response
                $result.StatusCode = [int]$webResp.StatusCode
                $result.Status = "HTTP_$($result.StatusCode)"
            } else {
                $result.Status = "FAILED"
                $result.Error = $_.Exception.Message
            }
        } catch {
            $stopwatch.Stop()
            $result.DurationMs = $stopwatch.ElapsedMilliseconds
            $result.Status = "ERROR"
            $result.Error = $_.Exception.Message
        }

        return $result
    }

    $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $svc.Name, $svc.Url, $TimeoutSec
}

Write-Host "Pinging all 6 services concurrently..." -ForegroundColor Gray
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job -Force

Write-Host ""
Write-Host "----------------- Warm-Up Results -----------------" -ForegroundColor Green

foreach ($r in $results) {
    $color = if ($r.Status -eq "HEALTHY") { "Green" } elseif ($r.Status -like "HTTP_*") { "Yellow" } else { "Red" }
    $codeStr = if ($r.StatusCode) { "$($r.StatusCode)" } else { "N/A" }
    Write-Host ("[{0}] {1,-18} -> Code: {2,-4} ({3,5} ms) | {4}" -f $r.Status, $r.Service, $codeStr, $r.DurationMs, $r.Url) -ForegroundColor $color
    if ($r.Error) {
        Write-Host "       Error: $($r.Error)" -ForegroundColor DarkRed
    }
}

Write-Host "---------------------------------------------------" -ForegroundColor Green
$healthyCount = ($results | Where-Object { $_.Status -eq "HEALTHY" }).Count
Write-Host "Warm-up finished. $healthyCount / $($services.Count) services responding healthy." -ForegroundColor $(if ($healthyCount -eq $services.Count) { "Green" } else { "Yellow" })
