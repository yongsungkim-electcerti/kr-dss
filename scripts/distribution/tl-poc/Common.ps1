$ErrorActionPreference = 'Stop'
$packageRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$config = Get-Content -LiteralPath (Join-Path $packageRoot 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$services = @(
    [pscustomobject]@{ Name='management'; Jar='tl-management.jar'; Port=[int]$config.managementPort; Api='/api/admin/state'; Page='/' },
    [pscustomobject]@{ Name='verification'; Jar='tl-verification.jar'; Port=[int]$config.verificationPort; Api='/api/tl-poc/active'; Page='/tl-poc.html' }
)
function Get-ServiceProcess($service) {
    $file = Join-Path $packageRoot "run/$($service.Name).json"
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    $saved = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$saved.pid)"
    if (-not $process) { return $null }
    $expectedJar = Join-Path $packageRoot "modules/$($service.Jar)"
    if ($process.Name -notin @('java.exe','javaw.exe') -or
        -not $process.CommandLine.Contains("-Dkrtl.package.id=$($saved.token)") -or
        -not $process.CommandLine.Contains($expectedJar)) {
        throw "PID $($saved.pid) does not belong to this package. Refusing to control it."
    }
    return $process
}
function Test-Port([int]$port) {
    $client = New-Object Net.Sockets.TcpClient
    try { $task = $client.ConnectAsync('127.0.0.1',$port); return ($task.Wait(400) -and $client.Connected) }
    catch { return $false } finally { $client.Dispose() }
}
