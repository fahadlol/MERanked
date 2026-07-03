@echo off
echo Stopping Gradle daemons...
if exist gradlew.bat call gradlew.bat --stop 2>nul
if exist gradlew call gradlew --stop 2>nul
echo Removing stale lock files...
if exist ".gradle\buildOutputCleanup\buildOutputCleanup.lock" del /f /q ".gradle\buildOutputCleanup\buildOutputCleanup.lock"
if exist ".gradle\8.12\fileHashes\fileHashes.lock" del /f /q ".gradle\8.12\fileHashes\fileHashes.lock"
echo Done. You can now run: gradlew build
