# Run from the working Chatwoot Compose project. Only the Sidekiq container is recreated.
[CmdletBinding()]
param([switch]$DisableAutoAssignment)
$ErrorActionPreference = 'Stop'
$encoding = New-Object System.Text.UTF8Encoding($false, $true)
function DockerText([string[]]$Arguments) {
    $result = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed ($LASTEXITCODE)." }
    return ($result -join "`n")
}
function HostPath([string]$path) {
    if ($env:OS -eq 'Windows_NT' -and $path -match '^/(?:run/desktop/mnt/host|host_mnt|mnt)/([A-Za-z])/(.*)$') {
        $path = $Matches[1] + ':/' + $Matches[2]
    }
    return (Resolve-Path -LiteralPath $path).Path
}
function ComposeArgs([string[]]$paths) {
    $arguments = @('compose', '-p', $project)
    foreach ($path in $paths) { $arguments += @('-f', $path) }
    return $arguments
}
# Compose environment and label names are case-sensitive. The framework JSON
# reader preserves them even in Windows PowerShell 5.1 (no -AsHashtable).
function ReadDockerJson([string]$json) {
    if ($PSVersionTable.PSVersion.Major -le 5) { Add-Type -AssemblyName System.Runtime.Serialization }
    $reader = [System.Runtime.Serialization.Json.JsonReaderWriterFactory]::CreateJsonReader(
        [Text.Encoding]::UTF8.GetBytes($json), [System.Xml.XmlDictionaryReaderQuotas]::Max)
    try {
        $document = New-Object System.Xml.XmlDocument
        $document.PreserveWhitespace = $true
        $document.Load($reader)
        return ,$document
    } finally { $reader.Dispose() }
}
function Config([string[]]$paths) {
    $argsList = @(ComposeArgs $paths) + @('config', '--format', 'json')
    return ,(ReadDockerJson (DockerText $argsList))
}
function RubyRunner([string[]]$paths, [string]$program) {
    $argsList = @(ComposeArgs $paths) + @('exec','-T','sidekiq','bundle','exec','rails','runner',$program)
    return (DockerText $argsList)
}

$id = (DockerText @('compose','ps','-q','sidekiq')).Trim()
if (!$id -or $id -match '\s') { throw 'Run in the project directory with one running sidekiq service.' }
$meta = ReadDockerJson (DockerText @('inspect',$id))
$labels = $meta.SelectSingleNode('/root/item/Config/Labels')
$project = $labels.SelectSingleNode('com.docker.compose.project').InnerText
$paths = @($labels.SelectSingleNode('com.docker.compose.project.config_files').InnerText -split ',' | ForEach-Object { HostPath $_ })
$directory = HostPath ($labels.SelectSingleNode('com.docker.compose.project.working_dir').InnerText)
if ((Get-Location).Path -ne $directory) { throw "Run this script from $directory." }
$versionCheck = "abort unless Chatwoot.config[:version].to_s == [4,19,1].join(46.chr); AccountUser.find_by!(account_id: 2, user_id: 2); puts :preflight_ok"
if ((RubyRunner $paths $versionCheck) -notmatch 'preflight_ok') { throw 'Chatwoot 4.19.1 and operator 2 in account 2 are required.' }
$before = Config $paths
$target = '/app/config/initializers/amazy_incoming_push.rb'
$modulePath = Join-Path $directory 'amazy_incoming_push.rb'
$volumesPath = '/root/services/sidekiq/volumes'
$mountPath = "$volumesPath/item[target='$target']"
$existing = @($before.SelectNodes($mountPath))
$moduleHash = '4bf563dc03e9c6bca8d87daf43553f4348a08a63a2255d6d7fa492a8d20db05f'
if (Test-Path -LiteralPath $modulePath) {
    if ((Get-FileHash -LiteralPath $modulePath).Hash -ne $moduleHash) { throw "A different file already exists at $modulePath." }
} else {
    $download = [IO.Path]::GetTempFileName()
    try {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/myximka-netizen/amazy-apart/334ca3a6ea3c0f8428485d300d944139f1862b83/chatwoot-official/server/incoming/amazy_incoming_push.rb' -OutFile $download
        if ((Get-FileHash -LiteralPath $download).Hash -ne $moduleHash) { throw 'Initializer checksum mismatch.' }
        [IO.File]::Copy($download, $modulePath, $false)
    } finally { Remove-Item -LiteralPath $download -Force }
}

