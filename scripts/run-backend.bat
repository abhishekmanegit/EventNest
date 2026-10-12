@echo off
REM ===========================================================================
REM EventNest - run the backend
REM
REM Why a .bat file rather than plain ./mvnw:
REM   This machine runs PostgreSQL and the browser on Windows, and WSL2 cannot
REM   reach a Windows service through localhost (the Hyper-V firewall blocks
REM   inbound connections from WSL). So the JVM runs on the Windows side, where
REM   localhost:5432 works.
REM
REM   Build (Linux Maven, WSL):   ./mvnw package -DskipTests
REM   Run   (Windows JVM, this script)
REM
REM Usage:  run-backend.bat [dev]
REM ===========================================================================
setlocal
set "PROFILE=%~1"
if "%PROFILE%"=="" set "PROFILE=dev"

cd /d "%~dp0..\backend"

echo Starting EventNest API (profile=%PROFILE%) on http://localhost:8080
echo Swagger UI:  http://localhost:8080/swagger-ui.html
echo.

set "JAVA=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe"

"%JAVA%" ^
  -Xmx512m ^
  -Dfile.encoding=UTF-8 ^
  -Duser.timezone=UTC ^
  -jar target\eventnest-api-0.0.1-SNAPSHOT.jar ^
  --spring.profiles.active=%PROFILE%

endlocal