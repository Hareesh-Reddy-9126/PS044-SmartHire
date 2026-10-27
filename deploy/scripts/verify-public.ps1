<#
.SYNOPSIS
    End-to-end verification of a deployed SmartHire instance.

.DESCRIPTION
    Checks TLS, security headers, SPA routing, API reachability, CORS enforcement and the complete
    candidate/recruiter workflow against a public HTTPS URL. Prints one PASS/FAIL line per check
    and exits non-zero if anything fails.

    The workflow checks create real accounts and one job posting in the target database. Use
    -SkipFlow for a read-only check of the edge and API surface.

.PARAMETER BaseUrl
    Public origin, e.g. https://140.238.1.20.sslip.io

.PARAMETER Insecure
    Accept any TLS certificate. Only for a local stack behind Caddy's internal CA.

.PARAMETER SkipFlow
    Skip registration and the candidate/recruiter workflow (no data is written).

.PARAMETER OutFile
    Optional path for a JSON evidence file.

.EXAMPLE
    .\deploy\scripts\verify-public.ps1 -BaseUrl https://140.238.1.20.sslip.io
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [switch]$Insecure,
    [switch]$SkipFlow,
    [string]$OutFile
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

if ($Insecure) {
    Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class SmartHireTrustAll : ICertificatePolicy {
  public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
"@
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object SmartHireTrustAll
}
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$results = New-Object System.Collections.ArrayList
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$password = 'VerifyCheck!2026'
$candidateEmail = "verify-cand-$stamp@example.com"
$recruiterEmail = "verify-rec-$stamp@example.com"

function Record {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    $tag = if ($Ok) { 'PASS' } else { 'FAIL' }
    [void]$results.Add([pscustomobject]@{ Check = $Name; Result = $tag; Detail = $Detail })
    Write-Host ("{0,-46} {1}  {2}" -f $Name, $tag, $Detail)
}

function Expect {
    param([string]$Name, [int]$Actual, [int[]]$Expected, [string]$Detail = '')
    Record $Name ($Expected -contains $Actual) ("HTTP $Actual" + $(if ($Detail) { " $Detail" } else { '' }))
}

function Call {
    param([string]$Method, [string]$Path, [string]$Token, $Body, $Session)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($Session) { $params['WebSession'] = $Session }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Compress)
        $params['ContentType'] = 'application/json'
    }
    try {
        $r = Invoke-WebRequest @params
        # PowerShell returns byte[] for vendor media types (e.g. actuator JSON); normalise to text.
        $body = $r.Content
        if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
        return [pscustomobject]@{ Code = [int]$r.StatusCode; Body = $body; Headers = $r.Headers; Raw = $r }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            return [pscustomobject]@{ Code = [int]$resp.StatusCode; Body = $reader.ReadToEnd(); Headers = $resp.Headers; Raw = $null }
        }
        return [pscustomobject]@{ Code = 0; Body = $_.Exception.Message; Headers = @{}; Raw = $null }
    }
}

Write-Host "== SmartHire public verification: $BaseUrl =="
Write-Host ''

# ---- Edge: TLS, headers, SPA ----------------------------------------------------------------
$root = Call GET '/'
Expect 'SPA root' $root.Code @(200) $root.Headers['Content-Type']
Record 'HSTS header' ($root.Headers['Strict-Transport-Security'] -match 'max-age=\d{6,}') "$($root.Headers['Strict-Transport-Security'])"
$csp = [string]$root.Headers['Content-Security-Policy']
Record 'CSP present and self-only' ($csp -match "default-src 'self'" -and $csp -match "object-src 'none'") 'default-src self, object-src none'
Record 'X-Content-Type-Options nosniff' ($root.Headers['X-Content-Type-Options'] -eq 'nosniff') "$($root.Headers['X-Content-Type-Options'])"
Record 'X-Frame-Options DENY' ($root.Headers['X-Frame-Options'] -eq 'DENY') "$($root.Headers['X-Frame-Options'])"
Record 'Referrer-Policy set' ([bool]$root.Headers['Referrer-Policy']) "$($root.Headers['Referrer-Policy'])"
Record 'Server header not leaked' (-not $root.Headers['Server']) "Server: $($root.Headers['Server'])"

