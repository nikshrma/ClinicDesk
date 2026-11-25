@echo off
echo ========================================================
echo   ClinicDesk - Clinic ^& Patient Records Management
echo ========================================================

rem Check if mvn is in PATH
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" (
        set "PATH=%PATH%;C:\tools\apache-maven-3.9.9\bin"
    ) else (
        echo [ERROR] Maven not found in PATH. Please install Maven or add it to PATH.
        pause
        exit /b 1
    )
)

rem Check JAVA_HOME or java
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Java\jdk-21.0.12" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

echo Starting ClinicDesk...
mvn clean javafx:run
pause
