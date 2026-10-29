. (Join-Path $PSScriptRoot 'Common.ps1')
foreach ($s in $services) {
    $p=Get-ServiceProcess $s
    if ($p) { Stop-Process -Id $p.ProcessId -ErrorAction Stop; Wait-Process -Id $p.ProcessId -Timeout 15 -ErrorAction SilentlyContinue; Write-Host "$($s.Name): stopped" }
    else { Write-Host "$($s.Name): not running" }
}
