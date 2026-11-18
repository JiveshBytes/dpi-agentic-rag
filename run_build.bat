cd /d d:\POC\mosip-dpi-agentic-rag
del /f /q target\classes\tf\dpi\mosip\chunking\*.class 2>nul
mvn compile -q -DskipTests
if %ERRORLEVEL% EQU 0 (
    echo COMPILED_OK
) else (
    echo COMPILED_FAILED
)