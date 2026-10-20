# SmartHire Business Workflow Tests
# Run with: powershell -ExecutionPolicy Bypass -File .\run-workflow-tests.ps1

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"
$results = New-Object System.Collections.Generic.List[object]

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int[]]$ExpectedStatus = @(200, 201),
        [string]$Description = ""
    )
    
    $url = "$baseUrl$Endpoint"
    $headers = @{ "Content-Type" = "application/json" }
    if ($Headers.Count -gt 0) {
        $keys = @($Headers.Keys)
        foreach ($key in $keys) {
            $headers[$key] = $Headers[$key]
        }
    }
    
    try {
        if ($Body) {
            $response = Invoke-RestMethod -Method $Method -Uri $url -Headers $headers -Body $Body -ErrorAction Stop
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $url -Headers $headers -ErrorAction Stop
        }
        $statusCode = 200
        $passed = $ExpectedStatus -contains $statusCode
        $resultText = if ($passed) { "PASS" } else { "FAIL" }
        $results.Add([pscustomobject]@{
            Test = $Name
            Endpoint = $Endpoint
            Method = $Method
            Status = $statusCode
            Result = $resultText
            Description = $Description
        })
        Write-Host "[$resultText] $Name - $Method $Endpoint - Status: $statusCode"
        return $response
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $passed = $ExpectedStatus -contains $statusCode
        $resultText = if ($passed) { "PASS" } else { "FAIL" }
        $results.Add([pscustomobject]@{
            Test = $Name
            Endpoint = $Endpoint
            Method = $Method
            Status = $statusCode
            Result = $resultText
            Description = $Description + " (Error: $($_.Exception.Message))"
        })
        Write-Host "[$resultText] $Name - $Method $Endpoint - Status: $statusCode - Error: $($_.Exception.Message)"
        return $null
    }
}

# Generate unique test email
$testId = [Guid]::NewGuid().ToString().Substring(0, 8)
$candidateEmail = "candidate$testId@test.com"
$candidatePassword = "TestPass123!"
$recruiterEmail = "recruiter$testId@test.com"
$recruiterPassword = "RecruiterPass123!"

Write-Host "=== SmartHire Business Workflow Tests ==="
Write-Host "Base URL: $baseUrl"
Write-Host "Test ID: $testId"
Write-Host ""

# 1. Gateway Health
Test-Endpoint -Name "Gateway Health" -Method "GET" -Endpoint "/actuator/health" -Description "Gateway health check"

# 2. Candidate Registration through Gateway
$regBody = @{ email = "candidate$testId@test.com"; password = "TestPass123!" } | ConvertTo-Json -Depth 3
$regResponse = Test-Endpoint -Name "Candidate Registration" -Method "POST" -Endpoint "/api/v1/auth/register" -Body $regBody -ExpectedStatus @(201) -Description "Register new candidate"

# 3. Candidate Login through Gateway
$loginBody = @{ email = "candidate$testId@test.com"; password = "TestPass123!" } | ConvertTo-Json -Depth 3
$loginResponse = Test-Endpoint -Name "Candidate Login" -Method "POST" -Endpoint "/api/v1/auth/login" -Body $loginBody -ExpectedStatus @(200) -Description "Login candidate"

# Extract access token from login response
$accessToken = $null
if ($loginResponse -and $loginResponse.accessToken) {
    $accessToken = $loginResponse.accessToken
    Write-Host "Access token obtained (length: $($accessToken.Length))"
}

# 4. Public Job Listing
Test-Endpoint -Name "Public Job Listing" -Method "GET" -Endpoint "/api/v1/jobs" -Description "List public jobs"

# 5. Public Job Details - get a job ID from the listing first
$jobsResponse = Invoke-RestMethod -Method GET -Uri "$baseUrl/api/v1/jobs" -Headers @{ "Content-Type" = "application/json" } -ErrorAction SilentlyContinue
$jobId = $null
if ($jobsResponse -and $jobsResponse.content -and $jobsResponse.content.Count -gt 0) {
    $jobId = $jobsResponse.content[0].jobId
    Write-Host "Found job ID: $jobId"
}

if ($jobId) {
    # 5. Public Job Details
    Test-Endpoint -Name "Public Job Details" -Method "GET" -Endpoint "/api/v1/jobs/$jobId" -Description "Get job details"
} else {
    $results.Add([pscustomobject]@{
        Test = "Public Job Details"
        Endpoint = "/api/v1/jobs/{jobId}"
        Method = "GET"
        Status = 0
        Result = "BLOCKED"
        Description = "No jobs found to test details"
    })
    Write-Host "[BLOCKED] Public Job Details - No jobs found"
}

# 6. Authenticated Candidate Access - Get /api/v1/auth/me
if ($accessToken) {
    $authHeaders = @{ "Authorization" = "Bearer $accessToken" }
    Test-Endpoint -Name "Authenticated /me" -Method "GET" -Endpoint "/api/v1/auth/me" -Headers $authHeaders -Description "Get authenticated user profile"
} else {
    $results.Add([pscustomobject]@{
        Test = "Authenticated /me"
        Endpoint = "/api/v1/auth/me"
        Method = "GET"
        Status = 0
        Result = "BLOCKED"
        Description = "No access token available"
    })
    Write-Host "[BLOCKED] Authenticated /me - No access token"
}

