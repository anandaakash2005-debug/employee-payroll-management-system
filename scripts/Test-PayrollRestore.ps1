param([string]$Backup = (Join-Path $PSScriptRoot '../payroll_db_before_v3_20261011.sql'))
$ErrorActionPreference = 'Stop'
$payrollBackup = (Resolve-Path -LiteralPath $Backup).Path
# Never execute this backup in an existing server. It contains destructive restore statements.
# Restrict this helper to the reviewed single-schema mysqldump format.
foreach ($line in [IO.File]::ReadLines($payrollBackup)) {
    if ($line -match '(?i)^\s*(CREATE\s+DATABASE|USE\s|SOURCE\s|CONNECT\s|\\!|SYSTEM\s)') {
        throw 'Multi-schema or client-command dump requires a separate isolated restore review.'
    }
}
$payrollContainer = 'payroll-restore-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$payrollTarget = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../target'))
$payrollUtf8 = Join-Path $payrollTarget ($payrollContainer + '.sql')
$payrollSaved = @{}
foreach ($key in 'DB_URL','DB_USERNAME','DB_PASSWORD','PAYROLL_TIMEZONE','MYSQL_ROOT_PASSWORD','MYSQL_ROOT_HOST','MYSQL_PWD') {
    $payrollSaved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
function Invoke-LocalDocker {
    param([string[]]$Arguments)
    $ErrorActionPreference = 'Continue' # Suppress native stderr, which may contain backup row values.
    $output = & docker @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Docker operation failed; details suppressed to protect backup data.' }
    return $output
}
function Invoke-RehearsalMigration {
    param([string]$Command, [string]$Confirmation = '')
    $jar = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../target/payroll-api-1.0.0.jar')).Path
    $arguments = @('run','--rm','--network',"container:$payrollContainer",'--mount',"type=bind,source=$jar,target=/app/payroll.jar,readonly",
        '-e','DB_URL','-e','DB_USERNAME','-e','DB_PASSWORD','-e','PAYROLL_TIMEZONE','eclipse-temurin:17-jre',
        'java','-Dloader.main=com.akash.payroll.migration.MigrationRunner','-cp','/app/payroll.jar',
        'org.springframework.boot.loader.launch.PropertiesLauncher',$Command)
    if ($Confirmation) { $arguments += $Confirmation }
    Invoke-LocalDocker $arguments
}
try {
    # Windows PowerShell Out-File creates UTF-16 dumps. MySQL needs a UTF-8 copy.
    # BOM-aware strict decoding preserves text and leaves the original backup untouched.
    $encoding = New-Object Text.UTF8Encoding($false, $true)
    $reader = New-Object IO.StreamReader($payrollBackup, $encoding, $true)
    try { [IO.File]::WriteAllText($payrollUtf8, $reader.ReadToEnd(), $encoding) }
    finally { $reader.Dispose() }
    $env:MYSQL_ROOT_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:MYSQL_PWD = $env:MYSQL_ROOT_PASSWORD
    $env:MYSQL_ROOT_HOST = '%'
    Invoke-LocalDocker @('run','--detach','--name',$payrollContainer,'--publish','127.0.0.1::3306',
        '--env','MYSQL_ROOT_PASSWORD','--env','MYSQL_ROOT_HOST','--env','MYSQL_DATABASE=payroll_rehearsal','mysql:8.4') | Out-Null
    $ready = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        try {
            Invoke-LocalDocker @('exec','-e','MYSQL_PWD',$payrollContainer,'mysql','--protocol=TCP',
                '--host=127.0.0.1','--user=root','--execute','SELECT 1') | Out-Null
            $ready = $true; break
        } catch { } # Wait for authenticated SQL, not mysqladmin ping (which accepts auth failures).
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw 'Isolated MySQL did not become ready.' }
    Invoke-LocalDocker @('cp',$payrollUtf8,"${payrollContainer}:/tmp/restore.sql") | Out-Null
    Invoke-LocalDocker @('exec','-e','MYSQL_PWD',$payrollContainer,'sh','-c',
        'mysql --user=root payroll_rehearsal < /tmp/restore.sql') | Out-Null
    Write-Output 'Backup restored into isolated MySQL.'
    $binding = Invoke-LocalDocker @('port',$payrollContainer,'3306/tcp')
    if ($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected local port binding.' }
    # Java 17 shares ONLY this new container's network namespace.
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/payroll_rehearsal?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = $env:MYSQL_ROOT_PASSWORD
    if (!$env:PAYROLL_TIMEZONE) { $env:PAYROLL_TIMEZONE = 'Asia/Kolkata' }
    Invoke-RehearsalMigration info
    Invoke-RehearsalMigration preflight
    # Compare every original column/row, including user hashes and audit timestamps, in memory.
    # Decimal casts are safe only AFTER the lossless-conversion preflight passes.
    $queries = @(
        'SELECT id,employee_type,name,department FROM employees ORDER BY id',
        'SELECT id,CAST(monthly_salary AS DECIMAL(12,2)) FROM full_time_employees ORDER BY id',
        'SELECT id,CAST(hourly_rate AS DECIMAL(12,2)),hours_worked FROM part_time_employees ORDER BY id',
        'SELECT id,username,password,role FROM users ORDER BY id',
        'SELECT id,employee_id,pay_basis,previous_rate,new_rate,effective_date,reason,created_at FROM salary_increments ORDER BY id',
        'SELECT id,employee_id,amount,bonus_date,reason,status,created_at FROM employee_bonuses ORDER BY id'
    )
    $before = @()
    foreach ($query in $queries) {
        $before += ,(@(Invoke-LocalDocker @('exec','-e','MYSQL_PWD',$payrollContainer,'mysql','--user=root',
            '--batch','--skip-column-names','payroll_rehearsal','-e',"SET time_zone='+00:00'; $query")) -join "`n")
    }
    # This baseline confirmation applies ONLY to the newly created isolated restore.
    Invoke-RehearsalMigration baseline-reviewed-backup '--confirm-reviewed-backup-and-stopped-writers'
    Invoke-RehearsalMigration migrate '--confirm-stopped-writers'
    Invoke-RehearsalMigration validate
    Invoke-RehearsalMigration validate-schema
    for ($index=0; $index -lt $queries.Count; $index++) {
        $after = @(Invoke-LocalDocker @('exec','-e','MYSQL_PWD',$payrollContainer,'mysql','--user=root',
            '--batch','--skip-column-names','payroll_rehearsal','-e',("SET time_zone='+00:00'; " + $queries[$index]))) -join "`n"
        if ($before[$index] -cne $after) { throw "Legacy data comparison failed for table number $index. Stop." }
    }
    Write-Output 'Restore, V1-V3, Hibernate validation, and comparison of all legacy rows/columns passed.'
    Write-Output "Isolated restore retained for review: $payrollContainer (stop/remove explicitly after review)."
} finally {
    if ([IO.Path]::GetFullPath($payrollUtf8).StartsWith($payrollTarget + [IO.Path]::DirectorySeparatorChar) -and
        (Test-Path -LiteralPath $payrollUtf8)) { Remove-Item -LiteralPath $payrollUtf8 }
    foreach ($key in $payrollSaved.Keys) {
        [Environment]::SetEnvironmentVariable($key, $payrollSaved[$key], 'Process')
    }
    Write-Output "Restore container name: $payrollContainer"
}
