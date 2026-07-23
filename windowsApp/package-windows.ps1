$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $ProjectRoot

if (Test-Path ".\gradlew.bat") {
    & ".\gradlew.bat" ":windowsApp:installDist"
} else {
    gradle ":windowsApp:installDist"
}

$InputDir = Resolve-Path ".\windowsApp\build\install\windowsApp\lib"
$MainJar = Get-ChildItem $InputDir -Filter "windowsApp*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Select-Object -First 1

if ($null -eq $MainJar) {
    throw "Unable to locate the Windows application JAR in $InputDir"
}

$Destination = Join-Path $ProjectRoot "dist\windows"
New-Item -ItemType Directory -Force -Path $Destination | Out-Null

$CommonArguments = @(
    "--name", "P59Tuner",
    "--input", $InputDir,
    "--main-jar", $MainJar.Name,
    "--main-class", "com.p59.windows.MainKt",
    "--dest", $Destination,
    "--app-version", "1.0.0",
    "--vendor", "Dynapetey",
    "--description", "OBDX Pro GM P59 tuning and diagnostics runtime",
    "--win-dir-chooser",
    "--win-menu",
    "--win-shortcut"
)

& jpackage "--type" "exe" @CommonArguments
& jpackage "--type" "msi" @CommonArguments

Write-Host "Windows installers created in $Destination"
