@echo off
rem Starts the IAM API Tomcat in this window (port 8081). Close the window or press Ctrl+C to stop it.
rem Tomcat must run on Java 17. This script uses the newest Temurin 17 it finds, and only falls back
rem to JAVA_HOME if no Temurin 17 is installed. Your system JAVA_HOME is not changed.
set "CATALINA_HOME=D:\Apache Tomcat\apache-tomcat-9.0.113"
set "CATALINA_BASE=D:\tomcat-iam-base"
set "CATALINA_OPTS=-Diam.config=C:\Projects\iam-lifecycle-rbac-platform\config.properties"

if not exist "%CATALINA_BASE%\conf\server.xml" goto nobase

set "IAM_JAVA="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if exist "%%~D\bin\java.exe" set "IAM_JAVA=%%~D"
if defined IAM_JAVA goto havejava
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "IAM_JAVA=%JAVA_HOME%"
if defined IAM_JAVA goto havejava
echo No Java found. Install Temurin 17 (https://adoptium.net) and run this again.
pause
goto end

:havejava
set "JAVA_HOME=%IAM_JAVA%"
set "JRE_HOME="
echo Using Java: %JAVA_HOME%
call "%JAVA_HOME%\bin\java.exe" -version
call "%CATALINA_HOME%\bin\catalina.bat" run
goto end

:nobase
echo %CATALINA_BASE% is not set up yet. Run setup-iam-api-tomcat.bat first.
pause

:end