# Insert one bind into the last file already used by this project. Validate the
# complete merged Compose model before touching the file or recreating a service.
$composePath = $paths[-1]
$original = [IO.File]::ReadAllText($composePath, $encoding)
$originalBytes = [IO.File]::ReadAllBytes($composePath)
$changed = $false
$recreated = $false
$published = $false
$up = @(ComposeArgs $paths) + @('up','-d','--no-deps','--no-build','--pull','never','sidekiq')
if ($existing.Count -gt 0) {
    if ($existing.Count -ne 1 -or $existing[0].SelectSingleNode('type').InnerText -cne 'bind' -or
        $existing[0].SelectSingleNode('read_only').InnerText -cne 'true' -or (HostPath ($existing[0].SelectSingleNode('source').InnerText)) -ne $modulePath) {
        throw 'The initializer mount is already occupied by another file.'
    }
} else {
    $nl = if ($original.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in [regex]::Split($original,'\r?\n')) { $lines.Add($line) }
    $start = $lines.FindIndex([Predicate[string]]{ param($s) $s -match '^services:\s*(?:#.*)?$' })
    if ($start -lt 0) { throw 'The last Compose file needs a standard services block. See the manual bind-mount instructions.' }
    $end = $start + 1
    while ($end -lt $lines.Count -and $lines[$end] -notmatch '^\S[^#]*:') { $end++ }
    $block = $lines.GetRange($start+1,$end-$start-1)
    $row = $block.FindIndex([Predicate[string]]{ param($s) $s -match '^  sidekiq:\s*(?:#.*)?$' })
    $mount = '      - "./amazy_incoming_push.rb:/app/config/initializers/amazy_incoming_push.rb:ro"'
    if ($row -lt 0) {
        $lines.InsertRange($start+1,[string[]]@('  sidekiq:', '    volumes:', $mount))
    } else {
        $at = $start + 1 + $row
        $stop = $at + 1
        while ($stop -lt $end -and $lines[$stop] -notmatch '^  [^ #]') { $stop++ }
        $serviceBlock = $lines.GetRange($at+1,$stop-$at-1)
        $vol = $serviceBlock.FindIndex([Predicate[string]]{ param($s) $s -match '^    volumes:\s*(?:#.*)?$' })
        if ($vol -lt 0) { $lines.InsertRange($at+1,[string[]]@('    volumes:', $mount)) }
        else {
            $pos = $at+2+$vol
            $next = $pos
            while ($next -lt $stop -and $lines[$next] -match '^\s*(#.*)?$') { $next++ }
            if ($next -lt $stop -and $lines[$next] -match '^( +)-\s') { $mount = $Matches[1]+$mount.TrimStart() }
            $lines.Insert($pos,$mount)
        }
    }
    $candidate = $lines -join $nl
    $temp = Join-Path (Split-Path $composePath) ('.amazy-push-' + [Guid]::NewGuid().ToString('N') + '.yaml')
    try {
        [IO.File]::WriteAllText($temp,$candidate,$encoding)
        $trialPaths = @($paths.Clone())
        $trialPaths[-1] = $temp
        $after = Config $trialPaths
        $newMount = @($after.SelectNodes($mountPath))
        if ($newMount.Count -ne 1 -or $newMount[0].SelectSingleNode('type').InnerText -cne 'bind' -or
            $newMount[0].SelectSingleNode('read_only').InnerText -cne 'true' -or (HostPath ($newMount[0].SelectSingleNode('source').InnerText)) -ne $modulePath) { throw 'Could not verify the new mount.' }
        $volumes = $newMount[0].ParentNode
        [void]$volumes.RemoveChild($newMount[0])
        if (!$volumes.HasChildNodes) {
            $oldVolumes = $before.SelectSingleNode($volumesPath)
            if ($null -eq $oldVolumes) { [void]$volumes.ParentNode.RemoveChild($volumes) }
            else { $volumes.IsEmpty = $oldVolumes.IsEmpty }
        }
        if (![string]::Equals($before.OuterXml, $after.OuterXml, [StringComparison]::Ordinal)) { throw 'The proposed change affects other Compose settings. See the manual bind-mount instructions.' }
        if ([IO.File]::ReadAllText($composePath,$encoding) -cne $original) { throw 'Compose file changed concurrently.' }
        [IO.File]::Copy($temp,$composePath,$true)
        $changed = $true
    } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force } }
}
try {
    $runningMount = @($meta.SelectNodes("/root/item/Mounts/item[Destination='$target' and RW='false']"))
    if ($changed -or $runningMount.Count -eq 0) { $recreated=$true; DockerText $up | Out-Host }
    $setup = 'abort unless Messages::NewMessageNotificationService < AmazyIncomingPush; Account.transaction { s = NotificationSetting.find_by!(account_id: 2, user_id: 2); s.with_lock { s.push_assigned_conversation_new_message = true; s.push_participating_conversation_new_message = true; s.save! }; '
    if ($DisableAutoAssignment) { $setup += 'Account.find(2).inboxes.where(enable_auto_assignment: true).each { |i| i.update!(enable_auto_assignment: false) }; ' }
    $setup += '}; puts :incoming_push_ready'
    $result = RubyRunner $paths $setup
    if ($result -notmatch 'incoming_push_ready') { throw 'The worker did not load the notification rule.' }
    $published = $true
    Write-Output 'incoming_push_ready account=2 operator=2'
    if ($DisableAutoAssignment) { Write-Output 'auto_assignment_disabled account=2' }
} finally {
    if (!$published -and $changed) {
        [IO.File]::WriteAllBytes($composePath,$originalBytes)
        if ($recreated) { DockerText $up | Out-Host }
    }
}
