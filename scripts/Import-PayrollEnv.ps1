# Dot-source in a fresh PowerShell process. Values are literal; never execute .env as code.
param([string]$Path = (Join-Path $PSScriptRoot '../.env'))
$ErrorActionPreference = 'Stop'
$payrollValues = @{}
foreach ($payrollLine in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path))) {
    if ($payrollLine -match '^\s*(?:export\s+)?(DB_URL|DB_USERNAME|DB_PASSWORD|PAYROLL_TIMEZONE)\s*=(.*)$') {
        $payrollKey = $Matches[1]
        $payrollValue = $Matches[2].Trim()
        if ($payrollValues.ContainsKey($payrollKey)) { throw "Duplicate variable: $payrollKey" }
        if ($payrollValue.StartsWith('"') -or $payrollValue.StartsWith("'")) {
            if ($payrollValue.Length -lt 2 -or $payrollValue[-1] -ne $payrollValue[0]) {
                throw "Unmatched quotes for $payrollKey"
            }
            $payrollValue = $payrollValue.Substring(1, $payrollValue.Length - 2)
        }
        if ([string]::IsNullOrWhiteSpace($payrollValue)) { throw "Empty variable: $payrollKey" }
        $payrollValues[$payrollKey] = $payrollValue
    }
}
foreach ($payrollKey in 'DB_URL','DB_USERNAME','DB_PASSWORD') {
    if (!$payrollValues.ContainsKey($payrollKey)) { throw "Missing variable: $payrollKey" }
}
foreach ($payrollKey in $payrollValues.Keys) {
    [Environment]::SetEnvironmentVariable($payrollKey, $payrollValues[$payrollKey], 'Process')
}
if (!$payrollValues.ContainsKey('PAYROLL_TIMEZONE')) { $env:PAYROLL_TIMEZONE = 'Asia/Kolkata' }
Remove-Variable payrollValues,payrollLine,payrollKey,payrollValue -ErrorAction SilentlyContinue
