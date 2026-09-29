@echo off
chcp 65001 >nul
echo =========================================
echo   GPDb Windows Client Build Script
echo =========================================

where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Node.js，请先安装 Node.js 18+ (https://nodejs.org/)
    pause
    exit /b 1
)

where cargo >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Rust，请先安装 Rust (https://rustup.rs/)
    pause
    exit /b 1
)

echo.
echo --> 正在安装前端依赖...
call npm install
if %errorlevel% neq 0 (
    echo [错误] npm install 失败
    pause
    exit /b 1
)

echo.
echo --> 正在编译打包 Windows NSIS 安装程序...
call npm run tauri:build
if %errorlevel% neq 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)

echo.
echo =========================================
echo  [成功] Windows 安装程序构建完成！
echo  产物位置: src-tauri\target\release\bundle\nsis\
echo =========================================
pause
