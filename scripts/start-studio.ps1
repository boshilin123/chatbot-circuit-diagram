param(
    [int]$Port = 2024,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$langgraphExe = Join-Path $projectRoot ".venv\Scripts\langgraph.exe"

if (-not (Test-Path -LiteralPath $langgraphExe)) {
    throw "LangGraph CLI is not installed. Run: .\.venv\Scripts\python.exe -m pip install -e '.[dev]'"
}

$arguments = @("dev", "--port", $Port.ToString())
if ($NoBrowser) {
    $arguments += "--no-browser"
}

Push-Location $projectRoot
try {
    & $langgraphExe @arguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
