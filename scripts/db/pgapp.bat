@echo off
REM ---------------------------------------------------------------
REM EventNest - psql as the application role against the eventnest DB
REM Usage:  pgapp.bat
REM ---------------------------------------------------------------
setlocal
set "PGPASSWORD=eventnest_local_dev"
"C:\Program Files\PostgreSQL\16\bin\psql.exe" -U eventnest_app -d eventnest -h localhost -w %*
