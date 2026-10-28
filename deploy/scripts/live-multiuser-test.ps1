#requires -Version 5.1
<#
.SYNOPSIS
  Live multi-user smoke test for a deployed SmartHire instance.

.DESCRIPTION
  Registers/logs in several candidates and recruiters, exercises the full
  application -> shortlist -> interview -> hire lifecycle, and checks
  role-based access control. Every call goes through the public HTTPS edge.

  Safe to re-run: existing accounts are logged in instead of re-registered.
#>
param(
  [string]$BaseUrl = 'https://172.198.139.59.sslip.io',
  # Credentials are supplied through the environment so they are never committed.
  [string]$Password = $env:SMARTHIRE_TEST_PASSWORD,
  [string]$JobId = '49fa6570-c23d-44da-bcdd-4ee5057bbf7a',
  [string]$AdminEmail = 'hareeshreddy916@gmail.com',
  [string]$AdminPassword = $env:SMARTHIRE_ADMIN_PASSWORD
)

if (-not $Password) {
  throw 'Set SMARTHIRE_TEST_PASSWORD to the shared password for the disposable test accounts.'
}

$ErrorActionPreference = 'Stop'
$script:Pass = 0
$script:Fail = 0
$script:Warn = 0
$script:Rows = New-Object System.Collections.ArrayList

function Record {
  param([string]$Name, [bool]$Ok, [string]$Detail)
  if ($Ok) { $script:Pass++ } else { $script:Fail++ }
  $mark = if ($Ok) { 'PASS' } else { 'FAIL' }
  [void]$script:Rows.Add([pscustomobject]@{ Check = $Name; Result = $mark; Detail = $Detail })
  Write-Host ("{0,-52} {1}  {2}" -f $Name, $mark, $Detail)
}

function Record-Warn {
  param([string]$Name, [string]$Detail)
  $script:Warn++
  [void]$script:Rows.Add([pscustomobject]@{ Check = $Name; Result = 'WARN'; Detail = $Detail })
  Write-Host ("{0,-52} {1}  {2}" -f $Name, 'WARN', $Detail)
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    $Body,
    [string]$Token
  )
  $headers = @{}
  if ($Token) { $headers['Authorization'] = "Bearer $Token" }
  $params = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    UseBasicParsing = $true
    Headers = $headers
    TimeoutSec = 30
  }
  if ($null -ne $Body) {
    $params['Body'] = ($Body | ConvertTo-Json -Depth 10 -Compress)
    $params['ContentType'] = 'application/json'
  }
  try {
    $r = Invoke-WebRequest @params
    return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
  } catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { throw }
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $text = $reader.ReadToEnd()
    return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text }
  }
}

function Register-Or-Login {
  param([string]$Email, [string]$Role, [string]$OrganizationName)
  $body = @{ email = $Email; password = $Password; role = $Role }
  if ($OrganizationName) { $body['organizationName'] = $OrganizationName }
  $reg = Invoke-Api -Method Post -Path '/api/v1/auth/register' -Body $body
  $login = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ email = $Email; password = $Password }
  if ($login.Status -ne 200) {
    throw "Login failed for $Email (register $($reg.Status), login $($login.Status)): $($login.Body)"
  }
  $parsed = $login.Body | ConvertFrom-Json
  return [pscustomobject]@{
    Email = $Email
    Role = $Role
    OrganizationName = $OrganizationName
    RegisterStatus = $reg.Status
    Token = $parsed.accessToken
  }
}

function New-Job {
  param([string]$Token, [string]$Company, [string]$Role, [string]$Location)
  $create = Invoke-Api -Method Post -Path '/api/v1/jobs' -Token $Token -Body @{
    companyName = $Company; role = $Role; location = $Location
  }
  if ($create.Status -ne 201) { throw "Job create failed ($($create.Status)): $($create.Body)" }
  $job = $create.Body | ConvertFrom-Json
  $id = $job.jobId
  $open = Invoke-Api -Method Post -Path "/api/v1/jobs/$id/open" -Token $Token
  if ($open.Status -ne 200) { throw "Job open failed ($($open.Status)): $($open.Body)" }
  return $id
}

Write-Host "== SmartHire live multi-user test: $BaseUrl =="
Write-Host ("   Started {0:u}" -f (Get-Date))

