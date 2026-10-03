param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8011
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectRoot ".venv\Scripts\python.exe"
$envPath = Join-Path $projectRoot ".env"
$healthUrl = "http://127.0.0.1:$Port/api/health"

if (-not (Test-Path -LiteralPath $pythonPath)) {
    throw "Virtual environment not found: $pythonPath. Create .venv and install dependencies first."
}

if (-not (Test-Path -LiteralPath $envPath)) {
    throw ".env not found. Copy .env.example to .env and set DEEPSEEK_API_KEY."
}

Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot "import-dotenv.ps1") -Path $envPath

Write-Host "[1/2] Starting Milvus / MinIO / etcd..."
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
}

try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
    if ($health.status -eq "ok") {
        Write-Host "[2/2] Web service is already running: http://127.0.0.1:$Port"
        return
    }
}
catch {
    # No healthy service is listening yet; start Uvicorn below.
}

Write-Host "[2/2] Starting Web service: http://127.0.0.1:$Port"
& $pythonPath -m uvicorn app.main:app --host 127.0.0.1 --port $Port
if ($LASTEXITCODE -ne 0) {
    throw "Uvicorn exited with code $LASTEXITCODE."
}
