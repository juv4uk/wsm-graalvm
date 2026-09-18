@echo off
setlocal
if "%~1"=="" (
  echo usage: wsm-graalvm.cmd ^<program.lisp^> 1>&2
  exit /b 2
)
set "ROOT=%~dp0.."
"%ROOT%\bin\native-wsm.exe" "%~1" "%ROOT%\authority\lib\surface\semantic-registry.lisp" "%ROOT%\authority"
endlocal
