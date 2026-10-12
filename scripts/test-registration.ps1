# =============================================================================
# EventNest - end-to-end test for registration + bookmarks (Phase 5)
#
# Run from WSL (backend must be running):
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-registration.ps1
#
# Covers ARCHITECTURE.md section 7.3 and the flow in 5.5. Every assertion is a
# real HTTP call; the things worth proving here (the 409 ladder, the capacity
# guard under concurrency, idempotent save) are properties of the running system
# that a mocked repository cannot demonstrate.
# =============================================================================
$ErrorActionPreference = "Stop"
trap { Write-Host "`nSETUP FAILURE: $_" -ForegroundColor Red; exit 2 }
$base = "http://localhost:8080/api/v1"
$script:pass = 0
$script:fail = 0

function Check($name, $expected, $actual, $detail = "") {
    if ("$expected" -eq "$actual") {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name  (expected=[$expected] actual=[$actual]) $detail" -ForegroundColor Red
        $script:fail++
    }
}

function CodeOf($body) {
    $m = [regex]::Match($body, '"code"\s*:\s*"([A-Z_]+)"')
    if ($m.Success) { return $m.Groups[1].Value } return ""
}

function Invoke($method, $path, $body = $null, $token = $null) {
    $h = @{ "Content-Type" = "application/json; charset=utf-8" }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    try {
        if ($null -ne $body) {
            $r = Invoke-WebRequest -Uri "$base$path" -Method $method -Headers $h `
                    -Body ([Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing -TimeoutSec 30
        } else {
            $r = Invoke-WebRequest -Uri "$base$path" -Method $method -Headers $h `
                    -UseBasicParsing -TimeoutSec 30
        }
        return @{ status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ status = 0; body = $_.Exception.Message } }
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        return @{ status = [int]$resp.StatusCode; body = $reader.ReadToEnd() }
    }
}

function Login($email, $password) {
    $r = Invoke POST "/auth/login" ('{"email":"' + $email + '","password":"' + $password + '"}')
    if ($r.status -ne 200) { throw "login failed for $email -> $($r.status) $($r.body)" }
    return ($r.body | ConvertFrom-Json).accessToken
}

# Random per-run suffix so the suite is re-runnable against a database that has
# not been reset - a fixed address would turn the second run into a 409 on
# EMAIL_ALREADY_REGISTERED and prove nothing about registrations.
$script:runId = Get-Random -Minimum 100000 -Maximum 999999

function NewStudent($n) {
    $email = "regtest$($script:runId)$n@student.edu.in"
    $json = '{"fullName":"Reg Test ' + $n + '","email":"' + $email + '","password":"Student@123"}'
    $r = Invoke POST "/auth/register/student" $json
    if ($r.status -ne 201) { throw "register student $n failed -> $($r.status) $($r.body)" }
    return @{ email = $email; password = "Student@123" }
}

# GET /categories returns a bare JSON array, not a page envelope - unlike
# /colleges. Interpreting it as one yields $null, and a $null interpolated into
# the payload produces `"categoryId": ,`, which the server reports as
# "malformed" while the assertion that actually failed is three steps away.
$script:catId = ((Invoke GET "/categories").body | ConvertFrom-Json)[0].id
if (-not $script:catId) { throw "could not load a category id from GET /categories" }

# One here-string for the body, not string concatenation. Concatenating JSON is
# how a null id quietly becomes `"categoryId":,` - which the server rejects as
# "malformed" while the script reports something unrelated three assertions later.
function CreateEvent($orgToken, $title, $maxParticipants, $deadline, $startsAt, $endsAt) {
    $tail = Get-Random -Minimum 10000 -Maximum 99999
    $json = @"
{
  "title": "$title $tail",
  "summary": "Registration probe.",
  "description": "Created by scripts/test-registration.ps1.",
  "categoryId": $script:catId,
  "mode": "OFFLINE",
  "venue": "Test Hall",
  "city": "Pune",
  "state": "Maharashtra",
  "country": "India",
  "startsAt": "$startsAt",
  "endsAt": "$endsAt",
  "registrationDeadline": "$deadline",
  "registrationMode": "INTERNAL",
  "maxParticipants": $maxParticipants,
  "teamSizeMin": 1,
  "teamSizeMax": 4
}
"@
    $c = Invoke POST "/organizer/events" $json $orgToken
    if ($c.status -ne 201) { throw "create '$title' failed -> $($c.status) $($c.body)" }
    $created = $c.body | ConvertFrom-Json
    return @{ id = $created.summary.id; slug = $created.summary.slug }
}

function PublishEvent($orgToken, $title, $maxParticipants, $deadline) {
    $e = CreateEvent $orgToken $title $maxParticipants $deadline "2027-03-10T09:00:00Z" "2027-03-11T09:00:00Z"
    $pub = Invoke PATCH "/organizer/events/$($e.id)/status" '{ "action": "PUBLISH" }' $orgToken
    if ($pub.status -ne 200) { throw "publish '$title' failed -> $($pub.status) $($pub.body)" }
    return $e
}

