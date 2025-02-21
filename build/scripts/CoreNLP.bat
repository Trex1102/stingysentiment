@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  CoreNLP startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and CORE_NLP_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%$

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\CoreNLP-3.4.1.jar;%APP_HOME%\lib\lucene-core-4.10.3.jar;%APP_HOME%\lib\slf4j-api.jar;%APP_HOME%\lib\ant-contrib-1.0b3.jar;%APP_HOME%\lib\ejml-0.23.jar;%APP_HOME%\lib\lucene-analyzers-common-4.10.3.jar;%APP_HOME%\lib\joda-time.jar;%APP_HOME%\lib\xom-1.2.10.jar;%APP_HOME%\lib\commons-logging.jar;%APP_HOME%\lib\jollyday-0.4.7.jar;%APP_HOME%\lib\AppleJavaExtensions.jar;%APP_HOME%\lib\javax.json.jar;%APP_HOME%\lib\protobuf.jar;%APP_HOME%\lib\jflex-1.5.1.jar;%APP_HOME%\lib\lucene-queryparser-4.10.3.jar;%APP_HOME%\lib\lucene-demo-4.10.3.jar;%APP_HOME%\lib\lucene-queries-4.10.3.jar;%APP_HOME%\lib\appbundler-1.0.jar;%APP_HOME%\lib\junit.jar;%APP_HOME%\lib\log4j-1.2.16.jar;%APP_HOME%\lib\javax.servlet.jar;%APP_HOME%\lib\javacc.jar;%APP_HOME%\lib\slf4j-simple.jar;%APP_HOME%\lib\commons-lang3-3.1.jar

@rem Execute CoreNLP
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %CORE_NLP_OPTS%  -classpath "%CLASSPATH%" edu.stanford.nlp.pipeline.StanfordCoreNLP %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable CORE_NLP_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%CORE_NLP_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