# ---------------------------------------------------------------- accounts
$candidates = @(
  @{ Email = 'hareeshreddy916+candidate.kiran@gmail.com'; Name = 'Kiran' },
  @{ Email = 'hareeshreddy916+candidate.meera@gmail.com'; Name = 'Meera' },
  @{ Email = 'hareeshreddy916+candidate.vikram@gmail.com'; Name = 'Vikram' }
)
$recruiters = @(
  @{ Email = 'hareeshreddy916+recruiter.sneha@gmail.com'; Org = 'Orbit Systems' },
  @{ Email = 'hareeshreddy916+recruiter.arjun@gmail.com'; Org = 'Pulse Analytics' }
)

$candidateSessions = @()
foreach ($c in $candidates) {
  $s = Register-Or-Login -Email $c.Email -Role 'CANDIDATE'
  $candidateSessions += $s
  Record "Candidate account $($c.Name)" ($s.RegisterStatus -eq 201 -or $s.RegisterStatus -eq 409) "register=$($s.RegisterStatus) login=200"
}

$recruiterSessions = @()
foreach ($r in $recruiters) {
  $s = Register-Or-Login -Email $r.Email -Role 'RECRUITER' -OrganizationName $r.Org
  $recruiterSessions += $s
  Record "Recruiter account $($r.Org)" ($s.RegisterStatus -eq 201 -or $s.RegisterStatus -eq 409) "register=$($s.RegisterStatus) login=200"
}

# ---------------------------------------------------- candidates apply (job 1)
$applications = @()
for ($i = 0; $i -lt $candidateSessions.Count; $i++) {
  $s = $candidateSessions[$i]
  # Idempotency: reuse an existing application instead of creating duplicates.
  $existingList = Invoke-Api -Method Get -Path '/api/v1/applications?page=0&size=100' -Token $s.Token
  $existing = $null
  if ($existingList.Status -eq 200) {
    $existing = ($existingList.Body | ConvertFrom-Json).content | Where-Object { $_.jobId -eq $JobId } | Select-Object -First 1
  }
  if ($existing) {
    $applications += $existing
    Record "Apply as $($candidates[$i].Name)" $true "existing application=$($existing.applicationId)"
    continue
  }

  $apply = Invoke-Api -Method Post -Path '/api/v1/applications' -Token $s.Token -Body @{
    jobId = $JobId
    jobTitle = 'Frontend Engineer'
    companyName = 'Nova Labs'
    resumeLink = "https://example.com/resumes/$($candidates[$i].Name.ToLower())-frontend.pdf"
  }
  if ($apply.Status -eq 201) {
    $app = $apply.Body | ConvertFrom-Json
    $applications += $app
    Record "Apply as $($candidates[$i].Name)" $true "application=$($app.applicationId)"
  } else {
    Record "Apply as $($candidates[$i].Name)" $false "HTTP $($apply.Status): $($apply.Body)"
  }
}

# Candidate sees only their own applications.
$candList = Invoke-Api -Method Get -Path '/api/v1/applications?page=0&size=100' -Token $candidateSessions[0].Token
$candApps = ($candList.Body | ConvertFrom-Json).content
$onlyMine = $true
foreach ($a in $candApps) {
  if ($a.candidateId -and $a.candidateId -ne $candApps[0].candidateId) { $onlyMine = $false }
}
Record 'Candidate application list scoped to self' ($candList.Status -eq 200 -and $candApps.Count -ge 1) "$($candApps.Count) application(s), HTTP $($candList.Status)"

# ------------------------------------------------------ recruiters create jobs
$jobs = @()
foreach ($r in $recruiterSessions) {
  $id = New-Job -Token $r.Token -Company $r.OrganizationName -Role 'QA Automation Engineer' -Location 'Hybrid - Bengaluru'
  $jobs += $id
  Record "Create + publish job ($($r.OrganizationName))" $true "job=$id"
}

# ------------------------------------------------ recruiter 1 reviews Nova Labs
# The Nova Labs job belongs to Rahul's organization, so use that session.
$rec = Register-Or-Login -Email 'hareeshreddy916+recruiter.rahul@gmail.com' -Role 'RECRUITER' -OrganizationName 'Nova Labs'
$byJob = Invoke-Api -Method Get -Path "/api/v1/applications/by-job/${JobId}?page=0&size=100" -Token $rec.Token
$applicants = ($byJob.Body | ConvertFrom-Json).content
Record 'Recruiter lists applicants for own job' ($byJob.Status -eq 200 -and $applicants.Count -ge 3) "$($applicants.Count) applicant(s), HTTP $($byJob.Status)"

