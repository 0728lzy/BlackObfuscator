[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$PackageName,

    [string]$RulesFile,

    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,

    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,

    [Parameter(Mandatory = $true)]
    [string]$StorePassword,

    [string]$KeyPassword,

    [int]$Depth = 1,

    [string]$OutputApkPath,

    [string]$WorkDirectory,

    [string]$AndroidSdkRoot,

    [string]$BuildToolsVersion,

    [string]$GradleBat,

    [switch]$KeepWorkDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-FullPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathValue
    )

    return [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $PathValue).Path)
}

function Find-Executable {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        if (Test-Path -LiteralPath $candidate) {
            return [System.IO.Path]::GetFullPath($candidate)
        }

        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    return $null
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [switch]$CaptureOutput
    )

    if ($CaptureOutput) {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            $output = & $FilePath @Arguments 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        if ($exitCode -ne 0) {
            throw "Command failed: $FilePath $($Arguments -join ' ')"
        }

        return @($output | ForEach-Object { "$_" })
    }

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $FilePath $($Arguments -join ' ')"
    }
}

function Get-AndroidSdkRoot {
    param(
        [string]$RequestedSdkRoot
    )

    $candidates = @(
        $RequestedSdkRoot,
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        "C:\SDK",
        "D:\SDK",
        "C:\Android\Sdk",
        "D:\Android\Sdk"
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    throw "Android SDK not found. Pass -AndroidSdkRoot explicitly."
}

function Get-BuildToolsDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SdkRoot,

        [string]$RequestedVersion
    )

    $buildToolsRoot = Join-Path $SdkRoot "build-tools"
    if (-not (Test-Path -LiteralPath $buildToolsRoot)) {
        throw "build-tools directory not found under $SdkRoot"
    }

    if (-not [string]::IsNullOrWhiteSpace($RequestedVersion)) {
        $requestedPath = Join-Path $buildToolsRoot $RequestedVersion
        if (-not (Test-Path -LiteralPath $requestedPath)) {
            throw "Requested build-tools version not found: $RequestedVersion"
        }
        return [System.IO.Path]::GetFullPath($requestedPath)
    }

    $latest = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if (-not $latest) {
        throw "No build-tools versions found under $buildToolsRoot"
    }

    return $latest.FullName
}

function Ensure-DexToolLibs {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,

        [string]$GradleBatPath
    )

    $libDir = Join-Path $RepoRoot "dex-tools\build\install\dex-tools\lib"
    if (Test-Path -LiteralPath $libDir) {
        $jars = Get-ChildItem -LiteralPath $libDir -Filter "*.jar" -ErrorAction SilentlyContinue
        if ($jars) {
            return [System.IO.Path]::GetFullPath($libDir)
        }
    }

    $gradle = Find-Executable -Candidates @(
        $GradleBatPath,
        "gradle.bat",
        "gradle"
    )

    if (-not $gradle) {
        throw "Dex tool libs are missing. Build once with Gradle or pass -GradleBat."
    }

    Write-Host "Building dex-tools installDist..." -ForegroundColor Cyan
    Invoke-CheckedCommand -FilePath $gradle -Arguments @(":dex-tools:installDist")

    if (-not (Test-Path -LiteralPath $libDir)) {
        throw "dex-tools installDist did not produce lib directory."
    }

    return [System.IO.Path]::GetFullPath($libDir)
}

function Get-ApkPackageName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AaptPath,

        [Parameter(Mandatory = $true)]
        [string]$TargetApk
    )

    $output = Invoke-CheckedCommand -FilePath $AaptPath -Arguments @("dump", "badging", $TargetApk) -CaptureOutput
    foreach ($line in $output) {
        $text = "$line"
        if ($text -match "package:\s+name='([^']+)'") {
            return $Matches[1]
        }
    }

    throw "Failed to parse package name from APK."
}

function Get-ApkSignerSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApkSignerPath,

        [Parameter(Mandatory = $true)]
        [string]$TargetApk
    )

    try {
        $output = Invoke-CheckedCommand -FilePath $ApkSignerPath -Arguments @("verify", "--verbose", "--print-certs", $TargetApk) -CaptureOutput
        foreach ($line in $output) {
            $text = "$line"
            if ($text -match "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)") {
                return $Matches[1].ToLowerInvariant()
            }
        }
    } catch {
        return $null
    }

    return $null
}

