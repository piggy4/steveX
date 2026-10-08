@echo off
chcp 65001 >nul
setlocal

REM ====================================================================
REM  dsh launcher - double-click to start the DeepSeek Harness.
REM
REM  Usage:
REM    double-click                        start the web profile
REM    start-dsh.bat headless "do a task"  one-shot task
REM    start-dsh.bat --version             print version
REM  Everything after the script name is passed through to dsh.
REM
REM  -- Two things that must not change -------------------------------
REM  1) CI=true is required.
REM     This harness lives inside the steveX repo and has no .git of its
REM     own, so its lefthook postinstall resolves the git root to steveX
REM     and would write hooks into steveX/.git, rewrite core.hooksPath,
REM     and bump repositoryFormatVersion. CI=true is the only switch that
REM     stops it; LEFTHOOK=0 does not. The build script calls pnpm
REM     internally, so the build step needs CI=true as well.
REM
REM  2) Launch through npm run, not pnpm.
REM     pnpm re-runs the implicit install and postinstall before every
REM     script; npm run does neither, which sidesteps the hazard above.
REM     NEVER run npm install in this tree: npm does not support the
REM     workspace: protocol and 333 package.json files here use it.
REM     To install dependencies use:  CI=true pnpm install
REM
REM  This file is deliberately ASCII-only. cmd.exe loses byte alignment
REM  when it re-reads a batch file that contains multi-byte text after a
REM  chcp, and then executes fragments of the comments as commands.
REM ====================================================================

set "CI=true"
set "HARNESS=%~dp0src\agent\deepseek-harness-master"
title dsh - DeepSeek Harness

if not exist "%HARNESS%\package.json" goto :no_harness
cd /d "%HARNESS%"
if errorlevel 1 goto :no_harness

where node >nul 2>nul
if errorlevel 1 goto :no_node

if not exist "node_modules\.modules.yaml" goto :no_deps

if not exist ".dsh-build\client-build-environment.json" goto :need_build
goto :launch

REM -- first run: browser artifacts have never been built --------------
:need_build
echo [setup] Browser artifacts are not built yet. Building now.
echo         This takes a few minutes. Do not close this window.
echo.
call npm run build
if errorlevel 1 goto :fail
echo.
goto :launch

REM -- error branches --------------------------------------------------
:no_harness
echo [ERROR] Harness directory not found:
echo         "%HARNESS%"
goto :fail

:no_node
echo [ERROR] node was not found on PATH. Install Node.js 24 or newer.
goto :fail

:no_deps
echo [ERROR] Dependencies are not installed. Run this first:
echo.
echo         cd /d "%HARNESS%"
echo         set "CI=true" ^&^& pnpm install
echo.
goto :fail

REM -- launch ----------------------------------------------------------
:launch
if "%~1"=="" (set "DSH_ARGS=web") else (set "DSH_ARGS=%*")
echo [launch] dsh %DSH_ARGS%
echo.
call npm run dsh -- %DSH_ARGS%
if errorlevel 1 goto :fail

endlocal
exit /b 0

:fail
echo.
echo [FAILED] See the message above. Press any key to close...
pause >nul
endlocal
exit /b 1
