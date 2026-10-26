@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1
call gradlew.bat runClient %*
set "alacerExitCode=%errorlevel%"
popd
if not "%alacerExitCode%"=="0" (
    echo.
    echo Alacer failed to start or exited with an error. Install Eclipse Temurin JDK 25 if it is missing.
    pause
)
exit /b %alacerExitCode%