Write-Host "`n=== EventNest: registration + bookmarks ===" -ForegroundColor Cyan

$orgTok = Login "rahul@xyz.edu.in" "Organizer@123"
$students = @()
for ($i = 1; $i -le 3; $i++) { $students += NewStudent $i }
$tok1 = Login $students[0].email $students[0].password
$tok2 = Login $students[1].email $students[1].password
$tok3 = Login $students[2].email $students[2].password

# --- 1. Who is allowed to register at all ----------------------------------
$r = Invoke POST "/events/1/register"
Check "anonymous registering -> 401" 401 $r.status
$r = Invoke POST "/events/1/register" $null $orgTok
Check "organizer registering -> 403 (role gate)" 403 $r.status
$r = Invoke GET "/events/1/registration" $null $orgTok
Check "organizer reading registration status -> 403" 403 $r.status

$r = Invoke POST "/events/999999/register" $null $tok1
Check "registering for a missing event -> 404" 404 $r.status

# --- 2. Unpublished event ---------------------------------------------------
# CreateEvent does not publish, so this is a genuine DRAFT.
$draft = CreateEvent $orgTok "Draft Only Probe" 10 "2027-04-01T00:00:00Z" `
        "2027-04-10T09:00:00Z" "2027-04-11T09:00:00Z"
$draftId = $draft.id
$r = Invoke POST "/events/$draftId/register" $null $tok1
Check "registering for a DRAFT -> 409" 409 $r.status
Check "  ...code EVENT_NOT_PUBLISHED" "EVENT_NOT_PUBLISHED" (CodeOf $r.body)

# --- 3. Closed registration -------------------------------------------------
$closed = PublishEvent $orgTok "Deadline Probe" 10 "2020-01-01T00:00:00Z"
$r = Invoke POST "/events/$($closed.id)/register" $null $tok1
Check "registering after the deadline -> 409" 409 $r.status
Check "  ...code REGISTRATION_CLOSED" "REGISTRATION_CLOSED" (CodeOf $r.body)

# --- 4. The happy path ------------------------------------------------------
$ev = PublishEvent $orgTok "Capacity Probe" 2 "2027-03-01T00:00:00Z"
$r = Invoke POST "/events/$($ev.id)/register" $null $tok1
Check "first registration -> 201" 201 $r.status
$res = $r.body | ConvertFrom-Json
Check "  ...status CONFIRMED" "CONFIRMED" $res.status
Check "  ...registration number has the CP- format" $true ($res.registrationNumber -match "^CP-[A-Z2-9]{10}$")
Check "  ...event summary returned" $true ($res.event.id -eq $ev.id)
Check "  ...counter now 1" 1 $res.event.registrationCount

$r = Invoke POST "/events/$($ev.id)/register" $null $tok1
Check "double register -> 409" 409 $r.status
Check "  ...code ALREADY_REGISTERED" "ALREADY_REGISTERED" (CodeOf $r.body)

$r = Invoke GET "/events/$($ev.id)/registration" $null $tok1
Check "GET my registration -> 200" 200 $r.status
$mine = $r.body | ConvertFrom-Json
Check "  ...returns the same number" $res.registrationNumber $mine.registrationNumber
Check "  ...cancellable before the event starts" $true $mine.cancellable

$r = Invoke GET "/events/$($ev.id)/registration" $null $tok2
Check "non-registrant GET -> 204 (not 404)" 204 $r.status

# --- 5. Capacity (decision D9) ---------------------------------------------
$r = Invoke POST "/events/$($ev.id)/register" $null $tok2
Check "second seat -> 201" 201 $r.status
Check "  ...counter now 2" 2 (($r.body | ConvertFrom-Json).event.registrationCount)

$r = Invoke POST "/events/$($ev.id)/register" $null $tok3
Check "third seat on a full event -> 409" 409 $r.status
Check "  ...code EVENT_FULL" "EVENT_FULL" (CodeOf $r.body)

# --- 6. Cancel, then re-register -------------------------------------------
$r = Invoke DELETE "/events/$($ev.id)/register?reason=Made%20a%20mistake" $null $tok1
Check "cancel -> 204" 204 $r.status
# Not 204: the row survives cancellation (it is the organizer's audit trail), so
# the endpoint can honestly say "you cancelled this" instead of pretending the
# student never registered. The UI needs that to offer "register again".
$r = Invoke GET "/events/$($ev.id)/registration" $null $tok1
Check "GET after cancel -> 200" 200 $r.status
Check "  ...status CANCELLED" "CANCELLED" (($r.body | ConvertFrom-Json).status)

$r = Invoke POST "/events/$($ev.id)/register" $null $tok1
Check "re-register after cancel -> 201" 201 $r.status
$again = $r.body | ConvertFrom-Json
Check "  ...registration number is stable" $res.registrationNumber $again.registrationNumber
Check "  ...counter back to 2" 2 $again.event.registrationCount

$r = Invoke POST "/events/$($ev.id)/register" $null $tok1
Check "re-registering twice -> 409" 409 $r.status

$r = Invoke POST "/events/$($ev.id)/register" $null $tok3
Check "seat still taken after re-register -> 409" 409 $r.status
Check "  ...code EVENT_FULL" "EVENT_FULL" (CodeOf $r.body)

$r = Invoke DELETE "/events/$($ev.id)/register" $null $tok2
Check "freeing a seat -> 204" 204 $r.status
$r = Invoke POST "/events/$($ev.id)/register" $null $tok3
Check "the freed seat is claimable -> 201" 201 $r.status

# tok2 gave up its seat above and never re-registered, so it has a CANCELLED row
# and nothing active to cancel.
$r = Invoke DELETE "/events/$($ev.id)/register" $null $tok2
Check "cancelling with no active registration -> 409" 409 $r.status
Check "  ...code CANNOT_CANCEL_REGISTRATION" "CANNOT_CANCEL_REGISTRATION" (CodeOf $r.body)

# --- 7. Concurrency: the claim that D9 actually makes -----------------------
$race = PublishEvent $orgTok "Race Probe" 2 "2027-03-01T00:00:00Z"
$raceJobs = @()
for ($i = 4; $i -le 9; $i++) {
    $s = NewStudent $i
    $raceJobs += Start-Job -ScriptBlock {
        param($base, $email, $password, $eventId)
        $login = Invoke-RestMethod -Uri "$base/auth/login" -Method POST `
                    -ContentType "application/json" `
                    -Body ('{"email":"' + $email + '","password":"' + $password + '"}')
        try {
            Invoke-RestMethod -Uri "$base/events/$eventId/register" -Method POST `
                -Headers @{ Authorization = "Bearer $($login.accessToken)" } | Out-Null
            201
        } catch {
            [int]$_.Exception.Response.StatusCode
        }
    } -ArgumentList $base, $s.email, $s.password, $race.id
}
$raceResults = $raceJobs | Wait-Job | Receive-Job
$raceJobs | Remove-Job
$created = @($raceResults | Where-Object { $_ -eq 201 }).Count
Check "six concurrent registrants on a 2-seat event -> exactly 2 in" 2 $created