# 6b. Submit Application (if jobId exists)
if ($accessToken -and $jobId) {
    $appBody = @{ jobId = $jobId; resumeLink = "https://example.com/resume.pdf" } | ConvertTo-Json -Depth 3
    $appHeaders = @{ "Authorization" = "Bearer $accessToken" }
    $appResponse = Test-Endpoint -Name "Submit Application" -Method "POST" -Endpoint "/api/v1/applications" -Headers $appHeaders -Body $appBody -ExpectedStatus @(201) -Description "Submit job application"
    
    $appId = $null
    if ($appResponse -and $appResponse.applicationId) {
        $appId = $appResponse.applicationId
        Write-Host "Application submitted: $appId"
    }
} else {
    $results.Add([pscustomobject]@{
        Test = "Submit Application"
        Endpoint = "/api/v1/applications"
        Method = "POST"
        Status = 0
        Result = "BLOCKED"
        Description = "Missing access token or job ID"
    })
    Write-Host "[BLOCKED] Submit Application - Missing access token or job ID"
}

# 7. Rejection of Unauthorized Access to Protected Endpoints
# Test accessing /api/v1/auth/me without token
Test-Endpoint -Name "Unauthorized /me" -Method "GET" -Endpoint "/api/v1/auth/me" -ExpectedStatus @(401) -Description "Unauthenticated access to /me"

# 8. Recruiter Authentication (register a recruiter)
$recruiterRegBody = @{ email = "recruiter$testId@test.com"; password = "RecruiterPass123!" } | ConvertTo-Json -Depth 3
$recruiterRegResponse = Test-Endpoint -Name "Recruiter Registration" -Method "POST" -Endpoint "/api/v1/auth/register" -Body $recruiterRegBody -ExpectedStatus @(201) -Description "Register recruiter"

$recruiterLoginBody = @{ email = "recruiter$testId@test.com"; password = "RecruiterPass123!" } | ConvertTo-Json -Depth 3
$recruiterLoginResponse = Test-Endpoint -Name "Recruiter Login" -Method "POST" -Endpoint "/api/v1/auth/login" -Body $recruiterLoginBody -ExpectedStatus @(200) -Description "Recruiter login"

$recruiterToken = $null
if ($recruiterLoginResponse -and $recruiterLoginResponse.accessToken) {
    $recruiterToken = $recruiterLoginResponse.accessToken
    Write-Host "Recruiter access token obtained"
}

# 10. Recruiter Job Creation
if ($recruiterToken) {
    $recruiterHeaders = @{ "Authorization" = "Bearer $recruiterToken" }
    $jobBody = @{ companyName = "Test Corp"; role = "Software Engineer"; location = "Remote" } | ConvertTo-Json -Depth 3
    $jobCreateResponse = Test-Endpoint -Name "Recruiter Job Creation" -Method "POST" -Endpoint "/api/v1/jobs" -Headers $recruiterHeaders -Body $jobBody -ExpectedStatus @(201) -Description "Create job as recruiter"
    
    $recruiterJobId = $null
    if ($jobCreateResponse -and $jobCreateResponse.jobId) {
        $recruiterJobId = $jobCreateResponse.jobId
        Write-Host "Job created: $recruiterJobId"
    }
} else {
    $results.Add([pscustomobject]@{
        Test = "Recruiter Job Creation"
        Endpoint = "/api/v1/jobs"
        Method = "POST"
        Status = 0
        Result = "BLOCKED"
        Description = "Missing recruiter token"
    })
    Write-Host "[BLOCKED] Recruiter Job Creation - Missing token"
}

# Document operations - check if document service is accessible
# 11. Document operations (if document service works)
if ($accessToken) {
    $docHeaders = @{ "Authorization" = "Bearer $accessToken" }
    Test-Endpoint -Name "List Documents" -Method "GET" -Endpoint "/api/v1/documents" -Headers $docHeaders -ExpectedStatus @(200, 401) -Description "List documents"
} else {
    $results.Add([pscustomobject]@{
        Test = "List Documents"
        Endpoint = "/api/v1/documents"
        Method = "GET"
        Status = 0
        Result = "BLOCKED"
        Description = "Missing access token"
    })
    Write-Host "[BLOCKED] List Documents - Missing access token"
}

# Notification operations
if ($accessToken) {
    $notifHeaders = @{ "Authorization" = "Bearer $accessToken" }
    Test-Endpoint -Name "List Notifications" -Method "GET" -Endpoint "/api/v1/notifications" -Headers $notifHeaders -ExpectedStatus @(200, 401) -Description "List notifications"
} else {
    $results.Add([pscustomobject]@{
        Test = "List Notifications"
        Endpoint = "/api/v1/notifications"
        Method = "GET"
        Status = 0
        Result = "BLOCKED"
        Description = "Missing access token"
    })
    Write-Host "[BLOCKED] List Notifications - Missing access token"
}

# Output Results Table
Write-Host ""
Write-Host "=== TEST RESULTS ==="
$results | Format-Table -AutoSize

# Summary
$passed = ($results | Where-Object { $_.Result -eq "PASS" }).Count
$failed = ($results | Where-Object { $_.Result -eq "FAIL" }).Count
$blocked = ($results | Where-Object { $_.Result -eq "BLOCKED" }).Count
$notTested = ($results | Where-Object { $_.Result -eq "NOT TESTED" }).Count

Write-Host ""
Write-Host "=== SUMMARY ==="
Write-Host "Total: $($results.Count)"
Write-Host "PASS: $passed"
Write-Host "FAIL: $failed"
Write-Host "BLOCKED: $blocked"
Write-Host "NOT TESTED: $notTested"

# Return results for external consumption
$results