$deep = Call GET '/jobs'
Record 'SPA deep link falls back to index' ($deep.Code -eq 200 -and $deep.Body -match '<div id="root"') "HTTP $($deep.Code)"
$font = Call GET '/fonts/material-symbols-outlined.woff2'
Record 'Self-hosted icon font served' ($font.Code -eq 200) "HTTP $($font.Code), $($font.Headers['Content-Type'])"

if ($BaseUrl -notmatch 'localhost') {
    $httpUrl = $BaseUrl -replace '^https:', 'http:'
    $redirect = $null
    try {
        $null = Invoke-WebRequest -Uri "$httpUrl/" -UseBasicParsing -MaximumRedirection 0 -TimeoutSec 15
        Record 'HTTP redirects to HTTPS' $false 'no redirect'
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        $location = [string]$_.Exception.Response.Headers['Location']
        Record 'HTTP redirects to HTTPS' (($code -in 301, 302, 307, 308) -and $location -like 'https://*') "HTTP $code -> $location"
    }
}

# ---- API surface -----------------------------------------------------------------------------
$health = Call GET '/actuator/health'
Expect 'API health' $health.Code @(200)
Record 'Health reports UP' (([string]$health.Body) -match '"status"\s*:\s*"UP"') ([string]$health.Body)
$jwks = Call GET '/oauth2/jwks'
Record 'JWKS published' ($jwks.Code -eq 200 -and ($jwks.Body | ConvertFrom-Json).keys.Count -ge 1) "HTTP $($jwks.Code)"
$jobs = Call GET '/api/v1/jobs'
Expect 'Public job list' $jobs.Code @(200)
$jobList = $jobs.Body | ConvertFrom-Json
Record 'Job list shape' ($null -ne $jobList.content) "$($jobList.page.totalElements) jobs"
$anon = Call GET '/api/v1/applications'
Expect 'Anonymous blocked from applications' $anon.Code @(401)

$corsAllowed = $null
try {
    $corsAllowed = Invoke-WebRequest -Uri "$BaseUrl/api/v1/jobs" -UseBasicParsing -Headers @{ Origin = $BaseUrl } -TimeoutSec 20
} catch { }
Record 'CORS allows the site origin' ([bool]$corsAllowed -and $corsAllowed.Headers['Access-Control-Allow-Origin'] -eq $BaseUrl) "$($corsAllowed.Headers['Access-Control-Allow-Origin'])"

$badCode = 0
$badAcao = $null
try {
    $corsBad = Invoke-WebRequest -Uri "$BaseUrl/api/v1/jobs" -UseBasicParsing -Headers @{ Origin = 'https://evil.example' } -TimeoutSec 20
    $badCode = [int]$corsBad.StatusCode
    $badAcao = $corsBad.Headers['Access-Control-Allow-Origin']
} catch {
    if ($_.Exception.Response) {
        $badCode = [int]$_.Exception.Response.StatusCode
        $badAcao = $_.Exception.Response.Headers['Access-Control-Allow-Origin']
    }
}
Record 'CORS rejects unknown origin' ($badCode -eq 403 -and -not $badAcao) "HTTP $badCode"

