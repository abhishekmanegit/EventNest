# =============================================================================
# EventNest - end-to-end test for the admin surface (ARCHITECTURE.md section 7.5)
#
# Run from WSL (backend must be running):
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-admin.ps1
#
# The four things this suite exists to prove, because each is a property of the
# running system that mocking cannot demonstrate:
#   1. Suspending a user breaks their ALREADY-ISSUED token immediately
#      (the filter now checks status on every authenticated request).
#   2. The organizer approval queue is the trust gate: APPROVE unlocks an
#      organizer's ability to create events; nothing else does.
#   3. Report -> RESOLVE + takedown removes an event from discovery.
#   4. Category CRUD is database-driven and public discovery reflects it.
# =============================================================================
$ErrorActionPreference = "Stop"
trap { Write-Host "`nSETUP FAILURE: $_" -ForegroundColor Red; exit 2 }
$base = "http://localhost:8080/api/v1"
$script:pass = 0
$script:fail = 0
$runId = Get-Random -Minimum 100000 -Maximum 999999

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
    if ($r.status -ne 200) { throw "login failed for $email -> $($r.status)" }
    return ($r.body | ConvertFrom-Json)
}

# ---------------------------------------------------------------- 1. gates --
Write-Host "`n--- 1. Role gates on /admin ---"
$adm = Login "admin@eventnest.dev" "Admin@12345"
$admToken = $adm.accessToken
$org = Login "rahul@xyz.edu.in" "Organizer@123"

$r = Invoke GET "/admin/stats"
Check "anonymous -> /admin/stats 401" 401 $r.status
$r = Invoke GET "/admin/stats" $null $org.accessToken
Check "organizer -> /admin/stats 403" 403 $r.status

# ------------------------------------------------------- 2. stats shape --
Write-Host "`n--- 2. Platform stats ---"
$r = Invoke GET "/admin/stats" $null $admToken
Check "admin -> /admin/stats 200" 200 $r.status
$stats = $r.body | ConvertFrom-Json
Check "  ...users counted" $true ($stats.users -gt 0)
Check "  ...events counted" $true ($stats.events -gt 0)
Check "  ...trend is 31 zero-filled days" 31 @($stats.trend).Count
Check "  ...trend rows shaped {day,events,registrations,users}" $true `
      ($stats.trend[0].PSObject.Properties.Name -contains "registrations")

# ------------------------------------------------------- 3. users list --
Write-Host "`n--- 3. Admin user list ---"
$r = Invoke GET "/admin/users?q=admin&page=0&size=10" $null $admToken
Check "GET /admin/users?q=admin -> 200" 200 $r.status
$users = $r.body | ConvertFrom-Json
Check "  ...finds the admin" $true `
      (@($users.content | Where-Object { $_.email -eq "admin@eventnest.dev" }).Count -eq 1)

# ----------------------------------------------- 4. SUSPEND takes effect --
Write-Host "`n--- 4. Suspension breaks existing tokens immediately ---"
$n = Get-Random -Minimum 10000 -Maximum 99999
$studentEmail = "susp-$n@example.edu.in"
$r = Invoke POST "/auth/register/student" ("{`"fullName`":`"Suspend Test`",`"email`":`"$studentEmail`",`"password`":`"Passw0rd!xyz`",`"course`":`"B.Tech CSE`",`"graduationYear`":2027,`"city`":`"Pune`",`"state`":`"Maharashtra`"}")
if ($r.status -ne 201) { throw "could not create suspend-test student -> $($r.status)" }
$victim = $r.body | ConvertFrom-Json
$victimToken = $victim.accessToken
$victimRefresh = $victim.refreshToken

$r = Invoke GET "/auth/me" $null $victimToken
Check "victim token works before suspension" 200 $r.status

$r = Invoke GET "/admin/users?q=suspend&page=0&size=10" $null $admToken
$victimRow = ($r.body | ConvertFrom-Json).content | Where-Object { $_.email -eq $studentEmail }
$victimId = $victimRow.id
$r = Invoke PATCH "/admin/users/$victimId/status" '{"status":"SUSPEND"}' $admToken
Check "admin SUSPEND -> 200" 200 $r.status
Check "  ...result status SUSPENDED" "SUSPENDED" (($r.body | ConvertFrom-Json).status)

$r = Invoke GET "/auth/me" $null $victimToken
Check "suspended token -> 401 immediately" 401 $r.status
$r = Invoke POST "/auth/refresh" ("{`"refreshToken`":`"$victimRefresh`"}")
Check "  ...refresh token was revoked too" 401 $r.status

