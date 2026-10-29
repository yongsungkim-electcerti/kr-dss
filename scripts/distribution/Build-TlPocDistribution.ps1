param([string]$Name = ('TrustList-PoC-Windows-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if ($Name -notmatch '^[A-Za-z0-9._-]+$') { throw 'Invalid package name' }
$output=Join-Path $repo 'outputs'
$package=Join-Path $output $Name
if (Test-Path -LiteralPath $package) { throw 'Package already exists; use a new name.' }
New-Item -ItemType Directory -Force -Path (Join-Path $package 'modules') | Out-Null
$previous=$env:KRTL_DISTRIBUTION_FIXTURES
Push-Location $repo
try {
    $env:KRTL_DISTRIBUTION_FIXTURES=Join-Path $package 'samples'
    & ./gradlew.bat :poc:poc-kisa-tl:bootJar :poc:poc-relying-party:bootJar :poc:poc-relying-party:test --tests '*DistributionFixturesTest' --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Build/fixture generation failed. Partial package retained for inspection.' }
} finally { $env:KRTL_DISTRIBUTION_FIXTURES=$previous; Pop-Location }
foreach ($item in @(@('poc-kisa-tl','tl-management.jar'),@('poc-relying-party','tl-verification.jar'))) {
    $jars=@(Get-ChildItem (Join-Path $repo "src/poc/$($item[0])/build/libs") -Filter '*.jar' | Where-Object { $_.Name -notmatch '-(plain|sources|javadoc)\.jar$' })
    if ($jars.Count -ne 1) { throw 'Expected exactly one executable JAR per module.' }
    Copy-Item -LiteralPath $jars[0].FullName -Destination (Join-Path $package "modules/$($item[1])")
}
Get-ChildItem (Join-Path $PSScriptRoot 'tl-poc') -File | Copy-Item -Destination $package
foreach ($action in @('Start','Stop','Status')) {
    $cmd="@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0$action-Poc.ps1`"`r`nset result=%errorlevel%`r`npause`r`nexit /b %result%`r`n"
    [IO.File]::WriteAllText((Join-Path $package "$action-Poc.cmd"),$cmd,[Text.Encoding]::ASCII)
}
foreach ($folder in @('data','logs','run')) { New-Item -ItemType Directory -Path (Join-Path $package $folder) | Out-Null }
$seed = Join-Path $PSScriptRoot 'tl-poc/seed/management'
if (Test-Path -LiteralPath $seed) {
    Copy-Item -LiteralPath $seed -Destination (Join-Path $package 'data/management') -Recurse
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$manualSource=Join-Path $repo 'docs/guides/poc/tl-management/whale'
if(Test-Path -LiteralPath $manualSource) {
    $manualDestination=Join-Path $package 'manuals'
    New-Item -ItemType Directory -Force -Path $manualDestination | Out-Null
    Get-ChildItem -LiteralPath $manualSource -File | Where-Object Extension -in @('.pptx','.pdf') | Copy-Item -Destination $manualDestination
    if(Test-Path (Join-Path $manualSource 'examples')) {Copy-Item -LiteralPath (Join-Path $manualSource 'examples') -Destination $manualDestination -Recurse}
}
$dependencies=@{}
foreach ($jar in Get-ChildItem (Join-Path $package 'modules') -Filter '*.jar') {
    $z=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        if (-not $z.GetEntry('META-INF/MANIFEST.MF') -or -not $z.GetEntry('org/springframework/boot/loader/launch/JarLauncher.class')) { throw 'Not an executable Spring Boot JAR' }
        $dependencies[$jar.Name]=@($z.Entries | Where-Object FullName -like 'BOOT-INF/lib/*.jar' | ForEach-Object FullName)
    } finally { $z.Dispose() }
}
$records=@(Get-ChildItem -LiteralPath $package -Recurse -File | ForEach-Object { @{path=$_.FullName.Substring($package.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()} })
$commit=& git -C $repo rev-parse HEAD
$dirty=[bool](& git -C $repo status --porcelain)
@{name=$Name;createdAt=(Get-Date).ToUniversalTime().ToString('o');sourceCommit=$commit;sourceWorktreeModified=$dirty;java='21';executionValidation='USER_PENDING';demoPrivateKey='samples/signer/key.pem';dependencies=$dependencies;files=$records} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $package 'package-manifest.json') -Encoding UTF8
@($records | ForEach-Object { "$($_.sha256)  $($_.path)" }) | Set-Content -LiteralPath (Join-Path $package 'SHA256SUMS.txt') -Encoding ASCII
$zip=Join-Path $output "$Name.zip"
Compress-Archive -LiteralPath $package -DestinationPath $zip -CompressionLevel Optimal
"$((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant())  $Name.zip" | Set-Content -LiteralPath "$zip.sha256" -Encoding ASCII
Write-Host "Package: $package"
Write-Host "ZIP: $zip"
Write-Host 'Package applications were NOT started. User execution pending.'
