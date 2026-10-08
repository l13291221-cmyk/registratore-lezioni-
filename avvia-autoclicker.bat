@echo off
rem Doppio clic per avviare l'autoclicker (le opzioni vengono passate all'exe).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0avvia-autoclicker.ps1" %*
