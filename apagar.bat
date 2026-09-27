@echo off
REM Apaga backend (8080) y frontend (4200) por puerto, sin matar otros java/node
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do taskkill /PID %%a /F
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":4200 " ^| findstr "LISTENING"') do taskkill /PID %%a /F
echo Servidores apagados.
pause
