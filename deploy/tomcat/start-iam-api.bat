@echo off
rem Starts the IAM API Tomcat in this window (port 8081). Close the window or press Ctrl+C to stop it.
rem If you see "JAVA_HOME is not defined", set it first, for example:
rem    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "CATALINA_HOME=D:\Apache Tomcat\apache-tomcat-9.0.113"
set "CATALINA_BASE=D:\tomcat-iam-base"
set "CATALINA_OPTS=-Diam.config=C:\Projects\iam-lifecycle-rbac-platform\config.properties"

if not exist "%CATALINA_BASE%\conf\server.xml" goto nobase
if defined JAVA_HOME goto run
if defined JRE_HOME goto run
echo JAVA_HOME is not defined. Set it to your Java 17 folder, then run this again.
pause
goto end

:run
call "%CATALINA_HOME%\bin\catalina.bat" run
goto end

:nobase
echo %CATALINA_BASE% is not set up yet. Run setup-iam-api-tomcat.bat first.
pause

:end
