# Run with Windows PowerShell 5.1. The Docker double supplies the same case
# collisions as the Windows host. All file operations use a disposable project.
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) { throw 'Windows PowerShell 5.1 required.' }
$installer = Join-Path $PSScriptRoot '../incoming/install.ps1'
$source = Join-Path $PSScriptRoot '../incoming/amazy_incoming_push.rb'
$work = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($work)
$compose = Join-Path $work 'compose.yaml'
$moduleFile = Join-Path $work 'amazy_incoming_push.rb'
$targetPath = '/app/config/initializers/amazy_incoming_push.rb'
$originalYaml = "services:`r`n  sidekiq:`r`n    image: example`r`n"
$probeState = @{ starts = 0; changeProxy = $false }
function docker {
    $global:LASTEXITCODE = 0
    if ($args -contains 'ps') { return 'test-worker' }
    if ($args -contains 'inspect') {
        $mounts = @()
        if ($probeState.starts) { $mounts += @{Destination=$targetPath; RW=$false} }
        $labels = @{'com.docker.compose.project'='test'; 'com.docker.compose.project.working_dir'=$work; 'com.docker.compose.project.config_files'=$compose}
        return '[' + (@{Config=@{Labels=$labels}; Mounts=$mounts} | ConvertTo-Json -Depth 8 -Compress) + ']'
    }
    if ($args -contains 'config') {
        $file = $args[[array]::IndexOf($args, '-f')+1]
        $volumeJson = ''
        $lower = 'lower'
        if ([IO.File]::ReadAllText($file).Contains($targetPath)) {
            $bind = @{type='bind'; source=$moduleFile; target=$targetPath; read_only=$true} | ConvertTo-Json -Compress
            $volumeJson = ',"volumes":[' + $bind + ']'
            if ($probeState.changeProxy) { $lower = 'changed' }
        }
        return '{"services":{"sidekiq":{"environment":{"HTTP_PROXY":"UPPER","http_proxy":"' + $lower + '","NO_PROXY":"A","no_proxy":"b"}' + $volumeJson + '}}}'
    }
    if ($args -contains 'up') { $probeState.starts++; return 'worker started' }
    if ($args -contains 'runner' -and $args[-1] -like '*preflight_ok*') { return 'preflight_ok' }
    if ($args -contains 'runner' -and $args[-1] -like '*enable_auto_assignment: false*') { return 'incoming_push_ready' }
    throw 'Unexpected Docker operation in test.'
}
Push-Location $work
try {
    [IO.File]::WriteAllText($compose, $originalYaml)
    # The bind's pinned bytes use LF even on a Windows checkout with autocrlf.
    [IO.File]::WriteAllText($moduleFile, ([IO.File]::ReadAllText($source) -replace "`r`n","`n"))
    $probeState.changeProxy = $true
    try { & $installer -DisableAutoAssignment; throw 'Expected proxy change to be rejected.' }
    catch { if ($_.Exception.Message -notlike '*affects other Compose settings*') { throw } }
    if ($probeState.starts -ne 0 -or [IO.File]::ReadAllText($compose) -cne $originalYaml) { throw 'Compose changed before validation.' }
    $probeState.changeProxy = $false
    foreach ($attempt in 1..2) {
        $result = & $installer -DisableAutoAssignment
        if ($result -cnotcontains 'incoming_push_ready account=2 operator=2' -or $result -cnotcontains 'auto_assignment_disabled account=2') { throw 'Installer did not finish.' }
    }
    if ($probeState.starts -ne 1) { throw 'Expected exactly one Sidekiq recreation.' }
    Write-Output 'PASS Windows PowerShell 5.1: both proxy keys, isolated-change validation, installation and repeat run'
} finally {
    Pop-Location
    Remove-Item -LiteralPath $work -Recurse -Force
}