if (-not $SkipFlow) {
    # ---- Candidate workflow ------------------------------------------------------------------
    $r = Call POST '/api/v1/auth/register' $null @{ email = $candidateEmail; password = $password }
    Expect 'Candidate registration' $r.Code @(200, 201)

    $r = Call POST '/api/v1/auth/login' $null @{ email = $candidateEmail; password = $password }
    Expect 'Candidate login' $r.Code @(200)
    $candTok = ($r.Body | ConvertFrom-Json).accessToken

    $r = Call GET '/api/v1/auth/me' $candTok
    $roles = ($r.Body | ConvertFrom-Json).roles
    Record 'Candidate identity' ($r.Code -eq 200 -and $roles -contains 'CANDIDATE') "roles=$($roles -join ',')"

    $jobId = $jobList.content[0].jobId
    $r = Call POST '/api/v1/applications' $candTok @{ jobId = $jobId; resumeLink = 'https://example.com/resume.pdf' }
    Expect 'Candidate applies' $r.Code @(200, 201)

    $r = Call GET '/api/v1/applications' $candTok
    Expect 'Candidate lists own applications' $r.Code @(200)

    $r = Call POST '/api/v1/jobs' $candTok @{ companyName = 'Nope'; role = 'Nope'; location = 'Nope' }
    Expect 'Candidate cannot create a job' $r.Code @(403)

    # ---- Recruiter workflow ------------------------------------------------------------------
    $r = Call POST '/api/v1/auth/register' $null @{ email = $recruiterEmail; password = $password; role = 'RECRUITER'; organizationName = 'Verify Org' }
    Expect 'Recruiter registration' $r.Code @(200, 201)

    $r = Call POST '/api/v1/auth/login' $null @{ email = $recruiterEmail; password = $password }
    Expect 'Recruiter login' $r.Code @(200)
    $recTok = ($r.Body | ConvertFrom-Json).accessToken

    $r = Call POST '/api/v1/jobs' $recTok @{ companyName = 'Verify Org'; role = 'Verification Engineer'; location = 'Remote' }
    Expect 'Recruiter creates a job' $r.Code @(200, 201)
    $newJobId = ($r.Body | ConvertFrom-Json).jobId

    $r = Call POST "/api/v1/jobs/$newJobId/open" $recTok
    Expect 'Recruiter publishes the job' $r.Code @(200, 204)

    $r = Call GET "/api/v1/jobs/$newJobId"
    Record 'Published job is publicly visible' ($r.Code -eq 200) "HTTP $($r.Code) for $newJobId"

    $r = Call POST '/api/v1/applications' $candTok @{ jobId = $newJobId; resumeLink = 'https://example.com/resume2.pdf' }
    Expect 'Candidate applies to the new job' $r.Code @(200, 201)
    $appId = ($r.Body | ConvertFrom-Json).applicationId

    $r = Call POST '/api/v1/recruitments' $recTok @{ applicationId = $appId }
    Expect 'Recruiter opens a recruitment' $r.Code @(200, 201)
    $recruitId = ($r.Body | ConvertFrom-Json).recruitmentId

    $r = Call PATCH "/api/v1/recruitments/$recruitId/interview-status" $recTok @{ status = 'SCHEDULED' }
    Expect 'Interview scheduled' $r.Code @(200)
    $r = Call PATCH "/api/v1/recruitments/$recruitId/interview-status" $recTok @{ status = 'COMPLETED' }
    Expect 'Interview completed' $r.Code @(200)
    $r = Call PATCH "/api/v1/recruitments/$recruitId/final-status" $recTok @{ status = 'HIRED' }
    Expect 'Candidate marked HIRED' $r.Code @(200)

    $r = Call GET "/api/v1/recruitments/$recruitId" $candTok
    Expect 'Candidate sees own recruitment' $r.Code @(200)

    # ---- Session lifecycle -------------------------------------------------------------------
    $loginBody = @{ email = $candidateEmail; password = $password } | ConvertTo-Json -Compress
    try {
        $null = Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body $loginBody `
            -ContentType 'application/json' -UseBasicParsing -SessionVariable candSession -TimeoutSec 20
        $r = Call POST '/api/v1/auth/refresh' $null $null -Session $candSession
        Expect 'Refresh via HttpOnly cookie' $r.Code @(200)
        $r = Call POST '/api/v1/auth/logout' $candTok $null -Session $candSession
        Expect 'Logout' $r.Code @(200, 204)
    } catch {
        Record 'Session lifecycle' $false $_.Exception.Message
    }
}

Write-Host ''
$failed = @($results | Where-Object { $_.Result -ne 'PASS' })
Write-Host "== $($results.Count - $failed.Count)/$($results.Count) checks passed =="
if ($failed.Count -gt 0) {
    Write-Host 'FAILED:'
    $failed | ForEach-Object { Write-Host " - $($_.Check): $($_.Detail)" }
}

if ($OutFile) {
    [pscustomobject]@{
        target = $BaseUrl
        verifiedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        total = $results.Count
        passed = $results.Count - $failed.Count
        failed = $failed.Count
        checks = $results
    } | ConvertTo-Json -Depth 5 | Set-Content -Path $OutFile -Encoding UTF8
    Write-Host "Evidence written to $OutFile"
}

if ($failed.Count -gt 0) { exit 1 }
