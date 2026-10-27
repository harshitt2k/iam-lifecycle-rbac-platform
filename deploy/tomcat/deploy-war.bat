@echo off
rem Copies the freshly built target\iam-api.war into the IAM API Tomcat.
rem If Tomcat is already running it redeploys the app by itself within a few seconds.
set "CATALINA_BASE=D:\tomcat-iam-base"
set "WAR=C:\Projects\iam-lifecycle-rbac-platform\target\iam-api.war"

if not exist "%WAR%" goto nowar
if not exist "%CATALINA_BASE%\webapps" goto nobase

copy /Y "%WAR%" "%CATALINA_BASE%\webapps\iam-api.war"
echo.
echo Deployed. The API will be at http://localhost:8081/iam-api/api/health
goto end

:nowar
echo %WAR% not found. In Eclipse run: Run As - Maven build, goals: clean package
goto end

:nobase
echo %CATALINA_BASE% not found. Run setup-iam-api-tomcat.bat first.

:end
pause