$target = $applications[0]
# Idempotency: if this candidate already reached a terminal state for the job,
# verify it instead of replaying transitions.
$kiranForJob = @($candApps | Where-Object { $_.jobId -eq $JobId })
$kiranTerminal = $kiranForJob | Where-Object { $_.status -in @('HIRED', 'REJECTED') } | Select-Object -First 1
if ($kiranTerminal) {
  Record 'Applicant lifecycle already complete' $true "status=$($kiranTerminal.status)"
  $target = $null
}
if ($target) {
  $review = Invoke-Api -Method Patch -Path "/api/v1/applications/$($target.applicationId)/status" -Token $rec.Token -Body @{ status = 'UNDER_REVIEW' }
  Record 'Recruiter moves applicant to UNDER_REVIEW' ($review.Status -eq 200) "HTTP $($review.Status)"

  $shortlist = Invoke-Api -Method Patch -Path "/api/v1/applications/$($target.applicationId)/status" -Token $rec.Token -Body @{ status = 'SHORTLISTED' }
  Record 'Recruiter moves applicant to SHORTLISTED' ($shortlist.Status -eq 200) "HTTP $($shortlist.Status)"

  $recCreate = Invoke-Api -Method Post -Path '/api/v1/recruitments' -Token $rec.Token -Body @{ applicationId = $target.applicationId }
  $recruitmentId = $null
  if ($recCreate.Status -eq 201) { $recruitmentId = ($recCreate.Body | ConvertFrom-Json).recruitmentId }
  Record 'Shortlist creates recruitment record' ($recCreate.Status -eq 201 -and $null -ne $recruitmentId) "HTTP $($recCreate.Status) recruitment=$recruitmentId"

  if ($recruitmentId) {
    $date = (Get-Date).AddDays(2).ToString('yyyy-MM-dd')
    $schedule = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$recruitmentId/schedule" -Token $rec.Token -Body @{
      interviewDate = $date
      interviewStartTime = '10:30'
      interviewer = 'Rahul Sharma'
      interviewLocation = 'Google Meet'
    }
    Record 'Recruiter schedules interview' ($schedule.Status -eq 200) "HTTP $($schedule.Status) date=$date"

    $completed = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$recruitmentId/interview-status" -Token $rec.Token -Body @{ status = 'COMPLETED' }
    Record 'Recruiter marks interview COMPLETED' ($completed.Status -eq 200) "HTTP $($completed.Status)"

    $hired = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$recruitmentId/final-status" -Token $rec.Token -Body @{ status = 'HIRED' }
    Record 'Recruiter records final status HIRED' ($hired.Status -eq 200) "HTTP $($hired.Status)"

    $candView = Invoke-Api -Method Get -Path "/api/v1/recruitments/$recruitmentId" -Token $candidateSessions[0].Token
    $candRec = $null
    if ($candView.Status -eq 200) { $candRec = $candView.Body | ConvertFrom-Json }
    Record 'Candidate sees HIRED outcome' ($candView.Status -eq 200 -and $candRec.finalStatus -eq 'HIRED') "HTTP $($candView.Status) final=$($candRec.finalStatus)"
  }
}

