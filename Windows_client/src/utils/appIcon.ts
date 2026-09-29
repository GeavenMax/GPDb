import { ref } from 'vue';

export type IconSchemeId = 'scheme-a' | 'scheme-b' | 'scheme-c' | 'scheme-d';

export interface IconScheme {
  id: IconSchemeId;
  name: string;
  subtitle: string;
  style: string;
  privacy: string;
  stars: number;
  badge?: string;
  description: string;
  tags: string[];
}

export const ICON_SCHEMES: IconScheme[] = [
  {
    id: 'scheme-a',
    name: '方案 A:「双雄火星图腾」',
    subtitle: 'The Twin Mars Monolith',
    style: '古典男体神话 · 阳刚神圣',
    privacy: '高（几何艺术）',
    stars: 5,
    badge: '默认推荐',
    description: '午夜蓝深渊背景，中心由两个相互交错缠绕连接的火星符号「♂ ♂」（象征双雄、同志社群）构成。24K 缎面拉丝金与冷冽钛合金质感，两环交叠处形成微凸透镜反光，沉稳强韧，极具力量感与自豪感。',
    tags: ['双雄图腾', '24K 缎面金', '冷冽钛金', '神圣几何']
  },
  {
    id: 'scheme-b',
    name: '方案 B:「黑曜石棱镜胶片之匣」',
    subtitle: 'The Obsidian Film Vault',
    style: '现代极简 · 奢华典藏',
    privacy: '极高（防窥推荐）',
    stars: 4,
    description: '深空灰微曲面黑曜石质感，中央微透 35mm 胶片孔。一道克制幽暗的投影光束穿透微细棱镜，折射出低饱和度彩虹霓虹微光，隐现立体字母「G」。在 Dock 栏外观宛如高端胶片暗房或剪辑软件，私密防窥性极佳。',
    tags: ['黑曜石质感', '35mm 胶片', '微棱镜色散', '克制防窥']
  },
  {
    id: 'scheme-c',
    name: '方案 C:「大卫躯干与赛博明暗」',
    subtitle: 'Classical Torso & Cinema Chiaroscuro',
    style: '先锋电影节海报 · 肉体美学致敬',
    privacy: '中等（画廊唯美）',
    stars: 3,
    description: '米开朗基罗《大卫》古典健美男体躯干（Torso）剪影，以电影伦勃朗侧逆光打亮肌肉线条。高光处泛着冷蓝与暗紫胶片反光，外圈环绕 35mm 胶片取景画框，充满艺术先锋质感与纯粹的男性肉体之美。',
    tags: ['大卫躯干', '伦勃朗光', '六块腹肌', '35mm 画框']
  },
  {
    id: 'scheme-d',
    name: '方案 D:「流光字母印章」',
    subtitle: 'Neon G-Vault Crest',
    style: '精密科技感 · 品牌微标',
    privacy: '极高（零成人暗示）',
    stars: 5,
    badge: '极简微标',
    description: '中心为现代大写字母「G」，笔划内部注入流动的暗红与琥珀金霓虹流体，周围环绕 8 片暗夜金属机械大光圈（Aperture）叶片，边缘带有精致的倒角反光。极度低调安全，任何人看都是高品质影音工具。',
    tags: ['相机光圈', '流光霓虹', '字母印章', '极简微标']
  }
];

import { api } from '../api';

const STORAGE_KEY = 'gpdb_selected_app_icon';

export const currentIconScheme = ref<IconSchemeId>(
  (localStorage.getItem(STORAGE_KEY) as IconSchemeId) || 'scheme-a'
);

export function setIconScheme(id: IconSchemeId) {
  currentIconScheme.value = id;
  localStorage.setItem(STORAGE_KEY, id);

  // Update dynamic favicon in document head
  updateFavicon(id);

  // Update macOS Dock icon immediately via native Cocoa main-thread dispatch
  api.setDockIcon(id).catch(err => {
    console.warn('Failed to update dock icon:', err);
  });
}

export function updateFavicon(schemeId: IconSchemeId) {
  const link: HTMLLinkElement =
    document.querySelector("link[rel*='icon']") || document.createElement('link');
  link.type = 'image/svg+xml';
  link.rel = 'shortcut icon';
  link.href = `/src/assets/icons/${schemeId}.svg`;
  document.getElementsByTagName('head')[0].appendChild(link);
}

export function initAppIcon() {
  const current = currentIconScheme.value;
  updateFavicon(current);
  // Immediately synchronize the user's selected icon scheme to the macOS Dock on startup
  api.setDockIcon(current).catch(err => {
    console.warn('Failed to sync dock icon on launch:', err);
  });
}
