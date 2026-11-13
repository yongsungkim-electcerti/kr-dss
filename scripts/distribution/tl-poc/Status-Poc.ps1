. (Join-Path $PSScriptRoot 'Common.ps1')
foreach ($s in $services) {
    $p=Get-ServiceProcess $s; $state='stopped'
    if ($p) { try { $r=Invoke-RestMethod "http://127.0.0.1:$($s.Port)$($s.Api)" -TimeoutSec 3; $state=if ($s.Name -eq 'management' -and $r.health.available -ne $true) {'storage unavailable'} else {'API responding'} } catch { $state='process running; API unavailable' } }
    [pscustomobject]@{Service=$s.Name;State=$state;PID=if($p){$p.ProcessId}else{$null};URL="http://localhost:$($s.Port)$($s.Page)"}
}
