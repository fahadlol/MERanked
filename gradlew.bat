@echo off
setlocal
if exist ".gradle\buildOutputCleanup\buildOutputCleanup.lock" (
  echo Removing stale Gradle lock...
  del /f /q ".gradle\buildOutputCleanup\buildOutputCleanup.lock" 2>nul
)
call gradlew.bat %* 2>nul
if errorlevel 1 (
  if exist "gradlew.bat.real" call gradlew.bat.real %*
  else (
    echo Run: gradle wrapper  then  gradlew build
    exit /b 1
  )
)
