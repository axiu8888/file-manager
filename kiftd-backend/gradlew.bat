@ECHO OFF
SETLOCAL
SET "GRADLE_HOME=D:\develop\env\gradles\8.14"
IF NOT EXIST "%GRADLE_HOME%\bin\gradle.bat" (
  ECHO Gradle not found: %GRADLE_HOME%
  EXIT /B 1
)
CALL "%GRADLE_HOME%\bin\gradle.bat" %*
