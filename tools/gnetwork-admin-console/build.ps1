param(
    [switch]$InstallDesktop
)

$ErrorActionPreference = 'Stop'
$toolDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildDirectory = Join-Path $toolDirectory 'build'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) {
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (-not (Test-Path -LiteralPath $compiler)) {
    throw 'The .NET Framework C# compiler is not installed.'
}

New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
$output = Join-Path $buildDirectory 'GNetwork Admin Console.exe'
$icon = Join-Path $buildDirectory 'GNetworkAdmin.ico'
$logo = Join-Path $toolDirectory 'G_transparent.png'
$sources = @(
    (Join-Path $toolDirectory 'GuiProgram.cs'),
    (Join-Path $toolDirectory 'GameServerConsole.cs')
)

& $compiler /nologo /target:winexe /platform:anycpu /optimize+ "/out:$output" "/win32icon:$icon" "/resource:$logo,GNetworkLogo.png" /reference:System.dll /reference:System.Core.dll /reference:System.Drawing.dll /reference:System.Security.dll /reference:System.Web.Extensions.dll /reference:System.Windows.Forms.dll $sources
if ($LASTEXITCODE -ne 0) { throw "Console compilation failed with exit code $LASTEXITCODE." }

$test = Start-Process -FilePath $output -ArgumentList @('--self-test') -WindowStyle Hidden -Wait -PassThru
if ($test.ExitCode -ne 0) { throw "Console self-test failed with exit code $($test.ExitCode)." }

if ($InstallDesktop) {
    $desktop = Join-Path ([Environment]::GetFolderPath('Desktop')) 'GNetwork Admin Console.exe'
    $running = Get-Process | Where-Object {
        try { $_.Path -eq $desktop } catch { $false }
    }
    if ($running) { throw 'Close GNetwork Admin Console.exe before installing the update.' }
    Copy-Item -LiteralPath $output -Destination $desktop -Force
}

Get-Item -LiteralPath $output
