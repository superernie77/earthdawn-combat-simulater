@echo off
REM Startet PostgreSQL (Docker), Backend und Frontend.

setlocal
set ROOT=%~dp0

echo Starte PostgreSQL via Docker...
pushd "%ROOT%"
docker compose up -d
if errorlevel 1 (
    echo.
    echo FEHLER: docker compose konnte nicht gestartet werden.
    echo Bitte stelle sicher, dass Docker Desktop laeuft.
    popd
    pause
    exit /b 1
)
popd

echo Warte 3 Sekunden, bis PostgreSQL bereit ist...
timeout /t 3 /nobreak >nul

start "Earthdawn Backend"  cmd /k "cd /d %ROOT%backend && .\mvnw.cmd spring-boot:run"
start "Earthdawn Frontend" cmd /k "cd /d %ROOT%frontend && (if not exist node_modules npm install) && npx ng serve --open"

endlocal
