@echo off
setlocal
title English Word Bank

cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Java。请安装 JDK 25（或兼容版本）后重试。
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Maven。请安装 Maven 3.9+，并将 mvn 加入 PATH 后重试。
    pause
    exit /b 1
)

echo 正在启动 English Word Bank...
set "MAVEN_REPOSITORY=%CD%.mvn-local-repository"
call mvn "-Dmaven.repo.local=%MAVEN_REPOSITORY%" javafx:run

if errorlevel 1 (
    echo.
    echo [错误] 应用启动失败，请查看上方错误信息。
    pause
)

endlocal
