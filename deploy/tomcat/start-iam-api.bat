@echo off
rem Starts the IAM API Tomcat in this window (port 8081). Close the window or press Ctrl+C to stop it.
rem Tomcat must run on Java 17. If JAVA_HOME is not a JDK, the newest Temurin 17 is used.
set "CATALINA_HOME=D:\Apache Tomcat\apache-tomcat-9.0.113"
set "CATALINA_BASE=D:\tomcat-iam-base"
set "CATALINA_OPTS=-Diam.config=C:\Projects\iam-lifecycle-rbac-platform\config.properties"

if not exist "%CATALINA_BASE%\conf\server.xml" goto nobase
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto run
set "JAVA_HOME="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%~D"
if defined JAVA_HOME goto run
echo No Java 17 found. Install Temurin 17 or set JAVA_HOME to a JDK 17 folder, then run this again.
pause
goto end

:run
echo Using Java: %JAVA_HOME%
call "%CATALINA_HOME%\bin\catalina.bat" run
goto end

:nobase
echo %CATALINA_BASE% is not set up yet. Run setup-iam-api-tomcat.bat first.
pause

:end
