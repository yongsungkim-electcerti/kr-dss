. (Join-Path $PSScriptRoot 'Common.ps1')
if ($services[0].Port -eq $services[1].Port) { throw 'The two ports must differ.' }
foreach ($s in $services) {
    if ($s.Port -lt 1024 -or $s.Port -gt 65535) { throw 'Port must be 1024..65535.' }
    if (Get-ServiceProcess $s) { throw "$($s.Name) is already running. Use Status-Poc.ps1." }
    if (Test-Port $s.Port) { throw "Port $($s.Port) is occupied. Change config.json; no process was stopped." }
    if (-not (Test-Path -LiteralPath (Join-Path $packageRoot "modules/$($s.Jar)"))) { throw "Missing $($s.Jar)" }
}
$jhome = if ($config.javaHome) { $config.javaHome } else { $env:JAVA_HOME }
$java = if ($jhome) { Join-Path $jhome 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
$psi = New-Object Diagnostics.ProcessStartInfo
$psi.FileName=$java; $psi.Arguments='-version'; $psi.UseShellExecute=$false; $psi.RedirectStandardError=$true; $psi.CreateNoWindow=$true
$probe=[Diagnostics.Process]::Start($psi); $version=$probe.StandardError.ReadToEnd(); $probe.WaitForExit()
if ($probe.ExitCode -ne 0 -or $version -notmatch 'version "21[.\"]') { throw "Java 21 is required. Configure JAVA_HOME or config.json javaHome. $version" }
foreach ($folder in @('logs','run','data/management','data/verification')) { New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot $folder) | Out-Null }
$started=@()
try {
    foreach ($s in $services) {
        $token=[guid]::NewGuid().ToString(); $jar=Join-Path $packageRoot "modules/$($s.Jar)"
        $arguments=@("-Dkrtl.package.id=$token",'-Dfile.encoding=UTF-8','-jar',('"'+$jar+'"'),'--server.address=127.0.0.1',"--server.port=$($s.Port)")
        if ($s.Name -eq 'management') {
            $arguments+=@(('--krtl.admin.storage-root="'+(Join-Path $packageRoot 'data/management')+'"'),('--krtl.admin.signer-dir="'+(Join-Path $packageRoot 'samples/signer')+'"'))
        } else {
            $arguments+=@('--krtl.poc.enabled=true',('--krtl.poc.storage-root="'+(Join-Path $packageRoot 'data/verification')+'"'),('--krtl.poc.anchors="'+(Join-Path $packageRoot 'samples/tl-root.pem')+'"'))
        }
        $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
        $process=Start-Process -FilePath $java -ArgumentList ($arguments -join ' ') -WorkingDirectory $packageRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $packageRoot "logs/$($s.Name)-$stamp.log") -RedirectStandardError (Join-Path $packageRoot "logs/$($s.Name)-$stamp.err.log")
        @{pid=$process.Id;token=$token} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $packageRoot "run/$($s.Name).json") -Encoding UTF8
        $started+=,$s
        $ready=$false; $deadline=(Get-Date).AddSeconds(90)
        while ((Get-Date) -lt $deadline) {
            if ($process.HasExited) { throw "$($s.Name) exited. See logs/." }
            try {
                $result=Invoke-RestMethod "http://127.0.0.1:$($s.Port)$($s.Api)" -TimeoutSec 2
                if ($s.Name -eq 'management') { if ($result.health.available -ne $true) { throw 'Storage unavailable' }; $ready=$true }
                else { $ready=($null -ne $result.PSObject.Properties['document']) }
                if ($ready) { break }
            } catch { }
            Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw "$($s.Name) API did not become ready. See logs/." }
        Write-Host "$($s.Name): http://localhost:$($s.Port)$($s.Page)"
    }
} catch {
    foreach ($s in $started) { $p=Get-ServiceProcess $s; if ($p) { Stop-Process -Id $p.ProcessId } }
    throw
}
