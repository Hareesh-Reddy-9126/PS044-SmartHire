<#
.SYNOPSIS
    One-command deployment of SmartHire to a fresh Ubuntu VM.

.DESCRIPTION
    Uploads the current working tree, bootstraps Docker on the target, generates production
    secrets, builds the images and waits until every container is healthy. Then verifies the
    public HTTPS endpoint from this machine.

    The source is shipped as a tarball rather than cloned, so the deployment does not depend on
    the repository being pushed to a remote.

.PARAMETER Server
    Public IP address (or DNS name) of the VM.

.PARAMETER Domain
    Public hostname that resolves to the VM. For Oracle Cloud the zero-setup choice is
    "<public-ip>.sslip.io", e.g. "140.238.1.20.sslip.io".

.PARAMETER AcmeEmail
    Contact address for Let's Encrypt certificate expiry notices.

.PARAMETER SshUser
    Login user on the VM. Oracle Ubuntu images default to "ubuntu".

.PARAMETER SshKey
    Path to the private key. Defaults to ~/.ssh/smarthire_oracle.

.PARAMETER NotificationSenderType
    "logging" (default, no SMTP needed) or "smtp". With "smtp", also pass SmtpHost and SmtpFrom.

.PARAMETER AdminEmail
    When set, creates the first administrator after the stack is healthy and closes the
    invitation path. Credentials are written on the VM to /root/smarthire-admin-initial.txt.

.PARAMETER SkipDemoData
    Do not create the demo recruiter and the six sample job postings on an empty job board.

.EXAMPLE
    .\deploy\scripts\deploy-remote.ps1 -Server 140.238.1.20 `
        -Domain 140.238.1.20.sslip.io -AcmeEmail you@example.com `
        -AdminEmail admin@example.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [Parameter(Mandatory = $true)][string]$Domain,
    [Parameter(Mandatory = $true)][string]$AcmeEmail,
    [string]$SshUser = 'azureuser',
    [string]$SshKey = (Join-Path $HOME '.ssh\smarthire_oracle'),
    [ValidateSet('logging', 'smtp', 'resend')][string]$NotificationSenderType = 'logging',
    [string]$SmtpHost,
    [string]$SmtpFrom,
    [string]$SmtpEnvFile,
    [string]$AdminEmail,
    [switch]$SkipDemoData,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bundle = Join-Path $env:TEMP 'smarthire-src.tar.gz'

function Step($message) { Write-Host "`n==> $message" -ForegroundColor Cyan }
function Fail($message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }

# ---- Preflight -------------------------------------------------------------------------------
Step 'Preflight checks'
if (-not (Test-Path $SshKey)) { Fail "SSH key not found: $SshKey" }
if (-not (Test-Path (Join-Path $repoRoot 'deploy\docker-compose.prod.yml'))) {
    Fail "Run this from a SmartHire checkout (repo root not found at $repoRoot)."
}
if ($NotificationSenderType -eq 'smtp' -and (-not $SmtpHost -or -not $SmtpFrom)) {
    Fail 'SmtpHost and SmtpFrom are required when NotificationSenderType is smtp.'
}
if ($SmtpEnvFile -and -not (Test-Path $SmtpEnvFile)) { Fail "SMTP env file not found: $SmtpEnvFile" }

$sshArgs = @('-i', $SshKey, '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15')
$target = "$SshUser@$Server"

Step "Checking SSH connectivity to $target"
& ssh @sshArgs $target 'echo connected' 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail "Cannot SSH to $target. Check the IP, the user name, and that the VCN allows ingress TCP 22."
}

Step "Checking that $Domain points at $Server"
try {
    $resolved = [System.Net.Dns]::GetHostAddresses($Domain) | ForEach-Object { $_.IPAddressToString }
} catch {
    $resolved = @()
}
if ($resolved -contains $Server) {
    Write-Host "    DNS OK: $Domain -> $Server"
} elseif ($resolved.Count -eq 0) {
    Write-Host "    WARNING: $Domain does not resolve yet." -ForegroundColor Yellow
    if (-not $Force) { Fail 'DNS must resolve before Let''s Encrypt can issue a certificate. Use -Force to continue anyway.' }
} else {
    Write-Host "    WARNING: $Domain resolves to $($resolved -join ', '), not $Server." -ForegroundColor Yellow
    if (-not $Force) { Fail 'Certificate issuance will fail. Fix DNS or pass -Force.' }
}