$r = Invoke PATCH "/admin/users/$victimId/status" '{"status":"ACTIVATE"}' $admToken
Check "ACTIVATE -> 200" 200 $r.status
$r = Login $studentEmail "Passw0rd!xyz"
Check "re-activated account can log back in" $true ($r.accessToken.Length -gt 100)

# ----------------------------------------- 4b. cannot suspend last admin --
$admRow = ($((Invoke GET "/admin/users?q=admin@eventnest.dev" $null $admToken).body | ConvertFrom-Json)).content | Where-Object { $_.email -eq "admin@eventnest.dev" }
$r = Invoke PATCH "/admin/users/$($admRow.id)/status" '{"status":"SUSPEND"}' $admToken
Check "guard: cannot suspend the last active admin" 409 $r.status

# ------------------------------------ 5. organizer approval queue (D19) --
Write-Host "`n--- 5. Organizer approval is the trust gate ---"
$r = Invoke GET "/colleges" 
$collegeId = ($r.body | ConvertFrom-Json).content[0].id
$orgEmail = "neworg-$runId@example.edu.in"
$r = Invoke POST "/auth/register/organizer" ("{`"fullName`":`"New Organizer`",`"email`":`"$orgEmail`",`"password`":`"Organizer@123`",`"collegeId`":$collegeId,`"designation`":`"Coordinator`",`"department`":`"CSE`",`"phone`":`"9876501234`"}")
if ($r.status -ne 201) { throw "organizer register failed -> $($r.status) $($r.body)" }
$newOrg = $r.body | ConvertFrom-Json
$newOrgToken = $newOrg.accessToken
Check "new organizer is PENDING" "PENDING" $newOrg.user.organizer.verificationStatus

$r = Invoke GET "/admin/organizers?status=PENDING&page=0&size=100" $null $admToken
$pending = $r.body | ConvertFrom-Json
$pendingRow = $pending.content | Where-Object { $_.email -eq $orgEmail }
Check "  ...appears in the approval queue" $true ($null -ne $pendingRow)

$catId = ((Invoke GET "/categories").body | ConvertFrom-Json)[0].id
$eventBody = @"
{
  "title": "Post-Approval Hackathon $runId",
  "summary": "Created right after approval.",
  "description": "A demo event created by a freshly approved organizer.",
  "categoryId": $catId,
  "mode": "OFFLINE",
  "venue": "Main Auditorium",
  "city": "Kolhapur",
  "state": "Maharashtra",
  "startsAt": "2026-12-15T09:00:00Z",
  "endsAt": "2026-12-16T09:00:00Z",
  "registrationDeadline": "2026-12-10T23:59:00Z",
  "registrationMode": "INTERNAL",
  "maxParticipants": 120,
  "tags": ["hackathon"],
  "coordinatorName": "Prof. Test",
  "coordinatorEmail": "test@example.edu.in",
  "coordinatorPhone": "9876543210",
  "schedule": []
}
"@
$r = Invoke POST "/organizer/events" $eventBody $newOrgToken
Check "PENDING organizer creating an event -> 403" 403 $r.status

$r = Invoke PATCH "/admin/organizers/$($pendingRow.id)/approval" '{"action":"APPROVE"}' $admToken
Check "admin APPROVE -> 200" 200 $r.status
Check "  ...result APPROVED" "APPROVED" (($r.body | ConvertFrom-Json).verificationStatus)

$r = Invoke POST "/organizer/events?publish=true" $eventBody $newOrgToken
Check "approved organizer publishes immediately -> 201" 201 $r.status
$newEvent = $r.body | ConvertFrom-Json
$newEventId = $newEvent.summary.id
Check "  ...status PUBLISHED" "PUBLISHED" $newEvent.summary.status

# --------------------------------------------- 6. category CRUD (D16) --
Write-Host "`n--- 6. Category CRUD ---"
$catName = "Robotics-$runId"
$r = Invoke POST "/admin/categories" ("{`"name`":`"$catName`",`"icon`":`"Bot`",`"description`":`"Test category`",`"displayOrder`":99}") $admToken
Check "admin creates category -> 201" 201 $r.status
$newCat = $r.body | ConvertFrom-Json
Check "  ...slug auto-generated" $true ($newCat.slug -match "^robotics-")
$r = Invoke GET "/categories"
$public = $r.body | ConvertFrom-Json
Check "  ...visible in public discovery" $true (@($public | Where-Object { $_.id -eq $newCat.id }).Count -eq 1)
$r = Invoke PATCH "/admin/categories/$($newCat.id)" ("{`"name`":`"$catName Renamed`"}") $admToken
Check "PATCH category -> 200" 200 $r.status
Check "  ...name updated" "$catName Renamed" (($r.body | ConvertFrom-Json).name)
$r = Invoke DELETE "/admin/categories/$($newCat.id)" $null $admToken
Check "DELETE category -> 204" 204 $r.status
$r = Invoke GET "/categories"
$public = $r.body | ConvertFrom-Json
Check "  ...gone from public discovery" $true (($public | Where-Object { $_.id -eq $newCat.id }).Count -eq 0)
$r = Invoke POST "/admin/categories" ("{`"name`":`"$catName`"}") $null
Check "anonymous -> category create 401" 401 $r.status

# --------------------------------------------- 7. event moderation ----
Write-Host "`n--- 7. Event moderation: feature + takedown ---"
$r = Invoke GET "/admin/events?q=$runId&page=0&size=10" $null $admToken
$row = ($r.body | ConvertFrom-Json).content | Where-Object { $_.id -eq $newEventId }
Check "new event visible in admin queue" $true ($null -ne $row)
Check "  ...not featured yet" "False" $row.featured

$r = Invoke PATCH "/admin/events/$newEventId/moderation" '{"action":"FEATURE"}' $admToken
Check "FEATURE -> 200" 200 $r.status
Check "  ...featured true" "True" (($r.body | ConvertFrom-Json).featured)
$r = Invoke PATCH "/admin/events/$newEventId/moderation" '{"action":"UNFEATURE"}' $admToken
Check "UNFEATURE -> 200" 200 $r.status

# --------------------------------------------- 8. reports -> takedown --
Write-Host "`n--- 8. Report then takedown ---"
$newSlug = $newEvent.summary.slug
$r = Invoke POST "/events/$newEventId/report" '{"reason":"SPAM","details":"Suspicious links"}' $admToken
Check "admin files a report (any authed user can) -> 201" 201 $r.status
$rep = $r.body | ConvertFrom-Json
$reportId = $rep.id
Check "  ...status OPEN" "OPEN" $rep.status
$r = Invoke POST "/events/$newEventId/report" '{"reason":"SCAM"}'
Check "anonymous -> report 401" 401 $r.status

$r = Invoke GET "/admin/reports?status=OPEN&page=0&size=50" $null $admToken
$openRep = ($r.body | ConvertFrom-Json).content | Where-Object { $_.id -eq $reportId }
Check "  ...report in the admin queue" $true ($null -ne $openRep)
Check "  ...queue row names the event" $newSlug $openRep.eventSlug

$r = Invoke PATCH "/admin/reports/$reportId" '{"action":"RESOLVE","note":"Verified fake","takedown":true}' $admToken
Check "RESOLVE + takedown -> 200" 200 $r.status
Check "  ...report RESOLVED" "RESOLVED" (($r.body | ConvertFrom-Json).status)

$r = Invoke GET "/events/$newSlug"
Check "taken-down event is gone from discovery" 404 $r.status
$r = Invoke GET "/events/$newSlug" $null $org.accessToken
Check "  ...gone for signed-in students too" 404 $r.status

# ------------------------------------------------- 9. clean-ish roundup --
Write-Host "`n--- 9. Second report resolved without takedown ---"
$r = Invoke POST "/events/$newEventId/report" '{"reason":"FAKE_EVENT"}' $admToken
$reportId2 = ($r.body | ConvertFrom-Json).id
$r = Invoke PATCH "/admin/reports/$reportId2" '{"action":"DISMISS","note":"Dupe of #1"}' $admToken
Check "DISMISS without takedown -> 200" 200 $r.status
# event was already cancelled by takedown #1, so discovery stays closed
$r = Invoke GET "/events/$newSlug"
Check "  ...event still absent (was already taken down)" 404 $r.status

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  PASSED: $script:pass   FAILED: $script:fail"
Write-Host "=========================================" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 } else { exit 0 }