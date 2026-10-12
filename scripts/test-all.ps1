# =============================================================================
# EventNest - run every end-to-end suite in dependency order
#
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-all.ps1
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-all.ps1 -Reset   # clean repeatable run
#
# Order matters and is not arbitrary:
#   1. auth  - needs no events to exist.
#   2. events - asserts discovery starts empty and ends with exactly one
#      published event, so it must run before anything else creates events.
#   3. registration - creates its own events and students, and asserts nothing
#      about global counts, so it is safe to run late (and to re-run alone).
#   4. admin - asserts platform stats are non-zero (events exist) and creates
#      + cleans up its own categories/events, so it must run after events.
#   5. profile - edits organizer/student profiles and the college logo; touches
#      no global counts, so it is safe at the tail.
#
# -Reset stops the backend, wipes the dev schema, rebuilds it from the Flyway
# migrations + dev seed (accounts + categories only - never demo events), and
# restarts the backend. The events suite then sees a clean events table, which
# is what its "starts empty" assertions require. Without -Reset, re-runs can
# fail on count assertions from events the previous run left behind.
#
# Exits non-zero if any suite fails, so this is the command to wire into CI.
# =============================================================================
param([switch]$Reset)
$ErrorActionPreference = "Continue"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$totalFailed = 0

if ($Reset) {
    Write-Host ">>> resetting dev schema..." -ForegroundColor Cyan
    & powershell.exe -ExecutionPolicy Bypass -File (Join-Path $here "backend.ps1") stop
    & cmd.exe /c "`"$here\db\pg.bat`" `"$here\db\99_reset_dev_schema.sql`""
    if ($LASTEXITCODE -ne 0) { Write-Host "Schema reset failed." -ForegroundColor Red; exit 1 }
    # Start the backend detached (hidden window, its own stdout) so this script
    # does not stay alive waiting on the java child; the readiness gate below
    # blocks until login works anyway.
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-ExecutionPolicy","Bypass","-File",(Join-Path $here "backend.ps1"),"start","dev" `
        -WindowStyle Hidden | Out-Null
}

# Backend readiness gate. The actuator health endpoint can report UP a moment
# before the dev-profile seeder has finished inserting the demo accounts (the
# web server starts before ApplicationRunner beans run), and the first suite
# logs in as a seeded student - so we wait for THAT login to succeed rather than
# racing the seeder. Fresh-reset cold starts take ~30-60s on this machine.
Write-Host ">>> waiting for backend + seed data..." -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $null = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/login" -Method Post `
                -ContentType "application/json" `
                -Body '{"email":"abhishek@student.edu.in","password":"Student@123"}' `
                -UseBasicParsing -TimeoutSec 5
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) {
    Write-Host "Backend did not become ready within 180s." -ForegroundColor Red
    exit 1
}

foreach ($suite in @("test-auth.ps1", "test-events.ps1", "test-registration.ps1", "test-admin.ps1", "test-profile.ps1")) {
    Write-Host "`n>>> $suite" -ForegroundColor Cyan
    & powershell.exe -ExecutionPolicy Bypass -File (Join-Path $here $suite)
    if ($LASTEXITCODE -ne 0) { $totalFailed++ }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
if ($totalFailed -eq 0) {
    Write-Host "  ALL SUITES PASSED" -ForegroundColor Green
} else {
    Write-Host "  $totalFailed SUITE(S) FAILED" -ForegroundColor Red
}
Write-Host "=========================================`n" -ForegroundColor Cyan
exit $totalFailed
