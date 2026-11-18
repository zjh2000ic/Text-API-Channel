# 构建 APK（Windows / PowerShell）
#
# 用法:
#   pwsh -File scripts/build-apk.ps1                # debug APK
#   pwsh -File scripts/build-apk.ps1 -Release       # release APK（未签名，需要自己配签名）
#
# 依赖: 工程同级的 .toolchain 目录（jdk / gradle / android-sdk），或环境变量
#       JAVA_HOME / ANDROID_HOME 已配置好。

param(
    [switch]$Release,
    [string]$Task = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$toolchain = Join-Path $workspaceRoot ".toolchain"

function Find-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }
    $candidates = Get-ChildItem -Path (Join-Path $toolchain "jdk*") -Directory -ErrorAction SilentlyContinue
    foreach ($dir in $candidates) {
        if (Test-Path (Join-Path $dir.FullName "bin\javac.exe")) { return $dir.FullName }
        $inner = Get-ChildItem -Path $dir.FullName -Directory -ErrorAction SilentlyContinue
        foreach ($sub in $inner) {
            if (Test-Path (Join-Path $sub.FullName "bin\javac.exe")) { return $sub.FullName }
        }
    }
    return $null
}

function Find-GradleHome {
    $candidates = Get-ChildItem -Path (Join-Path $toolchain "gradle-*") -Directory -ErrorAction SilentlyContinue
    foreach ($dir in $candidates) {
        if (Test-Path (Join-Path $dir.FullName "bin\gradle.bat")) { return $dir.FullName }
        $inner = Get-ChildItem -Path $dir.FullName -Directory -ErrorAction SilentlyContinue
        foreach ($sub in $inner) {
            if (Test-Path (Join-Path $sub.FullName "bin\gradle.bat")) { return $sub.FullName }
        }
    }
    return $null
}

$jdkHome = Find-JdkHome
if (-not $jdkHome) { throw "找不到 JDK 17：请设置 JAVA_HOME，或把 JDK 解压到 $toolchain\jdk17" }
$env:JAVA_HOME = $jdkHome

$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome -or -not (Test-Path $sdkHome)) {
    $sdkHome = Join-Path $toolchain "android-sdk"
}
if (-not (Test-Path (Join-Path $sdkHome "platforms"))) {
    throw "找不到 Android SDK：请设置 ANDROID_HOME，或把 SDK 装到 $sdkHome"
}
$env:ANDROID_HOME = $sdkHome
$env:ANDROID_SDK_ROOT = $sdkHome
# Gradle / AGP 的缓存和临时目录也放在工程目录里，避免写到用户目录外被沙箱拒绝
$env:GRADLE_USER_HOME = Join-Path $toolchain "gradle-home"
$env:ANDROID_USER_HOME = Join-Path $toolchain "android-user-home"
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME, $env:ANDROID_USER_HOME,
    (Join-Path $toolchain "tmp") | Out-Null
$env:TEMP = Join-Path $toolchain "tmp"
$env:TMP = $env:TEMP

# local.properties 里的路径要转义反斜杠
$sdkDir = $sdkHome.Replace("\", "\\")
Set-Content -Path (Join-Path $projectRoot "local.properties") -Encoding ASCII `
    -Value "sdk.dir=$sdkDir`n"

$gradleHome = Find-GradleHome
$gradle = if ($gradleHome) { Join-Path $gradleHome "bin\gradle.bat" } else { "gradle" }

if (-not $Task) { $Task = if ($Release) { "assembleRelease" } else { "assembleDebug" } }

Write-Host "JAVA_HOME   = $jdkHome"
Write-Host "ANDROID_HOME= $sdkHome"
Write-Host "gradle      = $gradle"
Write-Host "task        = $Task"

# PowerShell 5.1 会把原生命令写到 stderr 的任何内容（例如 javac 的 "使用或覆盖了已过时的 API"
# 这类提示）包装成 NativeCommandError；在 $ErrorActionPreference = "Stop" 下会直接终止脚本，
# 造成"构建其实成功了但脚本报失败". 这里临时放宽为 Continue，改为按退出码判断成败。
$ErrorActionPreference = "Continue"
& $gradle --no-daemon -p $projectRoot $Task
$buildExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($buildExit -ne 0) { throw "gradle 构建失败（exit $buildExit）" }

$apkDir = if ($Release) { "release" } else { "debug" }
$built = Get-ChildItem -Path (Join-Path $projectRoot "app\build\outputs\apk\$apkDir") -Filter *.apk -ErrorAction SilentlyContinue
if (-not $built) { throw "没有找到构建出的 APK" }

$outDir = Join-Path $projectRoot "dist"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
foreach ($apk in $built) {
    Copy-Item $apk.FullName (Join-Path $outDir $apk.Name) -Force
    Write-Host ("APK: {0}  ({1:N1} MB)" -f (Join-Path $outDir $apk.Name), ($apk.Length / 1MB))
}
