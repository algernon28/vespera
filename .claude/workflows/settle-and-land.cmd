@echo off
rem Runs settle-and-land.groovy on the Groovy the project already pins, with nothing installed.
rem
rem The pom names Groovy once, in <groovy.version>, and Maven has already downloaded that jar into
rem the local repository for the two build scripts under .mvn\scripts. This reads the same version
rem out of the pom and runs the orchestrator straight off those jars, so there is no Groovy on the
rem PATH to keep in step with the build and no plugin execution wrapped around a script that is not
rem part of the build in the first place.
rem
rem   .claude\workflows\settle-and-land.cmd --dry-run "stage 1: the broken verdict"
rem
rem Every argument is passed through untouched; read the header of the .groovy for what they are.

rem setlocal without delayed expansion: it is needed nowhere here, and it silently strips every
rem exclamation mark out of the arguments this forwards, so a work item reading "fix it! now" would
rem reach the agents as "fix it now".
setlocal

set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%..\.." || exit /b 2

if not exist "pom.xml" (
    echo settle-and-land: no pom.xml above %SCRIPT_DIR% -- expected this script to live in .claude\workflows\ 1>&2
    popd
    exit /b 2
)

for /f "tokens=3 delims=<>" %%v in ('findstr /r "<groovy.version>" pom.xml') do set "GROOVY_VERSION=%%v"
if "%GROOVY_VERSION%"=="" (
    echo settle-and-land: no ^<groovy.version^> in pom.xml, so there is no version to run against 1>&2
    popd
    exit /b 2
)

rem Where Maven actually keeps its jars, asked rather than assumed. A settings.xml
rem <localRepository>, or MAVEN_ARGS carrying -Dmaven.repo.local, moves it -- and this script would
rem otherwise fetch into the real repository, look in the default one, and tell the operator to run
rem the command that had just succeeded. The pom already reads it properly for the aspectjweaver
rem agent; this asks the same question the same way.
set "LOCAL_REPO="
for /f "usebackq delims=" %%r in (`call "%CD%\mvnw.cmd" -q -DforceStdout -Dexpression^=settings.localRepository help:evaluate 2^>nul`) do (
    if not defined LOCAL_REPO set "LOCAL_REPO=%%r"
)
if not defined LOCAL_REPO set "LOCAL_REPO=%USERPROFILE%\.m2\repository"
set "GROOVY_REPO=%LOCAL_REPO%\org\apache\groovy"
set "GROOVY_JAR=%GROOVY_REPO%\groovy\%GROOVY_VERSION%\groovy-%GROOVY_VERSION%.jar"
set "GROOVY_JSON_JAR=%GROOVY_REPO%\groovy-json\%GROOVY_VERSION%\groovy-json-%GROOVY_VERSION%.jar"

rem groovy-json is a separate artifact from the core jar and the build has no reason to have pulled
rem it, so fetch it the first time rather than failing on an import the operator cannot see.
if not exist "%GROOVY_JAR%" call :fetch org.apache.groovy:groovy:%GROOVY_VERSION%
if not exist "%GROOVY_JSON_JAR%" call :fetch org.apache.groovy:groovy-json:%GROOVY_VERSION%

if not exist "%GROOVY_JAR%" goto :missing
if not exist "%GROOVY_JSON_JAR%" goto :missing

java -cp "%GROOVY_JAR%;%GROOVY_JSON_JAR%" groovy.ui.GroovyMain "%SCRIPT_DIR%settle-and-land.groovy" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%

:fetch
echo settle-and-land: fetching %~1 into the local repository
call "%CD%\mvnw.cmd" -q dependency:get -Dartifact=%~1
exit /b 0

:missing
echo settle-and-land: could not resolve Groovy %GROOVY_VERSION% under %LOCAL_REPO% -- run .\mvnw.cmd dependency:get -Dartifact=org.apache.groovy:groovy:%GROOVY_VERSION% and see where it puts the jar 1>&2
popd
exit /b 2
