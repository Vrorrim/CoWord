<#
.SYNOPSIS
    Start the English Word Bank desktop application.

.DESCRIPTION
    Keeps Maven dependencies inside this project, avoiding a machine-wide
    Maven cache and making the project easier to move between computers.
#>

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$localRepository = Join-Path $projectRoot '.mvn-local-repository'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw '未找到 Java。请安装 JDK 25（或兼容版本）并重新打开 PowerShell。'
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw '未找到 Maven。请安装 Maven 3.9+，或将 mvn 加入 PATH 后重试。'
}

Write-Host '正在启动 English Word Bank…' -ForegroundColor Cyan
Push-Location $projectRoot
try {
    & mvn "-Dmaven.repo.local=$localRepository" javafx:run
    if ($LASTEXITCODE -ne 0) {
        throw "应用启动失败（Maven 退出码：$LASTEXITCODE）。"
    }
}
finally {
    Pop-Location
}
