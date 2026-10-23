@echo off
rem Session 31 A/A pilot on the reference machine. Protocol: docs\benchmarks\2026-10-22-aa-pilot\README.md
rem
rem   tools\aa_pilot_windows.bat check     operator checks only (power plan, Python, JDK, no client running)
rem   tools\aa_pilot_windows.bat prepare   build + tests, one smoke launch per scene; inspect before freeze
rem   tools\aa_pilot_windows.bat run       calibrate, pilot, evaluate (unattended; resumable: run it again)
rem   tools\aa_pilot_windows.bat sitting N calibrate if needed, then at most N pairs (split the series)
rem
rem Nothing here changes machine settings: it checks them and stops if they differ from the protocol.
setlocal
cd /d "%~dp0.."
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "PROTOCOL=docs\benchmarks\2026-10-22-aa-pilot\windows2\protocol.json"
if defined ALACER_AA_PROTOCOL set "PROTOCOL=%ALACER_AA_PROTOCOL%"
set "PY=python"
echo Protocol: %PROTOCOL%

if not exist "%JAVA_HOME%\bin\java.exe" (echo JDK not found: %JAVA_HOME% & exit /b 2)
%PY% -c "import sys; assert sys.version_info >= (3, 9)" || (echo Python 3.9 or newer is required & exit /b 2)
powercfg /getactivescheme | findstr /i "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c" >nul
if errorlevel 1 (
  echo The active power plan is not High performance. Select it first: powercfg /setactive SCHEME_MIN
  exit /b 2
)

if /i "%~1"=="check" goto check
if /i "%~1"=="prepare" goto prepare
if /i "%~1"=="run" goto run
if /i "%~1"=="sitting" goto sitting
echo usage: tools\aa_pilot_windows.bat check^|prepare^|run^|sitting N
exit /b 2

:check
%PY% tools\aa_pilot.py plan --protocol %PROTOCOL% || exit /b 1
%PY% tools\aa_monitor.py 10 || exit /b 1
%PY% tools\aa_pilot.py status --protocol %PROTOCOL%
exit /b %errorlevel%

:prepare
call gradlew.bat build --offline --console=plain || exit /b 1
%PY% -m unittest discover -s tools\tests -p "test_aa_*.py" || exit /b 1
%PY% tools\aa_pilot.py smoke --protocol %PROTOCOL% || exit /b 1
echo Inspect the smoke records next to %PROTOCOL% before freezing.
echo Then freeze explicitly: %PY% tools\aa_pilot.py freeze --protocol %PROTOCOL%
exit /b 0

:run
%PY% tools\aa_pilot.py calibrate --protocol %PROTOCOL% || exit /b 1
%PY% tools\aa_pilot.py pilot --protocol %PROTOCOL% || exit /b 1
%PY% -c "import numpy" || (echo numpy is needed for evaluate: py -m pip install numpy & exit /b 1)
%PY% tools\aa_pilot.py evaluate --protocol %PROTOCOL%
exit /b %errorlevel%

:sitting
if "%~2"=="" (echo usage: tools\aa_pilot_windows.bat sitting N & exit /b 2)
%PY% tools\aa_pilot.py calibrate --protocol %PROTOCOL% || exit /b 1
%PY% tools\aa_pilot.py pilot --protocol %PROTOCOL% --max-pairs %~2 || exit /b 1
%PY% tools\aa_pilot.py status --protocol %PROTOCOL%
exit /b %errorlevel%