# ---- Package ---------------------------------------------------------------------------------
Step 'Packaging the working tree'
if (Test-Path $bundle) { Remove-Item $bundle -Force }
Push-Location $repoRoot
try {
    & tar -czf $bundle `
        --exclude=.git `
        --exclude=node_modules `
        --exclude=target `
        --exclude=dist `
        --exclude=coverage `
        --exclude=.kilo `
        --exclude=.idea `
        --exclude=.vscode `
        --exclude=deploy/.env `
        --exclude=deploy/.env.prodtest `
        --exclude=deploy/.env.test `
        --exclude='*.log' `
        .
    if ($LASTEXITCODE -ne 0) { Fail 'tar failed.' }
} finally {
    Pop-Location
}
$sizeMb = [math]::Round((Get-Item $bundle).Length / 1MB, 1)
Write-Host "    Bundle: $bundle ($sizeMb MB)"

# ---- Upload ----------------------------------------------------------------------------------
Step 'Uploading bundle and remote runner'
& scp @sshArgs $bundle "${target}:/tmp/smarthire-src.tar.gz"
if ($LASTEXITCODE -ne 0) { Fail 'Upload failed.' }
& scp @sshArgs (Join-Path $repoRoot 'deploy\scripts\remote-deploy.sh') "${target}:/tmp/smarthire-remote-deploy.sh"
if ($LASTEXITCODE -ne 0) { Fail 'Upload of the remote runner failed.' }
if ($SmtpEnvFile) {
    & scp @sshArgs $SmtpEnvFile "${target}:/tmp/smarthire-smtp.env"
    if ($LASTEXITCODE -ne 0) { Fail 'Upload of the SMTP env file failed.' }
}

# ---- Deploy ----------------------------------------------------------------------------------
Step 'Bootstrapping and starting the stack (this can take 10-20 minutes on first run)'
$remoteCommand = "sudo env SMTP_HOST='$SmtpHost' SMTP_FROM='$SmtpFrom' bash /tmp/smarthire-remote-deploy.sh '$Domain' '$AcmeEmail' '$NotificationSenderType'"
& ssh @sshArgs $target $remoteCommand
if ($LASTEXITCODE -ne 0) { Fail 'Remote deployment failed. Inspect the output above, or run docker compose ps on the VM.' }

# ---- Public verification ---------------------------------------------------------------------
Step "Waiting for https://$Domain/actuator/health"
$deadline = (Get-Date).AddMinutes(6)
$healthy = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri "https://$Domain/actuator/health" -UseBasicParsing -TimeoutSec 15
        if ($response.StatusCode -eq 200) { $healthy = $true; break }
    } catch {
        Start-Sleep -Seconds 5
    }
}
if (-not $healthy) { Fail "https://$Domain did not become healthy. Check Caddy logs: ssh $target 'sudo docker logs smarthire-frontend-1'" }
Write-Host "    Public health endpoint returned 200"

Step 'Checking TLS certificate and security headers'
$page = Invoke-WebRequest -Uri "https://$Domain/" -UseBasicParsing -TimeoutSec 30
Write-Host ("    SPA status: {0}, bytes: {1}" -f $page.StatusCode, $page.RawContentLength)
foreach ($header in 'Strict-Transport-Security', 'X-Content-Type-Options', 'Content-Security-Policy') {
    if ($page.Headers.ContainsKey($header)) {
        Write-Host "    $header present"
    } else {
        Write-Host "    WARNING: $header missing" -ForegroundColor Yellow
    }
}

Step 'Running read-only public verification'
& (Join-Path $PSScriptRoot 'verify-public.ps1') -BaseUrl "https://$Domain" -SkipFlow
if ($LASTEXITCODE -ne 0) {
    Write-Host '    WARNING: verification reported failures (see above).' -ForegroundColor Yellow
}

# ---- Optional demo content -------------------------------------------------------------------
if (-not $SkipDemoData) {
    Step 'Seeding the demo job board (no-op when jobs already exist)'
    & ssh @sshArgs $target 'sudo bash /opt/smarthire/deploy/scripts/seed-demo.sh'
    if ($LASTEXITCODE -ne 0) {
        Write-Host '    WARNING: demo seeding failed; the site itself is still live.' -ForegroundColor Yellow
    }
}

# ---- Optional admin --------------------------------------------------------------------------
if ($AdminEmail) {
    Step "Creating administrator $AdminEmail"
    & ssh @sshArgs $target "sudo bash /opt/smarthire/deploy/scripts/create-admin.sh '$AdminEmail'"
    if ($LASTEXITCODE -ne 0) { Fail 'Administrator creation failed.' }
    Write-Host ''
    Write-Host 'Initial credentials are on the VM. Read them with:' -ForegroundColor Green
    Write-Host "    ssh -i `"$SshKey`" $target `"sudo cat /root/smarthire-admin-initial.txt`""
    Write-Host 'Sign in, change the password, then run: sudo shred -u /root/smarthire-admin-initial.txt'
}

Write-Host ''
Write-Host '=====================================================' -ForegroundColor Green
Write-Host " SmartHire is live at https://$Domain" -ForegroundColor Green
Write-Host '=====================================================' -ForegroundColor Green
