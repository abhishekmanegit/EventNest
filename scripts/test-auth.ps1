# =============================================================================
# EventNest - end-to-end auth smoke test (Phase 1)
#
# Run from WSL:
#   powershell.exe -ExecutionPolicy Bypass -File scripts/test-auth.ps1
#
# Every assertion is a real HTTP call against a running server, because the
# properties worth proving here (status codes, JSON error shape, token
# acceptance, role gating, token rotation) are all properties of the wire
# contract - a unit test on a mocked repository would prove none of them.
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

# Pulls the ApiError "code" field out of a response body.
# Comparing the full body would fail on traceId/timestamp alone, which say
# nothing about behaviour - the code is the contract.
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

function Invoke($method, $path, $body = $null, $token = $null) {
    $h = @{ "Content-Type" = "application/json; charset=utf-8" }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    try {
        if ($null -ne $body) {
            $r = Invoke-WebRequest -Uri "$base$path" -Method $method -Headers $h `
                    -Body ([Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing -TimeoutSec 20
        } else {
            $r = Invoke-WebRequest -Uri "$base$path" -Method $method -Headers $h `
                    -UseBasicParsing -TimeoutSec 20
        }
        return @{ status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ status = 0; body = $_.Exception.Message } }
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        return @{ status = [int]$resp.StatusCode; body = $reader.ReadToEnd() }
    }
}

Write-Host "`n=== EventNest Phase 1: authentication ===" -ForegroundColor Cyan

# --- 1. Discovery is public, /auth/me is not -----------------------------
# Written in Phase 1 when these routes did not exist yet, so 404 was the proof
# they were not accidentally behind the auth wall. They exist now; the real proof
# is a 200 with no Authorization header attached.
$r = Invoke GET "/events"
Check "GET /events is public (200 with no token)" 200 $r.status
$r = Invoke GET "/categories"
Check "GET /categories is public (200 with no token)" 200 $r.status

$r = Invoke GET "/auth/me"
Check "GET /auth/me without token -> 401" 401 $r.status
Check "  ...code UNAUTHORIZED" "UNAUTHORIZED" (CodeOf $r.body)

# --- 2. Wrong password vs unknown email ---------------------------------
$r = Invoke POST "/auth/login" '{"email":"abhishek@student.edu.in","password":"totally-wrong"}'
Check "wrong password -> 401" 401 $r.status
Check "  ...code INVALID_CREDENTIALS" "INVALID_CREDENTIALS" (CodeOf $r.body)

$r2 = Invoke POST "/auth/login" '{"email":"nobody-here@example.com","password":"totally-wrong"}'
Check "unknown email -> same code as wrong password" (CodeOf $r.body) (CodeOf $r2.body)
Check "  ...same message (no user enumeration)" `
      ([regex]::Match($r.body, '"message"\s*:\s*"([^"]+)"').Groups[1].Value) `
      ([regex]::Match($r2.body, '"message"\s*:\s*"([^"]+)"').Groups[1].Value)

# --- 3. Successful student login ----------------------------------------
$r = Invoke POST "/auth/login" '{"email":"abhishek@student.edu.in","password":"Student@123"}'
Check "student login -> 200" 200 $r.status
$login = $r.body | ConvertFrom-Json
$studentToken = $login.accessToken
Check "  ...access token" $true ($login.accessToken.Length -gt 100)
Check "  ...refresh token" $true ($login.refreshToken.Length -gt 20)
Check "  ...tokenType Bearer" "Bearer" $login.tokenType
Check "  ...user.role STUDENT" "STUDENT" $login.user.role

# --- 4. Access token works -----------------------------------------------
$r = Invoke GET "/auth/me" $null $studentToken
Check "GET /auth/me with token -> 200" 200 $r.status
$me = $r.body | ConvertFrom-Json
Check "  ...returns logged-in email" "abhishek@student.edu.in" $me.email
Check "  ...password never echoed back" 0 ([regex]::Matches($r.body, "(?i)password").Count)

# --- 5. Duplicate email ---------------------------------------------------
$body = '{"fullName":"Dup Test","email":"abhishek@student.edu.in","password":"Passw0rd!xyz"}'
$r = Invoke POST "/auth/register/student" $body
Check "duplicate email -> 409" 409 $r.status
Check "  ...code EMAIL_ALREADY_REGISTERED" "EMAIL_ALREADY_REGISTERED" (CodeOf $r.body)

# --- 6. Validation error has fieldErrors ---------------------------------
$r = Invoke POST "/auth/register/student" '{"fullName":"","email":"not-an-email","password":"short"}'
Check "invalid body -> 400" 400 $r.status
Check "  ...code VALIDATION_FAILED" "VALIDATION_FAILED" (CodeOf $r.body)
Check "  ...fieldErrors present" $true ($r.body -match '"fieldErrors"')
Check "  ...fieldErrors names email" $true ($r.body -match '"email"')