function Get-KeystoreSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$KeytoolPath,

        [Parameter(Mandatory = $true)]
        [string]$KeystoreFile,

        [Parameter(Mandatory = $true)]
        [string]$Alias,

        [Parameter(Mandatory = $true)]
        [string]$StorePassValue,

        [Parameter(Mandatory = $true)]
        [string]$KeyPassValue
    )

    $output = Invoke-CheckedCommand -FilePath $KeytoolPath -Arguments @(
        "-list",
        "-v",
        "-keystore", $KeystoreFile,
        "-storepass", $StorePassValue,
        "-alias", $Alias,
        "-keypass", $KeyPassValue
    ) -CaptureOutput

    foreach ($line in $output) {
        $text = "$line"
        if ($text -match "SHA256:\s*([0-9A-Fa-f:]+)") {
            return ($Matches[1] -replace ":", "").ToLowerInvariant()
        }
    }

    throw "Failed to read SHA-256 digest from keystore."
}

function Copy-ZipWithDexReplacement {
    param(
        [Parameter(Mandatory = $true)]
        [string]$InputApk,

        [Parameter(Mandatory = $true)]
        [string]$OutputApk,

        [Parameter(Mandatory = $true)]
        [hashtable]$ReplacementDexMap
    )

    $pythonPath = Find-Executable -Candidates @("python", "python.exe", "py")
    if (-not $pythonPath) {
        throw "Python is required for repacking APKs while preserving ZIP methods."
    }

    $replacementJson = ($ReplacementDexMap | ConvertTo-Json -Compress)
    $pythonCode = @"
import json
import zipfile

input_apk = r'''$InputApk'''
output_apk = r'''$OutputApk'''
replacement_map = json.loads(r'''$replacementJson''')

with zipfile.ZipFile(input_apk, 'r') as zin, zipfile.ZipFile(output_apk, 'w') as zout:
    for info in zin.infolist():
        name = info.filename
        upper = name.upper()
        if name.startswith('META-INF/') and (upper.endswith('.RSA') or upper.endswith('.DSA') or upper.endswith('.EC') or upper.endswith('.SF') or upper.endswith('.MF')):
            continue

        if name in replacement_map:
            data = open(replacement_map[name], 'rb').read()
        else:
            data = zin.read(name)

        new_info = zipfile.ZipInfo(filename=info.filename, date_time=info.date_time)
        new_info.compress_type = info.compress_type
        new_info.comment = info.comment
        new_info.extra = info.extra
        new_info.create_system = info.create_system
        new_info.create_version = info.create_version
        new_info.extract_version = info.extract_version
        new_info.flag_bits = info.flag_bits
        new_info.volume = info.volume
        new_info.internal_attr = info.internal_attr
        new_info.external_attr = info.external_attr
        zout.writestr(new_info, data)
"@

    $tempPy = [System.IO.Path]::Combine([System.IO.Path]::GetDirectoryName($OutputApk), "repack_apk.py")
    try {
        [System.IO.File]::WriteAllText($tempPy, $pythonCode, [System.Text.Encoding]::UTF8)
        Invoke-CheckedCommand -FilePath $pythonPath -Arguments @($tempPy)
    } finally {
        if (Test-Path -LiteralPath $tempPy) {
            Remove-Item -Force -LiteralPath $tempPy
        }
    }
}

$repoRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$apkPathResolved = Resolve-FullPath -PathValue $ApkPath
$keystorePathResolved = Resolve-FullPath -PathValue $KeystorePath
$rulesFileResolved = $null

if (-not [string]::IsNullOrWhiteSpace($RulesFile)) {
    $rulesFileResolved = Resolve-FullPath -PathValue $RulesFile
}

if (-not $PSBoundParameters.ContainsKey("KeyPassword")) {
    $KeyPassword = $StorePassword
}

if (-not [string]::IsNullOrWhiteSpace($PackageName) -and -not [string]::IsNullOrWhiteSpace($rulesFileResolved)) {
    throw "Use either -PackageName or -RulesFile, not both."
}

