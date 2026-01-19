#!/usr/bin/env pwsh
# Build and run Hytale server with plugin

# Save current directory
$originalPath = Get-Location

# Configuration
$serverPath = [Environment]::GetFolderPath('Desktop') + "\HytaleServer"
$serverJarPath = "$serverPath\Server\HytaleServer.jar"
$assetsPath = "$serverPath\Assets.zip"

# Force Java 21 for build/runtime
$javaHome21 = "$env:USERPROFILE\.jabba\jdk\temurin@21.0.8"
if (Test-Path "$javaHome21\bin\java.exe") {
    $env:JAVA_HOME = $javaHome21
    $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
    Write-Host "Using JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Yellow
} else {
    Write-Host "Warning: Java 21 not found at $javaHome21. Using current JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Yellow
}

Write-Host "Building plugin..." -ForegroundColor Cyan
./gradlew clean fatJar

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Build successful!" -ForegroundColor Green

$jarFile = "build/libs/TaleShop-1.0-SNAPSHOT-all.jar"
$modsPath = "$serverPath\mods"

# Create mods directory if it doesn't exist
if (!(Test-Path $modsPath)) {
    Write-Host "Creating mods directory..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $modsPath | Out-Null
}

Write-Host "Copying plugin to server..." -ForegroundColor Cyan
Copy-Item $jarFile -Destination $modsPath -Force

Write-Host "Starting Hytale server..." -ForegroundColor Green

if (!(Test-Path $serverJarPath)) {
    Write-Host "Error: HytaleServer.jar not found at: $serverJarPath" -ForegroundColor Red
    Set-Location $originalPath
    exit 1
}

Set-Location $serverPath
java -jar $serverJarPath --assets $assetsPath

# Return to original directory
Set-Location $originalPath
