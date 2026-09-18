; WSM/GraalVM Windows x86_64 installer
; SourceDir, OutputDir and Version are supplied by the release workflow.
#ifndef SourceDir
#error SourceDir is required
#endif
#ifndef OutputDir
#error OutputDir is required
#endif
#ifndef Version
#error Version is required
#endif

[Setup]
AppId={{B7B1B2E5-2F13-4F95-9A37-9A37B7B1B2E5}
AppName=WSM/GraalVM
AppVersion={#Version}
AppPublisher=WSM Project
DefaultDirName={autopf}\WSM\{#Version}
DefaultGroupName=WSM/GraalVM
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputDir}
OutputBaseFilename=wsm-graalvm-{#Version}-windows-x86_64-installer
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName=WSM/GraalVM {#Version}
PrivilegesRequired=admin
ChangesEnvironment=yes

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\WSM/GraalVM release metadata"; Filename: "{app}\RELEASE.txt"

[Registry]
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}"; Check: NeedsAddPath; Flags: preservestringtype

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Code]
function NeedsAddPath(Param: String): Boolean;
var
  ExistingPath: String;
begin
  if not RegQueryStringValue(HKEY_LOCAL_MACHINE,
    'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', ExistingPath) then
    ExistingPath := '';
  Result := Pos(Uppercase(ExpandConstant('{app}')), Uppercase(ExistingPath)) = 0;
end;
