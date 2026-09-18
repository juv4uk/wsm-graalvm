$ErrorActionPreference = "Stop"

$Repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Graal = if ($env:G) { $env:G } elseif ($env:JAVA_HOME) { $env:JAVA_HOME } else { throw "Set G or JAVA_HOME to GraalVM 25" }
$JavaBin = Join-Path $Graal "bin"
$ThirdParty = Join-Path $Repo "third_party"
$Classes = Join-Path $Repo "classes"

$Version = if ($env:GRAAL_ARTIFACT_VERSION) { $env:GRAAL_ARTIFACT_VERSION } else { "25.3.4.1" }
$Maven = "https://repo.maven.apache.org/maven2"

New-Item -ItemType Directory -Force -Path $ThirdParty | Out-Null

function Fetch-Jar([string]$Group, [string]$Artifact, [string]$Target) {
    $out = Join-Path $ThirdParty $Target
    if (Test-Path $out) {
        & (Join-Path $JavaBin "jar.exe") tf $out *> $null
        if ($LASTEXITCODE -eq 0) { return }
    }

    $url = "$Maven/$($Group.Replace('.','/'))/$Artifact/$Version/$Artifact-$Version.jar"
    $tmp = "$out.tmp"
    Remove-Item -Force -ErrorAction SilentlyContinue $tmp, $out
    Write-Host "fetch: $Artifact@$Version"
    Invoke-WebRequest -Uri $url -OutFile $tmp
    & (Join-Path $JavaBin "jar.exe") tf $tmp *> $null
    if ($LASTEXITCODE -ne 0) { throw "invalid jar: $tmp" }
    Move-Item -Force $tmp $out
}

Fetch-Jar "org.graalvm.truffle" "truffle-api" "truffle-api.jar"
Fetch-Jar "org.graalvm.polyglot" "polyglot" "polyglot.jar"
Fetch-Jar "org.graalvm.truffle" "truffle-runtime" "truffle-runtime.jar"
Fetch-Jar "org.graalvm.truffle" "truffle-dsl-processor" "truffle-dsl-processor.jar"
Fetch-Jar "org.graalvm.truffle" "truffle-compiler" "truffle-compiler.jar"
Fetch-Jar "org.graalvm.sdk" "collections" "collections.jar"
Fetch-Jar "org.graalvm.sdk" "jniutils" "jniutils.jar"
Fetch-Jar "org.graalvm.sdk" "nativeimage" "nativeimage.jar"
Fetch-Jar "org.graalvm.sdk" "word" "word.jar"

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $Classes
New-Item -ItemType Directory -Force -Path $Classes | Out-Null

$CompileJars = @(
    (Join-Path $ThirdParty "truffle-api.jar"),
    (Join-Path $ThirdParty "polyglot.jar"),
    (Join-Path $ThirdParty "truffle-runtime.jar"),
    (Join-Path $ThirdParty "collections.jar")
) -join ";"

$Sources = Get-ChildItem (Join-Path $Repo "src/main/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& (Join-Path $JavaBin "javac.exe") --release 25 -cp $CompileJars -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$Resources = Join-Path $Repo "src/main/resources"
if (Test-Path $Resources) {
    Copy-Item (Join-Path $Resources "*") $Classes -Recurse -Force
}

$ModulePath = @(
    "truffle-api.jar", "truffle-runtime.jar", "truffle-compiler.jar",
    "polyglot.jar", "collections.jar", "jniutils.jar", "nativeimage.jar", "word.jar"
) | ForEach-Object { Join-Path $ThirdParty $_ } | Join-String -Separator ";"

$Native = Join-Path $Repo "native-wsm.exe"
Remove-Item -Force -ErrorAction SilentlyContinue $Native

$NativeArgs = @(
    "--module-path", $ModulePath,
    "--no-fallback",
    "--initialize-at-build-time=wsm.graalvm.providers.WsmLanguageProvider",
    "-H:IncludeResources=META-INF/services/com[.]oracle[.]truffle[.]api[.]provider[.]TruffleLanguageProvider",
    "-cp", $Classes,
    "wsm.graalvm.Main",
    $Native
)
& (Join-Path $JavaBin "native-image.cmd") @NativeArgs
if ($LASTEXITCODE -ne 0) { throw "native-image failed" }

$MyLisp = if ($env:MYLISP) { $env:MYLISP } else { Join-Path $Repo "external/my-lisp" }
$Canon = Join-Path $MyLisp "lib/canon.lisp"
$Registry = Join-Path $MyLisp "lib/surface/semantic-registry.lisp"
if (!(Test-Path $Canon)) { throw "missing pinned canon: $Canon" }
if (!(Test-Path $Registry)) { throw "missing pinned registry: $Registry" }

$Tmp = Join-Path $env:TEMP ("wsm-canon-" + [guid]::NewGuid().ToString() + ".lisp")
try {
    Get-Content $Canon -Raw | Set-Content $Tmp -NoNewline
    Add-Content $Tmp "`r`n(canon-conforms?)"
    $Log = Join-Path $Repo "native-canon-windows.log"
    & $Native $Tmp $Registry $MyLisp 2>&1 | Tee-Object -FilePath $Log
    if ($LASTEXITCODE -ne 0) { throw "native canon witness failed" }
    if (-not (Select-String -Path $Log -Pattern "\(canon-conformance satisfied\)" -Quiet)) {
        throw "NATIVE-IMAGE-CANON witness missing"
    }
    Write-Host "NATIVE-IMAGE-CANON-OK"
} finally {
    Remove-Item -Force -ErrorAction SilentlyContinue $Tmp
}
