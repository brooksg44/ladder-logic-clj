@echo off
REM LadderLogicClj execution script for Windows

REM Check if Clojure CLI is installed
where clj >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Clojure CLI (clj) not found. Please install it first.
    exit /b 1
)

REM Pass all arguments to the Clojure application
clj -M:run %*