$sdkRoot = Get-AndroidSdkRoot -RequestedSdkRoot $AndroidSdkRoot
$buildToolsDir = Get-BuildToolsDirectory -SdkRoot $sdkRoot -RequestedVersion $BuildToolsVersion
$aaptPath = Find-Executable -Candidates @((Join-Path $buildToolsDir "aapt.exe"))
$zipalignPath = Find-Executable -Candidates @((Join-Path $buildToolsDir "zipalign.exe"))
$apksignerPath = Find-Executable -Candidates @((Join-Path $buildToolsDir "apksigner.bat"), (Join-Path $buildToolsDir "apksigner"))
$javaHomeKeytool = $null
$commonJavaKeytool = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaHomeKeytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
}
if (Test-Path -LiteralPath "C:\Program Files\Java") {
    $commonJavaKeytool = Get-ChildItem -LiteralPath "C:\Program Files\Java" -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName "bin\keytool.exe" } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
}
$keytoolPath = Find-Executable -Candidates @(
    $javaHomeKeytool,
    $commonJavaKeytool,
    "keytool.exe",
    "keytool"
)

if (-not $aaptPath -or -not $zipalignPath -or -not $apksignerPath) {
    throw "Required Android build-tools executables were not found in $buildToolsDir"
}

if (-not $keytoolPath) {
    throw "keytool.exe not found. Ensure JAVA_HOME is set or keytool is on PATH."
}

if ([string]::IsNullOrWhiteSpace($PackageName) -and [string]::IsNullOrWhiteSpace($rulesFileResolved)) {
    $PackageName = Get-ApkPackageName -AaptPath $aaptPath -TargetApk $apkPathResolved
    Write-Host "Using package from manifest: $PackageName" -ForegroundColor Cyan
}

