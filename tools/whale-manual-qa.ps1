param([ValidateSet('Start','Render','Stop')][string]$Stage='Start')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$qa='C:/Users/Public/Documents/ESTsoft/CreatorTemp/whale-manual-20261028'
New-Item -ItemType Directory -Force -Path $qa | Out-Null
if($Stage -eq 'Start') {
  $pkg=Join-Path $qa 'package'
  if(-not(Test-Path $pkg)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $repo 'outputs/TrustList-PoC-Windows-20261028.zip'),$pkg)
  }
  $start=Get-ChildItem $pkg -Filter Start-Poc.ps1 -Recurse | Select-Object -First 1
  $root=$start.DirectoryName
  @{managementPort=19081;verificationPort=19082;javaHome='C:/Users/sol74/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2'} | ConvertTo-Json | Set-Content (Join-Path $root 'config.json') -Encoding UTF8
  Set-Content (Join-Path $qa 'package-root.txt') $root
  & $start.FullName
} elseif($Stage -eq 'Stop') {
  $root=(Get-Content (Join-Path $qa 'package-root.txt') -Raw).Trim()
  & (Join-Path $root 'Stop-Poc.ps1')
} else {
  $env:PYTHONPATH=Join-Path $qa 'pydeps'
  $env:PYTHONUTF8='1'
  $ppt=New-Object -ComObject PowerPoint.Application
  try {
    $layoutIssues=@()
    Get-ChildItem (Join-Path $repo 'docs/guides/poc/tl-management/whale') -Filter Whale_*.pptx | ForEach-Object {
      & 'C:/Users/sol74/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' 'C:/Users/sol74/.agents/skills/pptx/scripts/office/validate.py' $_.FullName
      $dest=Join-Path $qa ($_.BaseName.Replace('.','-')+'-revised')
      New-Item -ItemType Directory -Force -Path $dest | Out-Null
      $deck=$ppt.Presentations.Open($_.FullName, -1, 0, 0)
      try {
        foreach($slide in $deck.Slides) {foreach($shape in $slide.Shapes) {
          if($shape.HasTextFrame -eq -1 -and $shape.TextFrame.HasText -eq -1) {
            if($shape.TextFrame.TextRange.BoundHeight -gt $shape.Height+2) {
              $layoutIssues += [pscustomobject]@{file=$_.Name;slide=$slide.SlideIndex;shape=$shape.Name;bound=$shape.TextFrame.TextRange.BoundHeight;height=$shape.Height;text=$shape.TextFrame.TextRange.Text}
            }
          }
        }}
        $deck.Export($dest,'PNG',1600,900)
        Write-Output ($_.Name+': '+$deck.Slides.Count+' slides rendered')
      } finally { $deck.Close() }
    }
    ConvertTo-Json -InputObject @($layoutIssues) -Depth 4 | Set-Content (Join-Path $repo 'docs/guides/poc/tl-management/whale/evidence/layout-issues.json') -Encoding UTF8
    Write-Output ('Layout overflow candidates: '+$layoutIssues.Count)
  } finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($ppt) }
}