# --- 7. New student registers --------------------------------------------
$n = Get-Random -Minimum 10000 -Maximum 99999
$email = "smoketest-$n@example.edu.in"
$body = "{`"fullName`":`"Smoke Test`",`"email`":`"$email`",`"password`":`"Passw0rd!xyz`",`"course`":`"B.Tech CSE`",`"graduationYear`":2027,`"city`":`"Kolhapur`",`"state`":`"Maharashtra`"}"
$r = Invoke POST "/auth/register/student" $body
Check "register new student -> 201" 201 $r.status
$reg = $r.body | ConvertFrom-Json
$newToken = $reg.accessToken
Check "  ...auto-logged-in with tokens" $true ($reg.accessToken.Length -gt 100)

# --- 7b. The "Other…" college flow ----------------------------------------
# A student whose college is missing from the dropdown types the name; the
# backend turns it into a PENDING directory row (idempotent by name) and the
# student registers against it. The same row must stay out of the verified
# directory while still appearing in the includePending=true dropdown.
$r = Invoke POST "/colleges" '{"name":"Test Other College","city":"Pune","state":"Maharashtra"}'
Check "add missing college (Other) -> 201" 201 $r.status
$otherCollege = $r.body | ConvertFrom-Json
Check "  ...has an id" $true ($otherCollege.id -gt 0)
Check "  ...verified false (stays out of the public directory)" $false $otherCollege.verified

$r = Invoke POST "/colleges" '{"name":"  test other college  "}'
Check "same college typed again -> 201 (idempotent)" 201 $r.status
Check "  ...same id returned" $otherCollege.id (($r.body | ConvertFrom-Json).id)

$r = Invoke POST "/colleges" '{"name":""}'
Check "blank college name -> 400" 400 $r.status

$r = Invoke GET "/colleges?q=test+other+college"
Check "pending college hidden from default directory" 0 (($r.body | ConvertFrom-Json).totalElements)

$r = Invoke GET "/colleges?q=test+other+college&includePending=true"
Check "pending college in includePending dropdown" 1 (@(($r.body | ConvertFrom-Json).content).Count)

$n2 = Get-Random -Minimum 10000 -Maximum 99999
$body7b = "{`"fullName`":`"Other College Student`",`"email`":`"other-$n2@example.edu.in`",`"password`":`"Passw0rd!xyz`",`"collegeId`":$($otherCollege.id),`"city`":`"Pune`",`"state`":`"Maharashtra`"}"
$r = Invoke POST "/auth/register/student" $body7b
Check "register student against the new college -> 201" 201 $r.status

# --- 8. Organizer verification gating (D19: approved ONCE, then free) ----
$orgR = Invoke POST "/auth/login" '{"email":"rahul@xyz.edu.in","password":"Organizer@123"}'
Check "approved organizer login -> 200" 200 $orgR.status
$org = ($orgR.body | ConvertFrom-Json)
Check "  ...canPublish true" $true ($org.user.organizer.canPublish)
Check "  ...verificationStatus APPROVED" "APPROVED" $org.user.organizer.verificationStatus
$orgToken = $org.accessToken

$pendR = Invoke POST "/auth/login" '{"email":"priya@example.edu.in","password":"Organizer@123"}'
$pend = ($pendR.body | ConvertFrom-Json)
Check "pending organizer can log in" $true ($pend.accessToken.Length -gt 100)
Check "  ...but canPublish false" $false $pend.user.organizer.canPublish
Check "  ...verificationStatus PENDING" "PENDING" $pend.user.organizer.verificationStatus

# --- 9. Role gating (layer 1 of the three-layer authz model) -------------
$r = Invoke GET "/admin/stats" $null $studentToken
Check "student -> /admin/* is 403" 403 $r.status
$r = Invoke GET "/admin/stats" $null $orgToken
Check "organizer -> /admin/* is 403" 403 $r.status
$r = Invoke GET "/admin/stats"
Check "anonymous -> /admin/* is 401" 401 $r.status
$r = Invoke GET "/organizer/events" $null $studentToken
Check "student -> /organizer/* is 403" 403 $r.status

# --- 10. Admin can reach admin API ---------------------------------------
$adm = Invoke POST "/auth/login" '{"email":"admin@eventnest.dev","password":"Admin@12345"}'
Check "admin login -> 200" 200 $adm.status
$admToken = ($adm.body | ConvertFrom-Json).accessToken
$r = Invoke GET "/admin/stats" $null $admToken
# 200 = hasRole("ADMIN") admitted the token AND the controller now exists.
# When this returned 404, "not 401/403" was still the point of the assertion.
Check "admin -> /admin/* passes role gate (200 = stats endpoint live)" 200 $r.status

# --- 11. Forged token -----------------------------------------------------
$r = Invoke GET "/auth/me" $null "not.a.jwt.token"
Check "garbage token -> 401" 401 $r.status
# Per ARCHITECTURE.md 708, a malformed JWT maps to UNAUTHORIZED, not a
# bespoke code - the client cannot distinguish "malformed" from "expired"
# from "absent", and should not need to.
Check "  ...code UNAUTHORIZED" "UNAUTHORIZED" (CodeOf $r.body)

# --- 12. Refresh rotation, replay detection, logout -----------------------
$refreshBody = "{`"refreshToken`":`"$($reg.refreshToken)`"}"
$r = Invoke POST "/auth/refresh" $refreshBody
Check "refresh -> 200" 200 $r.status
$rotated = $r.body | ConvertFrom-Json
Check "  ...a DIFFERENT refresh token was issued (rotation)" `
      $true ($rotated.refreshToken -ne $reg.refreshToken)

$r = Invoke POST "/auth/refresh" $refreshBody
Check "replaying the now-old refresh token -> 401" 401 $r.status

$r = Invoke POST "/auth/logout" "{`"refreshToken`":`"$($rotated.refreshToken)`"}"
Check "logout -> 204" 204 $r.status
$r = Invoke POST "/auth/refresh" "{`"refreshToken`":`"$($rotated.refreshToken)`"}"
Check "token revoked by logout cannot be reused" 401 $r.status

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "  PASSED: $script:pass   FAILED: $script:fail" `
      -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
Write-Host "=========================================`n" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }