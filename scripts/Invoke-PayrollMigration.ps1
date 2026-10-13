param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('info','preflight','baseline-reviewed-backup','migrate','validate','validate-schema')]
    [string]$Command,
    [string]$Confirmation = ''
)
$ErrorActionPreference = 'Stop'
$payrollJar = Join-Path $PSScriptRoot '../target/payroll-api-1.0.0.jar'
if (!(Test-Path -LiteralPath $payrollJar)) { throw 'Build the reviewed JAR first: mvn.cmd -B package' }
$payrollArgs = @('-Dloader.main=com.akash.payroll.migration.MigrationRunner', '-cp', $payrollJar,
    'org.springframework.boot.loader.launch.PropertiesLauncher', $Command)
if ($Confirmation) { $payrollArgs += $Confirmation }
& java @payrollArgs
if ($LASTEXITCODE -ne 0) { throw 'Migration utility failed. Stop; do not baseline again, repair history, or retry DDL blindly.' }
