# =============================================================================
# EventNest - end-to-end test for discovery + organizer event management
#
# Run from WSL (backend must be running):
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-events.ps1
#
# Covers ARCHITECTURE.md sections 7.2 and 7.4. Every assertion is a real HTTP
# call: the things worth proving here (state transitions, ownership, role
# gating, the anonymous/public split) are properties of the running system.
# =============================================================================
$ErrorActionPreference = "Stop"
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

# PowerShell 5.1 pitfall: @("[]" | ConvertFrom-Json).Count is 1, not 0 - the
# pipeline hands @() the empty array as a single item. Assigning first and then
# wrapping is the only way to get the honest element count.
function CountOf($json) {
    $parsed = $json | ConvertFrom-Json
    if ($null -eq $parsed) { return 0 }
    if ($parsed -is [Array]) { return @($parsed).Count }
    return 1
}

function CodeOf($body) {
    $m = [regex]::Match($body, '"code"\s*:\s*"([A-Z_]+)"')
    if ($m.Success) { return $m.Groups[1].Value } return ""
}

function GetProp($json, $name) {
    $m = [regex]::Match($json, '"' + $name + '"\s*:\s*"([^"]*)"')
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

function Login($email, $pw) {
    $r = Invoke POST "/auth/login" "{`"email`":`"$email`",`"password`":`"$pw`"}"
    return ($r.body | ConvertFrom-Json).accessToken
}

Write-Host "`n=== EventNest: discovery + organizer events ===" -ForegroundColor Cyan

$orgToken   = Login "rahul@xyz.edu.in" "Organizer@123"
$pendToken  = Login "priya@example.edu.in" "Organizer@123"
$studentTok = Login "abhishek@student.edu.in" "Student@123"
$adminTok   = Login "admin@eventnest.dev" "Admin@12345"
Check "approved organizer logs in" $true ($orgToken.Length -gt 100)

# --- 1. Anonymous discovery surfaces --------------------------------------
$r = Invoke GET "/categories"
Check "GET /categories -> 200" 200 $r.status
$cats = ($r.body | ConvertFrom-Json)
Check "  ...seeded taxonomy present" $true ($cats.Count -ge 10)
Check "  ...each has a slug and icon" $true ($cats[0].slug -and $cats[0].icon)
$categoryId = $cats[0].id
$catSlug = $cats[0].slug

$r = Invoke GET "/colleges"
Check "GET /colleges -> 200" 200 $r.status
$colleges = ($r.body | ConvertFrom-Json)
Check "  ...returns a page envelope (content key)" $true ($r.body -match '"content"')
Check "  ...only VERIFIED colleges are listed" 0 ([regex]::Matches($r.body, '"verified"\s*:\s*false').Count)
$collegeId = $colleges.content[0].id

$r = Invoke GET "/colleges/$($colleges.content[0].slug)"
Check "GET /colleges/{slug} -> 200" 200 $r.status

$r = Invoke GET "/meta/locations"
Check "GET /meta/locations -> 200" 200 $r.status
Check "  ...returns an array" $true ($r.body.TrimStart().StartsWith("["))

$r = Invoke GET "/events"
Check "GET /events (anonymous) -> 200" 200 $r.status
Check "  ...empty page envelope" 0 ($r.body | ConvertFrom-Json).totalElements

$r = Invoke GET "/events/home"
Check "GET /events/home -> 200" 200 $r.status
$homePage = $r.body | ConvertFrom-Json
Check "  ...all six rails present" $true (
    $null -ne $homePage.recommended -and $null -ne $homePage.nearYou -and $null -ne $homePage.trending -and
    $null -ne $homePage.byCategory -and $null -ne $homePage.closingSoon -and $null -ne $homePage.newlyAdded)

# --- 2. Role gating on writes ---------------------------------------------
$payload = @"
{
  "title": "Kolhapur Inter-College Hackathon 2026",
  "summary": "A 24 hour build sprint across all engineering colleges in the district.",
  "description": "Bring a team of up to 4 and ship a working prototype. Judged by industry mentors.",
  "categoryId": $categoryId,
  "mode": "OFFLINE",
  "venue": "CS Building, Auditorium",
  "addressLine": "Plot 4, Rajaram Road",
  "city": "Kolhapur",
  "state": "Maharashtra",
  "startsAt": "2026-12-15T09:00:00Z",
  "endsAt": "2026-12-16T09:00:00Z",
  "registrationDeadline": "2026-12-10T23:59:00Z",
  "registrationMode": "INTERNAL",
  "maxParticipants": 120,
  "teamSizeMin": 1,
  "teamSizeMax": 4,
  "prizeTitle": "Winner takes all",
  "prizeAmount": 25000,
  "prizeCurrency": "INR",
  "tags": ["hackathon", "coding", "24h"],
  "coordinatorName": "Prof. Sharma",
  "coordinatorEmail": "events@xyz.edu.in",
  "coordinatorPhone": "9876543210",
  "schedule": [
    { "time": "09:00", "title": "Check-in and breakfast" },
    { "time": "10:00", "title": "Problem statement released" },
    { "time": "09:00 next day", "title": "Demos and judging" }
  ]
}
"@

$r = Invoke POST "/organizer/events" $payload $studentTok
Check "student creating an event -> 403" 403 $r.status
# Layer 1 (the URL matcher) rejects the role before EventCommandService.create runs,
# so this 403 carries Spring Security's denial, not the service's message. The
# service's own guards are exercised by the PENDING-organizer case just below.
Check "  ...rejected by the role gate, not a 500" $true ($r.status -eq 403)

$r = Invoke POST "/organizer/events" $payload $pendToken
Check "PENDING organizer creating an event -> 403" 403 $r.status
Check "  ...says it is awaiting verification" $true ($r.body -match "verif")

$r = Invoke POST "/organizer/events" $payload
Check "anonymous creating an event -> 401" 401 $r.status

# --- 3. Approved organizer creates a draft --------------------------------
$r = Invoke POST "/organizer/events" $payload $orgToken
Check "approved organizer creates (draft) -> 201" 201 $r.status
$created = $r.body | ConvertFrom-Json
$eventId = $created.summary.id
$slug = $created.summary.slug
Check "  ...status DRAFT" "DRAFT" $created.summary.status
Check "  ...slug generated from the title" "kolhapur-inter-college-hackathon-2026" $slug
Check "  ...college taken from the organizer, not the request" $true ($created.summary.college.id -gt 0)
Check "  ...category applied" $true ($created.summary.category.id -eq $categoryId)
Check "  ...registrationOpen false while still a draft" $false $created.summary.registrationOpen
Check "  ...agenda stored as JSON array" $true ($r.body -match '"schedule"')
Check "  ...view counter starts at 0" $true ($r.body -match '"viewCount":0')

# --- 4. Draft is invisible to the public ----------------------------------
$r = Invoke GET "/events/$slug"
Check "anonymous reading a DRAFT by slug -> 404 (not 403)" 404 $r.status

$r = Invoke GET "/events"
Check "draft does not appear in discovery" 0 ($r.body | ConvertFrom-Json).totalElements

$r = Invoke GET "/events/$slug" $null $orgToken
Check "its own organizer can read the draft -> 200" 200 $r.status

# --- 5. Validation ----------------------------------------------------------
$badDates = $payload -replace '"endsAt": "2026-12-16T09:00:00Z"', '"endsAt": "2026-12-14T09:00:00Z"'
$r = Invoke POST "/organizer/events" $badDates $orgToken
Check "end before start -> 400" 400 $r.status
Check "  ...code BAD_REQUEST" "BAD_REQUEST" (CodeOf $r.body)

$r = Invoke POST "/organizer/events" ($payload -replace '"title": "Kolhapur Inter-College Hackathon 2026"', '"title": ""') $orgToken
Check "blank title -> 400 VALIDATION_FAILED" "VALIDATION_FAILED" (CodeOf $r.body)

$noCategory = $payload -replace "`"categoryId`": $categoryId", "`"categoryId`": 999999"
$r = Invoke POST "/organizer/events" $noCategory $orgToken
Check "unknown category -> 400" 400 $r.status

$externalNoUrl = $payload -replace '"registrationMode": "INTERNAL"', '"registrationMode": "EXTERNAL"'
$r = Invoke POST "/organizer/events" $externalNoUrl $orgToken
Check "EXTERNAL registration without a link -> 400" 400 $r.status
Check "  ...names the missing link" $true ($r.body -match "registration link")

# --- 6. Ownership (authorisation layer 3) ----------------------------------
$r = Invoke PUT "/organizer/events/$eventId" $payload $studentTok
Check "student updating someone else's event -> 403 (role gate)" 403 $r.status

# --- 7. Publish -------------------------------------------------------------
$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "PUBLISH" }' $orgToken
Check "organizer publishes a draft -> 200" 200 $r.status
Check "  ...status now PUBLISHED" "PUBLISHED" (($r.body | ConvertFrom-Json).summary.status)

$r = Invoke GET "/events"
$listed = $r.body | ConvertFrom-Json
Check "published event appears in discovery" 1 $listed.totalElements

$r = Invoke GET "/events/$slug"
Check "anonymous can read the published event -> 200" 200 $r.status
$detail = $r.body | ConvertFrom-Json
Check "  ...no myRegistration for an anonymous viewer" 0 ([regex]::Matches($r.body, '"myRegistration"\s*:\s*\{').Count)
Check "  ...view counter incremented" $true ($detail.summary.viewCount -ge 1)

$r = Invoke GET "/events/$slug" $null $studentTok
$detail = $r.body | ConvertFrom-Json
# application.yml sets jackson default-property-inclusion: non_null, so a viewer
# with no registration gets the key *absent* rather than explicit null. The
# frontend reads it as detail.myRegistration ?? null; asserting presence of a
# literal null here would be asserting a contract the API does not have.
Check "signed-in non-registrant omits myRegistration" $false ($r.body -match '"myRegistration"')

$r = Invoke GET "/events/home"
$homePage = $r.body | ConvertFrom-Json
Check "home rails now populated" $true ($homePage.recommended.Count -ge 1)
Check "  ...trending rail has it" $true ($homePage.trending.Count -ge 1)
Check "  ...closing soon has it" $true ($homePage.closingSoon.Count -ge 1)
Check "  ...byCategory keyed by slug" $true (($homePage.byCategory.PSObject.Properties.Name -contains $catSlug))

$r = Invoke GET "/events/$slug/related"
Check "GET related -> 200" 200 $r.status

# --- 8. Search and filters ---------------------------------------------------
$r = Invoke GET "/events?q=hackathon"
Check "search finds it by title" 1 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?q=zzzznothingmatchesthis"
Check "search with no match -> empty" 0 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?city=Kolhapur"
Check "city filter matches" 1 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?city=Nowhere"
Check "city filter misses" 0 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?category=$catSlug"
Check "category filter matches" 1 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?mode=ONLINE"
Check "mode filter misses an offline event" 0 ($r.body | ConvertFrom-Json).totalElements
$r = Invoke GET "/events?includeClosed=true"
Check "includeClosed is accepted" 200 $r.status
$r = Invoke GET "/events?sort=POPULAR"
Check "sort whitelist accepts POPULAR" 200 $r.status
$r = Invoke GET "/events?sort=BogusSort"
Check "unknown sort falls back instead of erroring" 200 $r.status
$r = Invoke GET "/events?registered=true"
Check "registered filter while signed out -> 401" 401 $r.status

# --- 9. The organizer's own views -------------------------------------------
$r = Invoke GET "/organizer/events" $null $orgToken
Check "GET /organizer/events -> 200" 200 $r.status
Check "  ...sees their own event" 1 (CountOf $r.body)

$r = Invoke GET "/organizer/events?status=DRAFT" $null $orgToken
Check "filter by DRAFT -> empty" 0 (CountOf $r.body)
$r = Invoke GET "/organizer/events?status=NONSENSE" $null $orgToken
Check "unknown status -> 400" 400 $r.status

$r = Invoke GET "/organizer/events/dashboard" $null $orgToken
$dash = $r.body | ConvertFrom-Json
Check "dashboard totals published=1" 1 $dash.publishedEvents
Check "dashboard totals total=1" 1 $dash.totalEvents

$r = Invoke GET "/organizer/events/$eventId" $null $orgToken
Check "GET own event detail -> 200" 200 $r.status

$r = Invoke GET "/organizer/events" $null $studentTok
Check "student calling /organizer/* -> 403" 403 $r.status

# --- 10. State machine -------------------------------------------------------
$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "PUBLISH" }' $orgToken
Check "publishing an already-published event is a no-op -> 200" 200 $r.status

