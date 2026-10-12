# =============================================================================
# EventNest backend process control (Windows side)
#
#   powershell.exe -ExecutionPolicy Bypass -File scripts\backend.ps1 start
#   powershell.exe -ExecutionPolicy Bypass -File scripts\backend.ps1 stop
#   powershell.exe -ExecutionPolicy Bypass -File scripts\backend.ps1 status
#
# Why this exists: the JVM MUST run on the Windows side. WSL2 cannot reach a
# Windows service over localhost (the Hyper-V firewall drops inbound connections
# from the WSL subnet), so a JVM inside WSL would connect to PostgreSQL fine
# only if Postgres were also inside WSL - and the browser, where the user
# actually is, lives on Windows. Running the API on Windows keeps every hop on
# one side of that firewall: browser -> Vite -> API -> Postgres.
#
# The frontend dev server (Vite) can still run inside WSL: Windows -> WSL
# localhost forwarding works in that direction, which is all Vite needs.
# =============================================================================
param([string]$Action = "status", [string]$Profile = "dev")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root "scripts\.backend.pid"
$logFile = Join-Path $root "scripts\.backend.log"
$jar = Join-Path $root "backend\target\eventnest-api-0.0.1-SNAPSHOT.jar"
$java = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe"
$port = 8080

function Get-ApiPid {
    if (Test-Path $pidFile) {
        $p = [int](Get-Content $pidFile -ErrorAction SilentlyContinue)
        if ($p -and (Get-Process -Id $p -ErrorAction SilentlyContinue)) { return $p }
    }
    # PID file lost or stale - fall back to whoever owns port 8080.
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($c) { return $c[0].OwningProcess }
    return 0
}

function Start-Api {
    $running = Get-ApiPid
    if ($running) { Write-Host "Already running (pid $running) on port $port."; return }
    if (-not (Test-Path $jar)) {
        Write-Host "Jar not found: $jar" -ForegroundColor Red
        Write-Host "Build it first (from WSL):  cd backend && mvn -o package -DskipTests" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Starting EventNest API (profile=$Profile)..."
    $args = @("-Xmx512m", "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC",
              "-jar", $jar, "--spring.profiles.active=$Profile")
    $p = Start-Process -FilePath $java -ArgumentList $args `
            -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" `
            -PassThru -NoNewWindow
    Set-Content -Path $pidFile -Value $p.Id
    Write-Host "  pid=$($p.Id)  logging to scripts\.backend.log"
    Write-Host "  API:     http://localhost:8080"
    Write-Host "  Swagger: http://localhost:8080/swagger-ui.html"
}

function Stop-Api {
    $running = Get-ApiPid
    if (-not $running) { Write-Host "Not running."; return }
    Write-Host "Stopping pid $running..."
    Stop-Process -Id $running -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
    Write-Host "Stopped."
}

function Status-Api {
    $running = Get-ApiPid
    if ($running) {
        Write-Host "RUNNING  pid=$running  port=$port"
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" `
                    -UseBasicParsing -TimeoutSec 5
            Write-Host "  health: $($r.Content)"
        } catch { Write-Host "  health: not responding yet" -ForegroundColor Yellow }
    } else {
        Write-Host "STOPPED"
    }
}

switch ($Action) {
    "start"  { Start-Api }
    "stop"   { Stop-Api }
    "restart" { Stop-Api; Start-Sleep -Seconds 1; Start-Api }
    default  { Status-Api }
}