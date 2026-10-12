@echo off
REM ---------------------------------------------------------------
REM EventNest - run a psql script against the local PostgreSQL 16
REM Usage:  pg.bat <script.sql>
REM   e.g.  pg.bat C:\...\EventNest\_db\01_bootstrap.sql
REM ---------------------------------------------------------------
setlocal
if "%~1"=="" (
  echo Usage: pg.bat ^<script.sql^>
  exit /b 1
)
set "PGPASSWORD=postgre123"
"C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -h localhost -w -v ON_ERROR_STOP=1 -f "%~1"