$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "SUBMIT" }' $orgToken
Check "SUBMIT on a non-moderated college is a harmless no-op" 200 $r.status

# --- 11. Delete rules --------------------------------------------------------
$r = Invoke DELETE "/organizer/events/$eventId" $null $orgToken
Check "deleting a non-draft -> 409" 409 $r.status
Check "  ...tells them to cancel instead" $true ($r.body -match "cancel")

$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "CANCEL", "reason": "Venue unavailable" }' $orgToken
Check "cancel with a reason -> 200" 200 $r.status
$cancelled = $r.body | ConvertFrom-Json
Check "  ...cancellationReason recorded" "Venue unavailable" $cancelled.cancellationReason
Check "  ...cancelledAt set" $true ($r.body -match '"cancelledAt"\s*:\s*"')

$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "COMPLETE" }' $orgToken
Check "COMPLETE on a cancelled event -> 409 (CANCELLED is terminal)" 409 $r.status
$r = Invoke PATCH "/organizer/events/$eventId/status" '{ "action": "PUBLISH" }' $orgToken
Check "re-opening a CANCELLED event -> 409" 409 $r.status

$r = Invoke GET "/events"
Check "a cancelled event drops out of discovery" 0 ($r.body | ConvertFrom-Json).totalElements

# --- 12. College page --------------------------------------------------------
$r = Invoke GET "/colleges/$collegeId/events"
Check "college events page -> 200" 200 $r.status

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  PASSED: $script:pass   FAILED: $script:fail" `
      -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
Write-Host "=========================================`n" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }