<#
.SYNOPSIS
    GPDb Windows 客户端一键构建脚本 (PowerShell)
.DESCRIPTION
    自动检查 Node.js / Rust 环境，安装依赖并打包生成 Windows NSIS 安装包 (.exe)。
#>

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  GPDb Windows Client Build Script       " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. 检查 Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Error "未检测到 Node.js，请先安装 Node.js 18+ (https://nodejs.org/)"
}
$nodeVersion = node -v
Write-Host "[✓] Node.js 就绪: $nodeVersion" -ForegroundColor Green

# 2. 检查 Rust / Cargo
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    Write-Error "未检测到 Rust 工具链，请先安装 Rust (https://rustup.rs/)"
}
$rustVersion = cargo -V
Write-Host "[✓] Cargo 就绪: $rustVersion" -ForegroundColor Green

# 3. 安装前端依赖
Write-Host "`n--> 正在安装前端依赖..." -ForegroundColor Yellow
npm install

# 4. 构建前端产物
Write-Host "`n--> 正在构建前端资源 (Vue 3 + Vite)..." -ForegroundColor Yellow
npm run build

# 5. 打包 Windows NSIS 安装程序
Write-Host "`n--> 正在编译 Rust 后端并打包 NSIS 安装包..." -ForegroundColor Yellow
npm run tauri:build

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " [✓] Windows 客户端构建完成！" -ForegroundColor Green
Write-Host " 输出目录: src-tauri/target/release/bundle/nsis/" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
