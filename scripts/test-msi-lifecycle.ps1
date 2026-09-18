param(
  [Parameter(Mandatory=$true)][string]$NewMsi,
  [Parameter(Mandatory=$true)][string]$PayloadDir
)

$ErrorActionPreference = "Stop"
$root = Join-Path $env:ProgramFiles "WSM-GraalVM"
$tmp = Join-Path $env:TEMP ("wsm-msi-smoke-" + [Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

try {
  & "scripts\package-msi.ps1" "v0.0.9" $PayloadDir $tmp
  if ($LASTEXITCODE -ne 0) { throw "failed to build synthetic old MSI" }
  $oldMsi = Join-Path $tmp "wsm-graalvm-0.0.9-windows-x86_64.msi"
  if (-not (Test-Path $oldMsi)) { throw "old MSI missing: $oldMsi" }

  Start-Process msiexec.exe -ArgumentList @("/i", $oldMsi, "/qn", "/norestart") -Wait -NoNewWindow
  if ($LASTEXITCODE -ne 0) { throw "old MSI install failed: $LASTEXITCODE" }

  Start-Process msiexec.exe -ArgumentList @("/i", $NewMsi, "/qn", "/norestart") -Wait -NoNewWindow
  if ($LASTEXITCODE -ne 0) { throw "new MSI upgrade failed: $LASTEXITCODE" }

  $release = Get-ChildItem -Path $root -Filter RELEASE.txt -Recurse -ErrorAction Stop | Select-Object -First 1
  $text = Get-Content $release.FullName -Raw
  if ($text -notmatch "version: v0.1.0") { throw "upgraded release metadata is not v0.1.0" }

  $smoke = Join-Path $tmp "smoke.lisp"
  Set-Content -Path $smoke -Value "(quote 42)" -Encoding UTF8
  & (Join-Path $root "bin\wsm.cmd") $smoke
  if ($LASTEXITCODE -ne 0) { throw "installed WSM launcher failed: $LASTEXITCODE" }

  $product = Get-CimInstance Win32_Product -Filter "Name='WSM/GraalVM'"
  if ($product) {
    & msiexec.exe /x $product.IdentifyingNumber /qn /norestart
    if ($LASTEXITCODE -ne 0) { throw "MSI uninstall failed: $LASTEXITCODE" }
  } else {
    throw "installed WSM/GraalVM product not found for uninstall"
  }

  if (Test-Path $root) { throw "install root remains after uninstall: $root" }
  Write-Host "WINDOWS-MSI-LIFECYCLE-OK"
}
finally {
  Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $tmp
}