$check = Invoke GET "/events/$($race.slug)"
$checkBody = $check.body | ConvertFrom-Json
Check "  ...counter agrees: registrationCount == 2" 2 $checkBody.summary.registrationCount

# --- 8. Bookmarks -----------------------------------------------------------
$r = Invoke POST "/events/$($ev.id)/bookmark" $null $tok1
Check "first save -> 201" 201 $r.status
$r = Invoke POST "/events/$($ev.id)/bookmark" $null $tok1
Check "saving again -> 200 (idempotent, not 409)" 200 $r.status
$r = Invoke GET "/me/bookmarks" $null $tok1
Check "GET /me/bookmarks -> 200" 200 $r.status
Check "  ...one saved event" 1 ($r.body | ConvertFrom-Json).totalElements
Check "  ...it is the right event" $ev.id (($r.body | ConvertFrom-Json).content[0].id)

$r = Invoke POST "/events/$($ev.id)/bookmark" $null $orgTok
Check "organizer bookmarking -> 403 (role gate)" 403 $r.status
$r = Invoke GET "/me/bookmarks" $null $orgTok
Check "organizer reading /me/** -> 403" 403 $r.status

$r = Invoke DELETE "/events/$($ev.id)/bookmark" $null $tok1
Check "unsave -> 204" 204 $r.status
$r = Invoke DELETE "/events/$($ev.id)/bookmark" $null $tok1
Check "unsave when already unsaved -> 204 (idempotent)" 204 $r.status
$r = Invoke GET "/me/bookmarks" $null $tok1
Check "  ...list empty again" 0 ($r.body | ConvertFrom-Json).totalElements

$r = Invoke POST "/events/$draftId/bookmark" $null $tok1
Check "saving a DRAFT -> 404" 404 $r.status

# --- 9. /me/registrations ---------------------------------------------------
$r = Invoke GET "/me/registrations" $null $tok1
Check "GET /me/registrations -> 200" 200 $r.status
$rows = $r.body | ConvertFrom-Json
Check "  ...shows the active registration" 1 ($r.body | ConvertFrom-Json).totalElements
Check "  ...with its number" $res.registrationNumber $rows.content[0].registrationNumber
Check "  ...and a nested event summary" $true ($rows.content[0].event.title.Length -gt 0)

$r = Invoke GET "/me/registrations" $null $orgTok
Check "organizer reading /me/registrations -> 403" 403 $r.status

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  PASSED: $script:pass   FAILED: $script:fail" `
      -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
Write-Host "=========================================`n" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
