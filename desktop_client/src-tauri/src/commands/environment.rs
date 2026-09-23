//! 客户端运行环境检测与健康诊断。
//!
//! 检测项包含：
//! 1. Python 3 解释器是否可用、版本号、执行路径（自动化刮削与数据同步基石）
//! 2. Python 是否内置 sqlite3 模块
//! 3. 数据库文件是否就绪并包含有效 GPDb 表结构
//! 4. 扩展依赖（Playwright 浏览器自动化引擎）是否安装就绪
//! 5. 综合健康评估与缺失项提醒列表

use serde::{Deserialize, Serialize};
use std::process::Command;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct RuntimeEnvironmentInfo {
    /// 是否检测到 Python 3 运行环境
    pub python_installed: bool,
    /// Python 版本号 (如 "Python 3.12.3")
    pub python_version: Option<String>,
    /// Python 绝对路径 (如 "/usr/bin/python3")
    pub python_path: Option<String>,
    /// Python 内置 sqlite3 驱动是否可用
    pub sqlite3_available: bool,
    /// 核心数据库是否已就绪且可用
    pub database_ready: bool,
    /// 核心数据库当前路径
    pub database_path: Option<String>,
    /// 进阶组件 Playwright（穿透 Cloudflare 反爬）是否就绪
    pub playwright_available: bool,
    /// 核心必要项是否全部就绪 (python_installed && database_ready)
    pub all_ready: bool,
    /// 缺失项简短标签
    pub missing_items: Vec<String>,
    /// 针对性配置建议与一键安装指引
    pub recommendations: Vec<String>,
}

#[tauri::command]
pub fn check_runtime_environment() -> Result<RuntimeEnvironmentInfo, String> {
    let mut missing = Vec::new();
    let mut recs = Vec::new();

    // 1 & 2. 检测 Python 3 解释器及版本
    let python_bin = crate::commands::sync::resolve_python();
    let which_cmd = if cfg!(target_os = "windows") { "where" } else { "which" };

    let python_path = Command::new(which_cmd)
        .arg(&python_bin)
        .output()
        .ok()
        .and_then(|out| {
            if out.status.success() {
                let s = String::from_utf8_lossy(&out.stdout).trim().lines().next()?.to_string();
                if !s.is_empty() { Some(s) } else { None }
            } else {
                None
            }
        });

    let (python_installed, python_version) = match Command::new(&python_bin).arg("--version").output() {
        Ok(out) if out.status.success() => {
            let ver = String::from_utf8_lossy(&out.stdout).trim().to_string();
            let final_ver = if ver.is_empty() {
                String::from_utf8_lossy(&out.stderr).trim().to_string()
            } else {
                ver
            };
            (true, Some(final_ver))
        }
        _ => {
            missing.push("Python 3 运行环境缺失".to_string());
            #[cfg(target_os = "macos")]
            recs.push("建议在终端运行「xcode-select --install」或通过 brew「brew install python3」安装 Python 3。".to_string());
            #[cfg(target_os = "windows")]
            recs.push("请前往 python.org 官方网站下载安装 Python 3，安装时务必勾选「Add python.exe to PATH」。".to_string());
            #[cfg(not(any(target_os = "macos", target_os = "windows")))]
            recs.push("请在系统中安装 Python 3 并将其加入系统 PATH 环境变量。".to_string());
            (false, None)
        }
    };

    // 3. 检测 sqlite3 模块
    let sqlite3_available = if python_installed {
        match Command::new(&python_bin)
            .args(["-c", "import sqlite3; print('ok')"])
            .output()
        {
            Ok(out) => String::from_utf8_lossy(&out.stdout).contains("ok"),
            Err(_) => false,
        }
    } else {
        false
    };
    if python_installed && !sqlite3_available {
        missing.push("Python sqlite3 模块异常".to_string());
        recs.push("当前 Python 环境缺少 sqlite3 支持，请检查 Python 安装完整度。".to_string());
    }

    // 4. 检测数据库就绪状态
    let db_path_opt = crate::db::find_db_path();
    let (database_ready, database_path) = match db_path_opt {
        Some(ref p) if p.is_file() && crate::db::is_valid_gpdb_db(p) => {
            (true, Some(p.to_string_lossy().to_string()))
        }
        Some(ref p) => {
            missing.push("数据库文件结构不完整或无效".to_string());
            recs.push("当前指定的数据库文件无效，可在「缓存与设置」中切换或新建。".to_string());
            (false, Some(p.to_string_lossy().to_string()))
        }
        None => {
            missing.push("尚未连接或创建 GPDb.db 本地数据库".to_string());
            recs.push("请在首屏引导弹窗中点击「一键创建全新空白数据库」或指定已有的 GPDb.db。".to_string());
            (false, None)
        }
    };

    // 5. 检测可选进阶扩展 Playwright
    let playwright_available = if python_installed {
        match Command::new(&python_bin)
            .args(["-c", "import playwright; print('ok')"])
            .output()
        {
            Ok(out) => String::from_utf8_lossy(&out.stdout).contains("ok"),
            Err(_) => false,
        }
    } else {
        false
    };
    if !playwright_available && python_installed {
        recs.push("提示：安装 Playwright 可解锁全自动绕过 Cloudflare 抓取 BoyfriendTV 演员直链：运行「pip install playwright && playwright install chromium」即可。".to_string());
    }

    let all_ready = python_installed && database_ready && sqlite3_available;

    Ok(RuntimeEnvironmentInfo {
        python_installed,
        python_version,
        python_path,
        sqlite3_available,
        database_ready,
        database_path,
        playwright_available,
        all_ready,
        missing_items: missing,
        recommendations: recs,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_check_runtime_environment_does_not_panic() {
        let res = check_runtime_environment();
        assert!(res.is_ok(), "check_runtime_environment should never panic or return Err");
        let info = res.unwrap();
        println!("Runtime environment info: {:?}", info);
    }
}
