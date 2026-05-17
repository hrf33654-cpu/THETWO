param(
    [string]$ArchivePath = "app/libs/EasyARSense_4.8.0.11837.7z",
    [string]$Destination = "app/libs/easyar-sdk"
)

if (-not (Test-Path $ArchivePath)) {
    Write-Error "Archive not found: $ArchivePath"
    exit 1
}

$size = (Get-Item $ArchivePath).Length
if ($size -lt 1024) {
    Write-Error "Archive looks invalid or incomplete ($size bytes): $ArchivePath"
    exit 2
}

$sevenZip = Get-Command 7z -ErrorAction SilentlyContinue
if (-not $sevenZip) {
    Write-Error "7z is not installed or not on PATH. Please extract the archive manually and copy EasyAR.aar into app/libs/."
    exit 4
}

if (Test-Path $Destination) {
    Remove-Item $Destination -Recurse -Force
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

& $sevenZip.Source x $ArchivePath "-o$Destination" -y | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "7z extraction failed"
    exit 5
}

$aar = Get-ChildItem -Path $Destination -Recurse -Filter "EasyAR.aar" | Select-Object -First 1
if (-not $aar) {
    Write-Error "EasyAR.aar not found after extraction"
    exit 3
}

Copy-Item $aar.FullName "app/libs/EasyAR.aar" -Force
Write-Output "Extracted and copied: $($aar.FullName) -> app/libs/EasyAR.aar"

