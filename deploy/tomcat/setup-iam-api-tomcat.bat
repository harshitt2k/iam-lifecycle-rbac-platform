@echo off
rem Run ONCE. Creates a separate Tomcat "base" folder for the IAM API on port 8081.
rem It reuses your Tomcat installation but has its own conf, webapps and logs,
rem so it never touches the Tomcat that runs SailPoint IdentityIQ.
rem Edit the two paths below if yours differ.
set "CATALINA_HOME=D:\Apache Tomcat\apache-tomcat-9.0.113"
set "CATALINA_BASE=D:\tomcat-iam-base"

if not exist "%CATALINA_HOME%\bin\catalina.bat" goto nohome

for %%D in (conf logs temp webapps work) do if not exist "%CATALINA_BASE%\%%D" mkdir "%CATALINA_BASE%\%%D"
copy /Y "%~dp0server.xml" "%CATALINA_BASE%\conf\server.xml" >nul
for %%F in (web.xml catalina.properties logging.properties context.xml) do copy /Y "%CATALINA_HOME%\conf\%%F" "%CATALINA_BASE%\conf\%%F" >nul

echo.
echo Done. The IAM API Tomcat lives in %CATALINA_BASE%
echo Next: build the project, then run deploy-war.bat and start-iam-api.bat
goto end

:nohome
echo Tomcat was not found at %CATALINA_HOME%
echo Edit CATALINA_HOME at the top of this file and run it again.

:end
pause
