<#
.SYNOPSIS
  PoC TL·사업자 인증체계(설계 27)를 생성하고 main 체크아웃에 자동 복사한다.

.DESCRIPTION
  1. 현재 체크아웃의 runtime/tl-management/pki 가 없으면 krdss-cli cert tree 로 생성한다.
     이미 있으면 재생성하지 않는다(키 세트는 하나만 사용).
  2. git worktree 목록의 main 체크아웃(기본 F:/kr-dss-works/kr-dss)으로 폴더 전체를 복사한다.
     - 대상이 없으면 복사 후 전체 파일 SHA-256 을 대조한다.
     - 대상이 있고 내용이 같으면 그대로 둔다.
     - 대상이 있고 내용이 다르면 덮어쓰지 않고 실패한다.
  키 파일은 Git 비관리(runtime/)이며 다른 체크아웃에서 재생성하지 않는다.

.PARAMETER Target
  복사 대상 저장소 루트. 생략하면 git worktree 목록의 첫 항목(main 체크아웃).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/gen-tl-pki.ps1
#>
param(
    [string]$Target
)
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$relative = 'runtime\tl-management\pki'
$source = Join-Path $root $relative

if (-not $Target) {
    $line = git -C $root worktree list --porcelain | Select-String '^worktree ' | Select-Object -First 1
    if (-not $line) { throw 'git worktree 목록에서 main 체크아웃을 찾지 못했습니다.' }
    $Target = $line.ToString().Substring(9).Trim()
}
$Target = [System.IO.Path]::GetFullPath($Target)
$destination = Join-Path $Target $relative

function Get-FileHashes([string]$dir) {
    $base = [System.IO.Path]::GetFullPath($dir).TrimEnd('\') + '\'
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        Get-ChildItem -LiteralPath $dir -Recurse -File | Sort-Object FullName | ForEach-Object {
            $hash = [System.BitConverter]::ToString($sha.ComputeHash([System.IO.File]::ReadAllBytes($_.FullName)))
            '{0} {1}' -f $hash.Replace('-', ''), $_.FullName.Substring($base.Length)
        }
    } finally {
        $sha.Dispose()
    }
}

# 1) 생성 (없을 때만)
if (Test-Path -LiteralPath $source) {
    Write-Host "• 기존 키 세트 사용: $source"
} else {
    Write-Host '• 키 세트 생성'
    & (Join-Path $root 'gradlew.bat') --project-dir $root ':tools:krdss-cli:run' '--console=plain' -q `
        "--args=cert tree -f scripts/poc/tl-management/pki-profile.json -o $relative"
    if ($LASTEXITCODE -ne 0) { throw "키 세트 생성 실패 (exit $LASTEXITCODE)" }
}

# 2) main 체크아웃으로 복사
if ([System.IO.Path]::GetFullPath($root).TrimEnd('\') -ieq $Target.TrimEnd('\')) {
    Write-Host '• 현재 체크아웃이 복사 대상과 같아 복사를 생략합니다.'
    exit 0
}
$sourceHashes = Get-FileHashes $source
if (Test-Path -LiteralPath $destination) {
    $destinationHashes = Get-FileHashes $destination
    if (-not (Compare-Object $sourceHashes $destinationHashes)) {
        Write-Host "• 대상에 같은 키 세트가 이미 있습니다 ($($sourceHashes.Count)개 파일): $destination"
        exit 0
    }
    throw "대상에 다른 키 세트가 있어 덮어쓰지 않습니다: $destination (manifest.json 의 setId·generatedAt 확인)"
}
New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
Copy-Item -LiteralPath $source -Destination $destination -Recurse
$destinationHashes = Get-FileHashes $destination
if (Compare-Object $sourceHashes $destinationHashes) {
    throw "복사 후 해시가 일치하지 않습니다: $destination"
}
Write-Host "• main 체크아웃에 복사 완료 ($($sourceHashes.Count)개 파일 해시 일치): $destination"
