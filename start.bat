@echo off
setlocal
title English Word Bank Launcher

cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Install JDK 25 or a compatible version.
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found. Install Maven 3.9+ and add it to PATH.
    pause
    exit /b 1
)

echo Starting English Word Bank...
set "MAVEN_REPOSITORY=%CD%.mvn-local-repository"
call mvn "-Dmaven.repo.local=%MAVEN_REPOSITORY%" javafx:run

if errorlevel 1 (
    echo.
    echo [ERROR] Application startup failed. Review the output above.
    pause
)

endlocal
