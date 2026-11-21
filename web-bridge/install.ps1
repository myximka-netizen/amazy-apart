$ErrorActionPreference = "Stop"
$root = (Get-Location).Path
if (-not (Test-Path "docker-compose.yaml") -and -not (Test-Path "docker-compose.yml") -and -not (Test-Path "compose.yaml") -and -not (Test-Path "compose.yml")) { throw "Run from the Chatwoot Compose directory" }
$dir = Join-Path $root "web-bridge"
New-Item -ItemType Directory -Force $dir | Out-Null
$base = "https://raw.githubusercontent.com/myximka-netizen/amazy-apart/chatwoot-web-bridge/web-bridge"
foreach ($file in @("Dockerfile","entrypoint.sh","bridge.py","compose.web-bridge.yaml","config.example.json",".gitignore","README.md")) {
  Invoke-WebRequest -UseBasicParsing "$base/$file" -OutFile (Join-Path $dir $file)
}
$localConfig = Join-Path $dir "config.local.json"
if (-not (Test-Path $localConfig)) { Copy-Item (Join-Path $dir "config.example.json") $localConfig }
$localEnv = Join-Path $dir ".env.local"
if (-not (Test-Path $localEnv)) {
  $bytes = New-Object byte[] 32
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $secret = [BitConverter]::ToString($bytes).Replace("-","").ToLower()
  $rng.GetBytes($bytes)
  $vnc = [BitConverter]::ToString($bytes).Replace("-","").Substring(0,8).ToLower()
  $rng.Dispose()
  @("CHATWOOT_URL=http://rails:3000","CHATWOOT_ACCOUNT_ID=2","CHATWOOT_INBOX_ID=0","CHATWOOT_API_TOKEN=","WEB_BRIDGE_SECRET=$secret","WEB_BRIDGE_VNC_PASSWORD=$vnc") | Set-Content -Encoding ASCII $localEnv
}
Write-Host "Files installed. Existing config and credentials preserved."
Write-Host "VNC password (local only):"
(Get-Content $localEnv | Where-Object { $_ -like 'WEB_BRIDGE_VNC_PASSWORD=*' }) -replace '^WEB_BRIDGE_VNC_PASSWORD=',''
Write-Host "Start with your existing Compose files plus -f web-bridge/compose.web-bridge.yaml."
