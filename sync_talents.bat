@echo off
REM Keeps the talent JSONs in step with the procedures.
REM Leave this window open while you work - it syncs a couple of seconds
REM after MCreator regenerates the code.
cd /d "%~dp0"
where py >nul 2>nul && (py sync_talents.py --watch & goto :eof)
where python >nul 2>nul && (python sync_talents.py --watch & goto :eof)
echo Python is not installed. Get it from python.org, then run this again.
pause
