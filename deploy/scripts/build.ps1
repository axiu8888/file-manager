# 构建前端 dist + 后端 jar → deploy/dist（供 Docker 镜像使用）
# 用法: pwsh -File deploy/scripts/build.ps1
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$DeployDir = Join-Path $RootDir "deploy"
$DistDir = Join-Path $DeployDir "dist"
$FrontDir = Join-Path $RootDir "kiftd-front"
$BackDir = Join-Path $RootDir "kiftd-backend"

Write-Host "==> 项目根目录: $RootDir"
New-Item -ItemType Directory -Force -Path (Join-Path $DistDir "front") | Out-Null

# ---------- 前端 ----------
Write-Host "==> 构建前端 (npm)"
Set-Location $FrontDir
if (-not (Test-Path "node_modules")) {
  npm ci --prefer-offline
  if ($LASTEXITCODE -ne 0) { npm install }
}
npm run build
if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
Remove-Item -Recurse -Force (Join-Path $DistDir "front") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $DistDir "front") | Out-Null
Copy-Item -Recurse -Force (Join-Path $FrontDir "dist\*") (Join-Path $DistDir "front\")
Write-Host "==> 前端已复制到 $DistDir\front"

# ---------- 后端 ----------
Write-Host "==> 构建后端 jar (Gradle)"
Set-Location $BackDir

$gradlew = Join-Path $BackDir "gradlew.bat"
if (Test-Path $gradlew) {
  & $gradlew --no-daemon clean bootJar -x test
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
  gradle --no-daemon clean bootJar -x test
} else {
  throw "未找到 gradlew.bat 或 gradle"
}
if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }

$jar = Get-ChildItem (Join-Path $BackDir "build\libs") -Filter "*.jar" |
  Where-Object { $_.Name -notlike "*-plain.jar" } |
  Select-Object -First 1
if (-not $jar) { throw "未找到 bootJar 产物" }
Copy-Item -Force $jar.FullName (Join-Path $DistDir "app.jar")
Copy-Item -Force (Join-Path $DeployDir "application-prod.yml") (Join-Path $DistDir "application-prod.yml") -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "构建完成。"
Write-Host "  $DistDir\app.jar"
Write-Host "  $DistDir\front\"
Write-Host "下一步(Ubuntu): ./deploy/scripts/install.sh && /opt/apps/kiftd/scripts/start.sh"
