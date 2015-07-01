:@echo off
set /a x=1
set /a y=10
:while
if %x% leq %y% (
  echo %x%

  start "Server #%x%" java -cp ".;../bin" Server 224.0.0.2 2333 "../log/log_server_%x%.txt" %y%

  set /a x+=1
  goto :while
)
:echo Test :D
