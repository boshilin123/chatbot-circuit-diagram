param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

if (-not (Test-Path -LiteralPath $Path)) {
    throw ".env file not found: $Path"
}

foreach ($rawLine in Get-Content -LiteralPath $Path) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -le 0) {
        continue
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()

    if (
        $value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))
    ) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    if ($name) {
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}