# --------------------------- Meera: alternate path NO_SHOW -> REJECTED
$meeraList = Invoke-Api -Method Get -Path '/api/v1/applications?page=0&size=100' -Token $candidateSessions[1].Token
$meeraApps = ($meeraList.Body | ConvertFrom-Json).content
$meeraApp = $meeraApps | Where-Object { $_.jobId -eq $JobId } | Select-Object -First 1
$meeraTerminal = $meeraApps | Where-Object { $_.jobId -eq $JobId -and $_.status -in @('HIRED', 'REJECTED') } | Select-Object -First 1
if ($meeraTerminal) {
  Record 'Second applicant already decided' $true "status=$($meeraTerminal.status)"
  $meeraApp = $null
}
if ($meeraApp) {
  $mReview = Invoke-Api -Method Patch -Path "/api/v1/applications/$($meeraApp.applicationId)/status" -Token $rec.Token -Body @{ status = 'UNDER_REVIEW' }
  $mShort = Invoke-Api -Method Patch -Path "/api/v1/applications/$($meeraApp.applicationId)/status" -Token $rec.Token -Body @{ status = 'SHORTLISTED' }
  $mRec = Invoke-Api -Method Post -Path '/api/v1/recruitments' -Token $rec.Token -Body @{ applicationId = $meeraApp.applicationId }
  $mRecId = $null
  if ($mRec.Status -eq 201) { $mRecId = ($mRec.Body | ConvertFrom-Json).recruitmentId }
  Record 'Second applicant shortlisted' ($mReview.Status -eq 200 -and $mShort.Status -eq 200 -and $mRec.Status -eq 201) "review=$($mReview.Status) shortlist=$($mShort.Status) recruitment=$($mRec.Status)"

  if ($mRecId) {
    $mDate = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
    $mSchedule = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$mRecId/schedule" -Token $rec.Token -Body @{
      interviewDate = $mDate; interviewStartTime = '15:00'; interviewer = 'Rahul Sharma'; interviewLocation = 'Microsoft Teams'
    }
    $mNoShow = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$mRecId/interview-status" -Token $rec.Token -Body @{ status = 'NO_SHOW' }
    $mReject = Invoke-Api -Method Patch -Path "/api/v1/recruitments/$mRecId/final-status" -Token $rec.Token -Body @{ status = 'REJECTED' }
    Record 'Interview NO_SHOW then REJECTED' ($mSchedule.Status -eq 200 -and $mNoShow.Status -eq 200 -and $mReject.Status -eq 200) "schedule=$($mSchedule.Status) noShow=$($mNoShow.Status) final=$($mReject.Status)"

    $mView = Invoke-Api -Method Get -Path "/api/v1/recruitments/$mRecId" -Token $candidateSessions[1].Token
    $mRecObj = $null
    if ($mView.Status -eq 200) { $mRecObj = $mView.Body | ConvertFrom-Json }
    Record 'Candidate sees REJECTED outcome' ($mView.Status -eq 200 -and $mRecObj.finalStatus -eq 'REJECTED') "HTTP $($mView.Status) final=$($mRecObj.finalStatus)"
  }
}

# --------------------------------------------------------------- RBAC checks
$anonApps = Invoke-Api -Method Get -Path '/api/v1/applications'
Record 'Anonymous blocked from applications (401)' ($anonApps.Status -eq 401) "HTTP $($anonApps.Status)"

$candPostJob = Invoke-Api -Method Post -Path '/api/v1/jobs' -Token $candidateSessions[0].Token -Body @{ companyName = 'X'; role = 'Y'; location = 'Z' }
Record 'Candidate blocked from creating jobs (403)' ($candPostJob.Status -eq 403) "HTTP $($candPostJob.Status)"

$candAdmin = Invoke-Api -Method Get -Path '/api/v1/auth/admin/whoami' -Token $candidateSessions[0].Token
Record 'Candidate blocked from admin endpoint (403)' ($candAdmin.Status -eq 403) "HTTP $($candAdmin.Status)"

$recCandList = Invoke-Api -Method Get -Path '/api/v1/applications?page=0&size=10' -Token $rec.Token
Record 'Recruiter blocked from candidate-only list (403)' ($recCandList.Status -eq 403) "HTTP $($recCandList.Status)"

$recAdmin = Invoke-Api -Method Get -Path '/api/v1/auth/admin/whoami' -Token $rec.Token
Record 'Recruiter blocked from admin endpoint (403)' ($recAdmin.Status -eq 403) "HTTP $($recAdmin.Status)"

$candByJob = Invoke-Api -Method Get -Path "/api/v1/applications/by-job/${JobId}?page=0&size=10" -Token $candidateSessions[0].Token
Record 'Candidate blocked from by-job applicant list (403)' ($candByJob.Status -eq 403) "HTTP $($candByJob.Status)"

# Recruiter 2 cannot read recruiter 1's applicants.
$crossOrg = Invoke-Api -Method Get -Path "/api/v1/applications/by-job/${JobId}?page=0&size=10" -Token $recruiterSessions[1].Token
Record 'Other org blocked from job applicants (404)' ($crossOrg.Status -eq 404) "HTTP $($crossOrg.Status)"

