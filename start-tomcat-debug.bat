@echo off
REM Starts Tomcat 10.1 in JPDA debug mode so VS Code / Antigravity can attach
REM to 127.0.0.1:8000 (matches .vscode/launch.json "Attach to Tomcat").
REM Double-click this file, wait for the new window to say "Server startup",
REM then run the "Attach to Tomcat" debug config in your editor.

set "CATALINA_HOME=C:\Program Files\Apache Software Foundation\Tomcat 10.1"
set "JPDA_TRANSPORT=dt_socket"
set "JPDA_ADDRESS=127.0.0.1:8000"

echo Starting Tomcat with JPDA debug port 8000...
call "%CATALINA_HOME%\bin\catalina.bat" jpda start

echo.
echo Tomcat launch triggered. Give it ~20-30 seconds to finish deploying,
echo then attach your debugger to 127.0.0.1:8000.
pause
