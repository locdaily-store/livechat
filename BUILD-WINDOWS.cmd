@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>nul
if errorlevel 1 (echo Gradle is not in PATH. Use GitHub Actions build.yml or install Gradle 9.2.1. & pause & exit /b 1)
java -version
echo.
echo Minecraft 1.21.1  [1]
echo Minecraft 1.21.11 [2]
set /p ver=Choose [1/2]: 
if "%ver%"=="1" set "mc=1.21.1"
if "%ver%"=="2" set "mc=1.21.11"
if not defined mc (echo Invalid choice & pause & exit /b 1)
gradle -p "builds/%mc%" --no-daemon build
if errorlevel 1 (echo BUILD FAILED. Review Gradle output. & pause & exit /b 1)
echo.
echo BUILD SUCCEEDED. Check builds/%mc%/build/libs/
pause
