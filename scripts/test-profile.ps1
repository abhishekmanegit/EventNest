# =============================================================================
# EventNest - profile surface: organizer profile, college logo, student profile
#
#   powershell.exe -ExecutionPolicy Bypass -File scripts\test-profile.ps1
#
# Must run AFTER test-auth (uses rahul's seeded login + a student login).
# Does not assert global counts, so it is safe anywhere in the tail of the run.
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
        $status = [int]$resp.StatusCode
        $msg = ""
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        return @{ status = $status; body = $msg }
    }
}

function Upload($path, $token, $bytes, $fileName, $contentType) {
    $boundary = [Guid]::NewGuid().ToString("N")
    $head = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"$fileName`"`r`nContent-Type: $contentType`r`n`r`n"
    $pre = [Text.Encoding]::UTF8.GetBytes($head)
    $tail = [Text.Encoding]::UTF8.GetBytes("`r`n--$boundary--`r`n")
    $ms = New-Object System.IO.MemoryStream
    $ms.Write($pre, 0, $pre.Length)
    $ms.Write($bytes, 0, $bytes.Length)
    $ms.Write($tail, 0, $tail.Length)
    $data = $ms.ToArray()
    $h = @{ Authorization = "Bearer $token"; "Content-Type" = "multipart/form-data; boundary=$boundary" }
    try {
        $r = Invoke-WebRequest -Uri "$base$path" -Method Post -Headers $h -Body $data -UseBasicParsing -TimeoutSec 30
        return @{ status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ status = 0; body = $_.Exception.Message } }
        return @{ status = [int]$resp.StatusCode; body = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { "" } }
    }
}

try {
    $login = Invoke POST "/auth/login" '{"email":"rahul@xyz.edu.in","password":"Organizer@123"}'
    if ($login.status -ne 200) { throw "rahul login failed -> $($login.status)" }
    $orgTok = ($login.body | ConvertFrom-Json).accessToken

    $stu = Invoke POST "/auth/login" '{"email":"abhishek@student.edu.in","password":"Student@123"}'
    if ($stu.status -ne 200) { throw "student login failed -> $($stu.status)" }
    $stuTok = ($stu.body | ConvertFrom-Json).accessToken

    # ------------------------------------------------ organizer profile ----
    Write-Host "`n--- 1. Organizer profile ---"
    $r = Invoke GET "/organizer/profile" $null $orgTok
    Check "GET /organizer/profile -> 200" 200 $r.status
    $p = $r.body | ConvertFrom-Json
    Check "  ...has full name" $true ($null -ne $p.fullName -and $p.fullName.Trim().Length -gt 0)
    Check "  ...has college name" $true ($null -ne $p.collegeName)
    Check "  ...is approved" "APPROVED" $p.verificationStatus
    Check "  ...canPublish" $true $p.canPublish

    $r = Invoke PUT "/organizer/profile" '{"fullName":"Rahul S.","designation":"Faculty","department":"Electronics","phone":"9990001111"}' $orgTok
    Check "PUT /organizer/profile -> 200" 200 $r.status
    Check "  ...designation updated" "Faculty" (($r.body | ConvertFrom-Json).designation)
    $r = Invoke GET "/organizer/profile" $null $orgTok
    Check "  ...persisted (designation)" "Faculty" (($r.body | ConvertFrom-Json).designation)
    Check "  ...persisted (phone)" "9990001111" (($r.body | ConvertFrom-Json).phone)

    $r = Invoke PUT "/organizer/profile" '{"fullName":"   "}' $orgTok
    Check "  ...blank fullName -> 400" 400 $r.status

    $r = Invoke GET "/organizer/profile"
    Check "anonymous GET /organizer/profile -> 401/403" $true (@(401, 403) -contains $r.status)
    $r = Invoke GET "/organizer/profile" $null $stuTok
    Check "student cannot read organizer profile -> 401/403" $true (@(401, 403) -contains $r.status)

    # ------------------------------------------------- student profile -----
    Write-Host "`n--- 2. Student profile ---"
    $r = Invoke GET "/me/profile" $null $stuTok
    Check "GET /me/profile -> 200" 200 $r.status
    $sp = $r.body | ConvertFrom-Json
    Check "  ...has course" $true ($null -ne $sp.course)

    $r = Invoke PUT "/me/profile" '{"course":"B.Tech Computer Science","graduationYear":2028,"city":"Pune","state":"Maharashtra","skills":["Java","React","SQL"],"phone":"9123456780"}' $stuTok
    Check "PUT /me/profile -> 200" 200 $r.status
    $r = Invoke GET "/me/profile" $null $stuTok
    $sp = $r.body | ConvertFrom-Json
    Check "  ...city persisted" "Pune" $sp.city
    Check "  ...skills persisted (count)" 3 @($sp.skills).Count
    $r = Invoke PUT "/me/profile" '{"graduationYear":3000}'
    Check "anonymous PUT /me/profile -> 401/403" $true (@(401, 403) -contains $r.status)

    # --------------------------------------------------- college logo ------
    Write-Host "`n--- 3. College logo upload ---"
    $png = [byte[]](0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A, 1,2,3,4, 0x49,0x45,0x4E,0x44,0xAE,0x42,0x60,0x82)
    $r = Upload "/organizer/college/logo" $orgTok $png "logo.png" "image/png"
    Check "POST /organizer/college/logo -> 201" 201 $r.status
    $logoUrl = ($r.body | ConvertFrom-Json).logoUrl
    Check "  ...returns a URL under /api/v1/files/" $true ($logoUrl -like "/api/v1/files/*")
    $r = Invoke-WebRequest -Uri ("http://localhost:8080" + $logoUrl) -UseBasicParsing
    Check "  ...logo is publicly served -> 200" 200 ([int]$r.StatusCode)
    Check "  ...serves bytes" $true ($r.Content.Length -gt 5)

    $colleges = (Invoke GET "/colleges").body | ConvertFrom-Json
    $ours = @($colleges.content | Where-Object { $_.slug -eq ([regex]::Match($logoUrl, 'files/\d{4}/\d{2}/').Value) }) | Select-Object -First 1
    # logo is on the college detail rather than guessed from the list
    $collegeSlug = ((Invoke GET "/organizer/profile" $null $orgTok).body | ConvertFrom-Json).collegeSlug
    $detail = (Invoke GET "/colleges/$collegeSlug").body | ConvertFrom-Json
    Check "  ...college detail exposes logoUrl" $true ($null -ne $detail.logoUrl)

    # anonymous upload rejected
    $r = Upload "/organizer/college/logo" $null $png "logo.png" "image/png"
    Check "anonymous logo upload -> 401/403" $true (@(401, 403) -contains $r.status)
}
catch {
    Write-Host "`nSETUP FAILURE: $_" -ForegroundColor Red
    exit 2
}

Write-Host "`n========================================="
Write-Host "  PASSED: $script:pass   FAILED: $script:fail"
Write-Host "=========================================`n"
if ($script:fail -gt 0) { exit 1 }