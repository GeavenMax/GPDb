import { api, IS_TAURI } from '../api';

export interface AppReleaseInfo {
  tagName: string;
  versionName: string;
  title: string;
  notes: string;
  assetUrl: string;
  assetName: string;
  assetSize: number;
}

const GITHUB_REPO = 'GeavenMax/GPDb';
export const CURRENT_VERSION = '2.15.0';

/**
 * 对比远程语义化版本号与当前客户端版本号
 */
export function isNewerVersion(remote: string, current: string = CURRENT_VERSION): boolean {
  try {
    const cleanRemote = remote.trim().replace(/^v/i, '').split('-')[0];
    const cleanCurrent = current.trim().replace(/^v/i, '').split('-')[0];
    const rParts = cleanRemote.split('.').map(n => parseInt(n, 10) || 0);
    const cParts = cleanCurrent.split('.').map(n => parseInt(n, 10) || 0);
    const maxLen = Math.max(rParts.length, cParts.length);
    for (let i = 0; i < maxLen; i++) {
      const r = rParts[i] ?? 0;
      const c = cParts[i] ?? 0;
      if (r > c) return true;
      if (r < c) return false;
    }
    return false;
  } catch {
    return false;
  }
}

export interface CheckUpdateDetailedResult {
  hasUpdate: boolean;
  release: AppReleaseInfo | null;
  remoteVersion?: string;
  currentVersion: string;
  error?: string;
}

/**
 * 细粒度检测更新，供设置页手动触发与即时反馈
 */
export async function checkAppUpdateDetailed(): Promise<CheckUpdateDetailedResult> {
  try {
    const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
      headers: {
        'Accept': 'application/vnd.github.v3+json'
      }
    });
    if (!res.ok) {
      return { hasUpdate: false, release: null, currentVersion: CURRENT_VERSION, error: `GitHub API 请求失败: HTTP ${res.status}` };
    }
    const json = await res.json();
    const tagName = json.tag_name || '';
    const remoteVersion = tagName.replace(/^v/i, '');

    if (!isNewerVersion(remoteVersion, CURRENT_VERSION)) {
      return { hasUpdate: false, release: null, remoteVersion, currentVersion: CURRENT_VERSION };
    }

    const assets = json.assets || [];

    // 优先匹配针对 Windows 的 EXE / NSIS 安装程序
    let targetAsset = assets.find((a: any) => {
      const name = (a.name || '').toLowerCase();
      return name.endsWith('.exe') || name.includes('windows') || name.includes('win');
    });

    if (!targetAsset && assets.length > 0) {
      targetAsset = assets[0];
    }

    if (!targetAsset) {
      return { hasUpdate: false, release: null, remoteVersion, currentVersion: CURRENT_VERSION, error: '新版本暂未上传对应的 Windows 安装包' };
    }

    const release: AppReleaseInfo = {
      tagName,
      versionName: remoteVersion,
      title: json.name || tagName,
      notes: json.body || '暂无更新说明',
      assetUrl: targetAsset.browser_download_url,
      assetName: targetAsset.name,
      assetSize: targetAsset.size || 0
    };

    return { hasUpdate: true, release, remoteVersion, currentVersion: CURRENT_VERSION };
  } catch (e: any) {
    return { hasUpdate: false, release: null, currentVersion: CURRENT_VERSION, error: e?.message || '网络连接超时或异常' };
  }
}

/**
 * 异步巡检 GitHub Releases 最新版本信息 (针对 Windows 环境优化资产匹配)
 */
export async function checkForAppUpdate(): Promise<AppReleaseInfo | null> {
  const res = await checkAppUpdateDetailed();
  return res.release;
}

/**
 * 下载安装包并实时回调进度，随后触发原生安装并安全退出当前进程
 */
export async function downloadAndInstallUpdate(
  release: AppReleaseInfo,
  onProgress: (percent: number) => void
): Promise<void> {
  const response = await fetch(release.assetUrl);
  if (!response.ok) throw new Error(`下载更新包失败: HTTP ${response.status}`);
  if (!response.body) throw new Error('响应流为空');

  const contentLength = Number(response.headers.get('Content-Length')) || release.assetSize || 0;
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let receivedBytes = 0;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    if (value) {
      chunks.push(value);
      receivedBytes += value.length;
      if (contentLength > 0) {
        const pct = Math.min(100, Math.round((receivedBytes / contentLength) * 100));
        onProgress(pct);
      }
    }
  }

  const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
  const combined = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.length;
  }

  if (IS_TAURI) {
    let binary = '';
    const step = 8192;
    for (let i = 0; i < combined.length; i += step) {
      const slice = combined.subarray(i, i + step);
      binary += String.fromCharCode.apply(null, slice as unknown as number[]);
    }
    const b64 = btoa(binary);

    const savedPath = await api.saveUpdateFile(release.assetName, b64);
    await api.installUpdateFile(savedPath);
  } else {
    const blob = new Blob([combined]);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = release.assetName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
}