$toolLibDir = Ensure-DexToolLibs -RepoRoot $repoRoot -GradleBatPath $GradleBat
$classpath = ((Get-ChildItem -LiteralPath $toolLibDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";")

$apkBaseName = [System.IO.Path]::GetFileNameWithoutExtension($apkPathResolved)
if ([string]::IsNullOrWhiteSpace($WorkDirectory)) {
    $WorkDirectory = Join-Path $repoRoot (".blackobf-work\" + $apkBaseName + "-" + (Get-Date -Format "yyyyMMddHHmmss"))
}

$workDirResolved = [System.IO.Path]::GetFullPath($WorkDirectory)
$dexInputDir = Join-Path $workDirResolved "dex-in"
$dexOutputDir = Join-Path $workDirResolved "dex-out"
$unsignedApk = Join-Path $workDirResolved ($apkBaseName + "-unsigned.apk")
$alignedApk = Join-Path $workDirResolved ($apkBaseName + "-aligned.apk")

if ([string]::IsNullOrWhiteSpace($OutputApkPath)) {
    $OutputApkPath = Join-Path ([System.IO.Path]::GetDirectoryName($apkPathResolved)) ($apkBaseName + "-blackobf-signed.apk")
}

$outputApkResolved = [System.IO.Path]::GetFullPath($OutputApkPath)

if (Test-Path -LiteralPath $workDirResolved) {
    Remove-Item -Recurse -Force -LiteralPath $workDirResolved
}

New-Item -ItemType Directory -Path $dexInputDir -Force | Out-Null
New-Item -ItemType Directory -Path $dexOutputDir -Force | Out-Null

$originalApkDigest = Get-ApkSignerSha256 -ApkSignerPath $apksignerPath -TargetApk $apkPathResolved
$keystoreDigest = Get-KeystoreSha256 -KeytoolPath $keytoolPath -KeystoreFile $keystorePathResolved -Alias $KeyAlias -StorePassValue $StorePassword -KeyPassValue $KeyPassword

if ($originalApkDigest -and $originalApkDigest -ne $keystoreDigest) {
    Write-Warning "APK signer does not match target keystore. Existing installs may require uninstall before install."
    Write-Warning "APK SHA-256:      $originalApkDigest"
    Write-Warning "Keystore SHA-256: $keystoreDigest"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPathResolved)
$dexEntries = @()

try {
    $dexEntries = $zip.Entries |
        Where-Object { $_.FullName -match "^classes(\d*)?\.dex$" } |
        Sort-Object FullName

    if (-not $dexEntries) {
        throw "No classes*.dex entries found in APK."
    }

    foreach ($entry in $dexEntries) {
        $targetPath = Join-Path $dexInputDir ([System.IO.Path]::GetFileName($entry.FullName))
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetPath, $true)
    }
} finally {
    $zip.Dispose()
}

$replacementDexMap = @{}
$resultRows = @()

foreach ($entry in $dexEntries) {
    $dexName = [System.IO.Path]::GetFileName($entry.FullName)
    $inputDex = Join-Path $dexInputDir $dexName
    $outputDex = Join-Path $dexOutputDir ("obf-" + $dexName)

    Write-Host "Obfuscating $dexName ..." -ForegroundColor Cyan

    $javaArgs = @(
        "-cp", $classpath,
        "com.googlecode.dex2jar.tools.BlackObfuscatorCmd",
        "-d", "$Depth",
        "-i", $inputDex,
        "-o", $outputDex
    )

    if (-not [string]::IsNullOrWhiteSpace($rulesFileResolved)) {
        $javaArgs += @("-a", $rulesFileResolved)
    } else {
        $javaArgs += @("-p", $PackageName)
    }

    $javaArgs += "run"

    $commandOutput = Invoke-CheckedCommand -FilePath "java" -Arguments $javaArgs -CaptureOutput
    foreach ($line in $commandOutput) {
        $text = "$line"
        if (-not [string]::IsNullOrWhiteSpace($text)) {
            Write-Host $text
        }
    }

    $inputSize = (Get-Item -LiteralPath $inputDex).Length
    $outputExists = Test-Path -LiteralPath $outputDex
    $outputSize = if ($outputExists) { (Get-Item -LiteralPath $outputDex).Length } else { $inputSize }

    if ($outputExists) {
        $replacementDexMap[$entry.FullName] = $outputDex
    }

    $resultRows += [PSCustomObject]@{
        Dex          = $dexName
        Obfuscated   = $outputExists
        InputSize    = $inputSize
        OutputSize   = $outputSize
        Delta        = $outputSize - $inputSize
    }
}

if ($replacementDexMap.Count -eq 0) {
    throw "No dex files were obfuscated. Check -PackageName or -RulesFile."
}

Write-Host ""
Write-Host "Dex summary:" -ForegroundColor Green
$resultRows | Format-Table -AutoSize

if (Test-Path -LiteralPath $unsignedApk) {
    Remove-Item -Force -LiteralPath $unsignedApk
}

if (Test-Path -LiteralPath $alignedApk) {
    Remove-Item -Force -LiteralPath $alignedApk
}

if (Test-Path -LiteralPath $outputApkResolved) {
    Remove-Item -Force -LiteralPath $outputApkResolved
}

Write-Host ""
Write-Host "Repacking APK..." -ForegroundColor Cyan
Copy-ZipWithDexReplacement -InputApk $apkPathResolved -OutputApk $unsignedApk -ReplacementDexMap $replacementDexMap

Write-Host "Running zipalign..." -ForegroundColor Cyan
Invoke-CheckedCommand -FilePath $zipalignPath -Arguments @("-f", "-p", "4", $unsignedApk, $alignedApk)

Write-Host "Signing APK..." -ForegroundColor Cyan
Invoke-CheckedCommand -FilePath $apksignerPath -Arguments @(
    "sign",
    "--v1-signing-enabled", "false",
    "--v2-signing-enabled", "true",
    "--v3-signing-enabled", "false",
    "--v4-signing-enabled", "false",
    "--ks", $keystorePathResolved,
    "--ks-key-alias", $KeyAlias,
    "--ks-pass", "pass:$StorePassword",
    "--key-pass", "pass:$KeyPassword",
    "--out", $outputApkResolved,
    $alignedApk
)

Write-Host "Verifying APK..." -ForegroundColor Cyan
Invoke-CheckedCommand -FilePath $apksignerPath -Arguments @("verify", "--verbose", "--print-certs", $outputApkResolved)
Invoke-CheckedCommand -FilePath $zipalignPath -Arguments @("-c", "-p", "4", $outputApkResolved)

$signedDigest = Get-ApkSignerSha256 -ApkSignerPath $apksignerPath -TargetApk $outputApkResolved
$outputInfo = Get-Item -LiteralPath $outputApkResolved

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "Output APK : $($outputInfo.FullName)"
Write-Host "Size       : $($outputInfo.Length)"
Write-Host "Signer SHA : $signedDigest"

if (-not $KeepWorkDirectory) {
    Remove-Item -Recurse -Force -LiteralPath $workDirResolved
}
