@echo off
REM Inicia backend (8080) y frontend (4200) en ventanas separadas
cd /d "%~dp0backend"
start "Botica-Backend" cmd /k ".\mvnw.cmd spring-boot:run"
cd /d "%~dp0frontend"
start "Botica-Frontend" cmd /k "npm start -- --host 0.0.0.0 --port 4200"
echo.
echo Servidores iniciando...
echo   Backend:  http://localhost:8080  (tarda 1-2 min)
echo   Frontend: http://localhost:4200  (tarda 2-3 min)
pause