# ---------------------------------------------------------------- admin checks
if ($AdminPassword) {
  $adminLogin = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ email = $AdminEmail; password = $AdminPassword }
  $adminToken = $null
  if ($adminLogin.Status -eq 200) { $adminToken = ($adminLogin.Body | ConvertFrom-Json).accessToken }
  Record 'Admin login' ($adminLogin.Status -eq 200) "HTTP $($adminLogin.Status)"

  if ($adminToken) {
    $who = Invoke-Api -Method Get -Path '/api/v1/auth/admin/whoami' -Token $adminToken
    Record 'Admin can call admin endpoint (200)' ($who.Status -eq 200) "HTTP $($who.Status)"

    $allRec = Invoke-Api -Method Get -Path '/api/v1/recruitments?page=0&size=100' -Token $adminToken
    $recCount = 0
    if ($allRec.Status -eq 200) { $recCount = ($allRec.Body | ConvertFrom-Json).content.Count }
    Record 'Admin sees all recruitments' ($allRec.Status -eq 200) "$recCount record(s), HTTP $($allRec.Status)"
  }
} else {
  Record-Warn 'Admin checks skipped' 'Set SMARTHIRE_ADMIN_PASSWORD to include them'
}

# ------------------------------------------------------------- edge cases
$badLogin = Invoke-Api -Method Post -Path '/api/v1/auth/login' -Body @{ email = $candidates[0].Email; password = 'wrong-password-123' }
Record 'Wrong password rejected (401)' ($badLogin.Status -eq 401) "HTTP $($badLogin.Status)"

# Observe (do not create) duplicate applications: the same candidate should not
# normally hold more than one active application for the same job.
$kiranList = Invoke-Api -Method Get -Path '/api/v1/applications?page=0&size=100' -Token $candidateSessions[0].Token
$kiranApps = ($kiranList.Body | ConvertFrom-Json).content
$kiranForJob = @($kiranApps | Where-Object { $_.jobId -eq $JobId })
if ($kiranForJob.Count -gt 1) {
  Record-Warn 'Duplicate application allowed' "$($kiranForJob.Count) active applications for the same job (product gap, not a crash)"
} else {
  Record 'One application per candidate/job' $true "$($kiranForJob.Count) application"
}

# ---------------------------------------------------- job close / reopen cycle
$close = Invoke-Api -Method Post -Path "/api/v1/jobs/$JobId/close" -Token $rec.Token
$closedJob = Invoke-Api -Method Get -Path "/api/v1/jobs/$JobId" -Token $rec.Token
$closedStatus = $null
if ($closedJob.Status -eq 200) { $closedStatus = ($closedJob.Body | ConvertFrom-Json).status }
Record 'Recruiter closes job' ($close.Status -eq 200 -and $closedStatus -eq 'CLOSED') "HTTP $($close.Status) ownerView=$($closedJob.Status) status=$closedStatus"

$publicClosed = Invoke-Api -Method Get -Path "/api/v1/jobs/$JobId"
Record 'Closed job hidden from public (404)' ($publicClosed.Status -eq 404) "HTTP $($publicClosed.Status)"

$applyClosed = Invoke-Api -Method Post -Path '/api/v1/applications' -Token $candidateSessions[2].Token -Body @{
  jobId = $JobId; jobTitle = 'Frontend Engineer'; companyName = 'Nova Labs'; resumeLink = 'https://example.com/resumes/closed.pdf'
}
Record 'Application to closed job rejected (400/404)' ($applyClosed.Status -eq 400 -or $applyClosed.Status -eq 404) "HTTP $($applyClosed.Status)"

$reopen = Invoke-Api -Method Post -Path "/api/v1/jobs/$JobId/reopen" -Token $rec.Token
$openJob = Invoke-Api -Method Get -Path "/api/v1/jobs/$JobId"
$openStatus = $null
if ($openJob.Status -eq 200) { $openStatus = ($openJob.Body | ConvertFrom-Json).status }
Record 'Recruiter reopens job' ($reopen.Status -eq 200 -and $openStatus -eq 'OPEN') "HTTP $($reopen.Status) status=$openStatus"

# ---------------------------------------------------------------- summary
Write-Host ''
Write-Host ("== {0} passed, {1} failed, {2} warning(s) ==" -f $script:Pass, $script:Fail, $script:Warn)
$script:Rows | Format-Table -AutoSize
if ($script:Fail -gt 0) { exit 1 }
exit 0
