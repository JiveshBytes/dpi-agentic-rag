Set-Location d:\POC\mosip-dpi-agentic-rag
del /f /q target\classes\tf\dpi\mosip\chunking\MetadataBasedSegmenter.class 2>nul
mvn compile -q -DskipTests
if ($LASTEXITCODE -eq 0) { Write-Host "COMPILED_OK" } else { Write-Host "COMPILED_FAILED" }
