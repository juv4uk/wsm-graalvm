param(
  [Parameter(Mandatory=$true)][string]$Version,
  [Parameter(Mandatory=$true)][string]$PayloadDir,
  [Parameter(Mandatory=$true)][string]$OutputDir
)

$ErrorActionPreference = "Stop"
$Version = $Version.TrimStart("v")

$heat = (Get-Command heat.exe -ErrorAction SilentlyContinue).Source
$candle = (Get-Command candle.exe -ErrorAction SilentlyContinue).Source
$light = (Get-Command light.exe -ErrorAction SilentlyContinue).Source
if (-not $heat -or -not $candle -or -not $light) {
  throw "WiX Toolset v3 (heat/candle/light) is required"
}
if (-not (Test-Path (Join-Path $PayloadDir "bin\wsm.cmd"))) {
  throw "payload does not contain bin\wsm.cmd"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$root = Join-Path $OutputDir "wix"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $root
New-Item -ItemType Directory -Force -Path $root | Out-Null

$harvest = Join-Path $root "payload.wxs"
& $heat dir $PayloadDir -cg PayloadComponents -dr INSTALLDIR -srd -gg -var var.PayloadDir -out $harvest
if ($LASTEXITCODE -ne 0) { throw "heat failed: $LASTEXITCODE" }

$product = Join-Path $root "product.wxs"
@"
<?xml version="1.0" encoding="UTF-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <Product Id="*" Name="WSM/GraalVM" Language="1033" Version="$Version" Manufacturer="WSM Project"
           UpgradeCode="{F5F2218A-3E74-4B0A-9A0A-4A8D4C8D8CF1}">
    <Package InstallerVersion="500" Compressed="yes" InstallScope="perMachine" Platform="x64" />
    <MajorUpgrade DowngradeErrorMessage="A newer version of WSM/GraalVM is already installed." />
    <MediaTemplate EmbedCab="yes" />

    <Directory Id="TARGETDIR" Name="SourceDir">
      <Directory Id="ProgramFiles64Folder">
        <Directory Id="INSTALLDIR" Name="WSM-GraalVM">
          <Component Id="PathEnvironment" Guid="{C5A2DD39-F1D7-45CE-A42D-A5E7BC7D8B21}">
            <CreateFolder />
            <Environment Id="WsmPath" Name="PATH" Value="[INSTALLDIR]bin" Action="set" Part="last" System="yes" />
          </Component>
        </Directory>
      </Directory>
    </Directory>

    <Feature Id="ProductFeature" Title="WSM/GraalVM" Level="1">
      <ComponentRef Id="PathEnvironment" />
      <ComponentGroupRef Id="PayloadComponents" />
    </Feature>
  </Product>
</Wix>
"@ | Set-Content -Encoding UTF8 $product

& $candle -ext WixUtilExtension -arch x64 "-dVersion=$Version" "-dPayloadDir=$PayloadDir" -out (Join-Path $root "product.wixobj") $product
if ($LASTEXITCODE -ne 0) { throw "candle product failed: $LASTEXITCODE" }
& $candle -ext WixUtilExtension -arch x64 "-dVersion=$Version" "-dPayloadDir=$PayloadDir" -out (Join-Path $root "payload.wixobj") $harvest
if ($LASTEXITCODE -ne 0) { throw "candle payload failed: $LASTEXITCODE" }

$msi = Join-Path $OutputDir "wsm-graalvm-$Version-windows-x86_64.msi"
& $light -ext WixUtilExtension -out $msi (Join-Path $root "product.wixobj") (Join-Path $root "payload.wixobj")
if ($LASTEXITCODE -ne 0) { throw "light failed: $LASTEXITCODE" }

Write-Host "MSI-OK $msi"
