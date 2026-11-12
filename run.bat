@echo off
cd /d "%~dp0"
call .venv\Scripts\activate.bat
python -m pc.main
pause
