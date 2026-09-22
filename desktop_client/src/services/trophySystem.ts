import { ref, computed } from 'vue';
import { analytics, type UserAnalytics, onAnalyticsEvent } from './analytics';
import { playTrophyUnlockSound } from '../utils/soundSynthesizer';
import { pluginsConfig } from './pluginManager';

export type TrophyTier = 'platinum' | 'gold' | 'silver' | 'bronze';

export interface Trophy {
  id: string;
  tier: TrophyTier;
  title: string;
  desc: string;
  icon: string; // semantic icon name
  unlockedAt: number | null;
  progress: (a: UserAnalytics) => { current: number; max: number };
  condition: (a: UserAnalytics) => boolean;
}

const TROPHIES_STORAGE_KEY = 'gevi_unlocked_trophies';

function loadUnlockedMap(): Record<string, number> {
  try {
    const raw = localStorage.getItem(TROPHIES_STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return {};
}

const unlockedMap = ref<Record<string, number>>(loadUnlockedMap());

function saveUnlockedMap() {
  localStorage.setItem(TROPHIES_STORAGE_KEY, JSON.stringify(unlockedMap.value));
}

// 77 Trophies Definition
export const TROPHIES: Trophy[] = [
  // PLATINUM (1)
  {
    id: 'platinum_master',
    tier: 'platinum',
    title: '至高全能收藏家',
    desc: '达成所有探索成就，解锁其余全部 76 座奖杯',
    icon: 'Crown',
    unlockedAt: null,
    progress: () => {
      const otherUnlocked = TROPHIES.filter(t => t.id !== 'platinum_master' && unlockedMap.value[t.id]).length;
      return { current: otherUnlocked, max: 76 };
    },
    condition: () => {
      const otherUnlocked = TROPHIES.filter(t => t.id !== 'platinum_master' && unlockedMap.value[t.id]).length;
      return otherUnlocked >= 76;
    },
  },

  // GOLD (6)
  {
    id: 'gold_movie_1000',
    tier: 'gold',
    title: '阅片浩瀚',
    desc: '累计探索浏览 2,500 部不同影片',
    icon: 'Film',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 2500), max: 2500 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 2500,
  },
  {
    id: 'gold_streak_30',
    tier: 'gold',
    title: '岁月沉浸',
    desc: '在客户端中累计活跃探索打卡 60 天',
    icon: 'Calendar',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.activeDays.length, 60), max: 60 }),
    condition: (a) => a.activeDays.length >= 60,
  },
  {
    id: 'gold_performer_500',
    tier: 'gold',
    title: '星河浩荡',
    desc: '累计了解探索 1,000 位不同演员档案',
    icon: 'Users',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 1000), max: 1000 }),
    condition: (a) => a.uniquePerformersViewed.length >= 1000,
  },
  {
    id: 'gold_favorite_200',
    tier: 'gold',
    title: '挚爱珍藏',
    desc: '个人收藏项目累计达到 500 项',
    icon: 'Heart',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 500), max: 500 }),
    condition: (a) => a.favoritesAddedCount >= 500,
  },
  {
    id: 'gold_focus_100h',
    tier: 'gold',
    title: '时光胶囊',
    desc: '在影视库中累计活跃专注时长达 200 小时',
    icon: 'Clock',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(Math.floor(a.totalFocusTimeSeconds / 3600), 200), max: 200 }),
    condition: (a) => a.totalFocusTimeSeconds >= 720000,
  },
  {
    id: 'gold_rating_100',
    tier: 'gold',
    title: '权威鉴赏家',
    desc: '为 300 部影片完成私密评星打分',
    icon: 'Star',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 300), max: 300 }),
    condition: (a) => a.ratingsCount >= 300,
  },

  // SILVER (20)
  {
    id: 'silver_movie_100',
    tier: 'silver',
    title: '百部初成',
    desc: '累计探索浏览 300 部影片',
    icon: 'Film',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 300), max: 300 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 300,
  },
  {
    id: 'silver_movie_300',
    tier: 'silver',
    title: '影海行舟',
    desc: '累计探索浏览 800 部影片',
    icon: 'Compass',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 800), max: 800 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 800,
  },
  {
    id: 'silver_movie_500',
    tier: 'silver',
    title: '影痴境界',
    desc: '累计探索浏览 1,500 部影片',
    icon: 'Flame',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 1500), max: 1500 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 1500,
  },
  {
    id: 'silver_episode_100',
    tier: 'silver',
    title: '片段捕手',
    desc: '累计查看鉴赏 250 个场景片段',
    icon: 'Layers',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 250), max: 250 }),
    condition: (a) => a.episodeViewsCount >= 250,
  },
  {
    id: 'silver_episode_300',
    tier: 'silver',
    title: '分集大观',
    desc: '累计查看鉴赏 750 个场景片段',
    icon: 'Tv',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 750), max: 750 }),
    condition: (a) => a.episodeViewsCount >= 750,
  },
  {
    id: 'silver_performer_50',
    tier: 'silver',
    title: '名伶初聚',
    desc: '累计了解 150 位演员档案',
    icon: 'Users',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 150), max: 150 }),
    condition: (a) => a.uniquePerformersViewed.length >= 150,
  },
  {
    id: 'silver_performer_100',
    tier: 'silver',
    title: '群星璀璨',
    desc: '累计了解 350 位演员档案',
    icon: 'Sparkles',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 350), max: 350 }),
    condition: (a) => a.uniquePerformersViewed.length >= 350,
  },
  {
    id: 'silver_performer_250',
    tier: 'silver',
    title: '阅尽千帆',
    desc: '累计了解 600 位演员档案',
    icon: 'Award',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 600), max: 600 }),
    condition: (a) => a.uniquePerformersViewed.length >= 600,
  },
  {
    id: 'silver_director_20',
    tier: 'silver',
    title: '执导之镜',
    desc: '累计查看 50 位导演档案',
    icon: 'Megaphone',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.directorViewsCount, 50), max: 50 }),
    condition: (a) => a.directorViewsCount >= 50,
  },
  {
    id: 'silver_director_50',
    tier: 'silver',
    title: '镜头大师',
    desc: '累计查看 120 位导演档案',
    icon: 'Video',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.directorViewsCount, 120), max: 120 }),
    condition: (a) => a.directorViewsCount >= 120,
  },
  {
    id: 'silver_studio_20',
    tier: 'silver',
    title: '厂牌巡礼',
    desc: '累计探索 40 家不同片商',
    icon: 'Building2',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.studioViewsCount, 40), max: 40 }),
    condition: (a) => a.studioViewsCount >= 40,
  },
  {
    id: 'silver_studio_50',
    tier: 'silver',
    title: '独具慧眼',
    desc: '累计探索 100 家不同片商',
    icon: 'Landmark',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.studioViewsCount, 100), max: 100 }),
    condition: (a) => a.studioViewsCount >= 100,
  },
  {
    id: 'silver_search_50',
    tier: 'silver',
    title: '灵动探索引擎',
    desc: '全库执行精准检索达到 150 次',
    icon: 'Search',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount, 150), max: 150 }),
    condition: (a) => a.searchesCount >= 150,
  },
  {
    id: 'silver_search_150',
    tier: 'silver',
    title: '离线雷达',
    desc: '全库执行精准检索达到 350 次',
    icon: 'Radar',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount, 350), max: 350 }),
    condition: (a) => a.searchesCount >= 350,
  },
  {
    id: 'silver_favorite_50',
    tier: 'silver',
    title: '黄金藏品',
    desc: '个人收藏项目达到 150 项',
    icon: 'Bookmark',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 150), max: 150 }),
    condition: (a) => a.favoritesAddedCount >= 150,
  },
  {
    id: 'silver_favorite_100',
    tier: 'silver',
    title: '殿堂陈列',
    desc: '个人收藏项目达到 300 项',
    icon: 'Gem',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 300), max: 300 }),
    condition: (a) => a.favoritesAddedCount >= 300,
  },
  {
    id: 'silver_rating_30',
    tier: 'silver',
    title: '独到品味',
    desc: '累计评星打分达到 80 次',
    icon: 'Star',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 80), max: 80 }),
    condition: (a) => a.ratingsCount >= 80,
  },
  {
    id: 'silver_rating_50',
    tier: 'silver',
    title: '审美品位',
    desc: '累计评星打分达到 150 次',
    icon: 'CheckCircle',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 150), max: 150 }),
    condition: (a) => a.ratingsCount >= 150,
  },
  {
    id: 'silver_focus_10h',
    tier: 'silver',
    title: '专注凝视',
    desc: '累计活跃探索时长达到 30 小时',
    icon: 'Hourglass',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(Math.floor(a.totalFocusTimeSeconds / 3600), 30), max: 30 }),
    condition: (a) => a.totalFocusTimeSeconds >= 108000,
  },
  {
    id: 'silver_streak_7',
    tier: 'silver',
    title: '两周之约',
    desc: '在客户端中连续打卡活跃 14 天',
    icon: 'CheckCheck',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.activeDays.length, 14), max: 14 }),
    condition: (a) => a.activeDays.length >= 14,
  },

  // BRONZE (50)
  {
    id: 'bronze_first_movie',
    tier: 'bronze',
    title: '初次邂逅',
    desc: '探索并打开第 1 部影片详情',
    icon: 'Film',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.movieViewsCount, 1), max: 1 }),
    condition: (a) => a.movieViewsCount >= 1,
  },
  {
    id: 'bronze_movie_10',
    tier: 'bronze',
    title: '渐入佳境',
    desc: '探索并浏览 10 部影片',
    icon: 'PlayCircle',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 10), max: 10 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 10,
  },
  {
    id: 'bronze_movie_25',
    tier: 'bronze',
    title: '影海探步',
    desc: '探索并浏览 25 部影片',
    icon: 'Folder',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 25), max: 25 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 25,
  },
  {
    id: 'bronze_movie_50',
    tier: 'bronze',
    title: '半百里程',
    desc: '探索并浏览 50 部影片',
    icon: 'Layers',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniqueMoviesViewed.length, 50), max: 50 }),
    condition: (a) => a.uniqueMoviesViewed.length >= 50,
  },
  {
    id: 'bronze_first_performer',
    tier: 'bronze',
    title: '注目初识',
    desc: '探索并打开第 1 位演员档案',
    icon: 'User',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.performerViewsCount, 1), max: 1 }),
    condition: (a) => a.performerViewsCount >= 1,
  },
  {
    id: 'bronze_performer_10',
    tier: 'bronze',
    title: '名伶巡礼',
    desc: '累计了解 10 位演员档案',
    icon: 'Users',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 10), max: 10 }),
    condition: (a) => a.uniquePerformersViewed.length >= 10,
  },
  {
    id: 'bronze_performer_25',
    tier: 'bronze',
    title: '面孔印记',
    desc: '累计了解 25 位演员档案',
    icon: 'Smile',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.uniquePerformersViewed.length, 25), max: 25 }),
    condition: (a) => a.uniquePerformersViewed.length >= 25,
  },
  {
    id: 'bronze_first_episode',
    tier: 'bronze',
    title: '场景掠影',
    desc: '探索并查看第 1 个分集片段',
    icon: 'Clapperboard',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 1), max: 1 }),
    condition: (a) => a.episodeViewsCount >= 1,
  },
  {
    id: 'bronze_episode_10',
    tier: 'bronze',
    title: '精彩碎片',
    desc: '探索查看 10 个场景分集',
    icon: 'Layers',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 10), max: 10 }),
    condition: (a) => a.episodeViewsCount >= 10,
  },
  {
    id: 'bronze_episode_25',
    tier: 'bronze',
    title: '片段收藏家',
    desc: '探索查看 25 个场景分集',
    icon: 'Grid',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 25), max: 25 }),
    condition: (a) => a.episodeViewsCount >= 25,
  },
  {
    id: 'bronze_episode_50',
    tier: 'bronze',
    title: '半百片段',
    desc: '探索查看 50 个场景分集',
    icon: 'Film',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.episodeViewsCount, 50), max: 50 }),
    condition: (a) => a.episodeViewsCount >= 50,
  },
  {
    id: 'bronze_first_director',
    tier: 'bronze',
    title: '幕后之人',
    desc: '首次打开一位导演的专属档案',
    icon: 'Megaphone',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.directorViewsCount, 1), max: 1 }),
    condition: (a) => a.directorViewsCount >= 1,
  },
  {
    id: 'bronze_director_5',
    tier: 'bronze',
    title: '掌控镜头',
    desc: '累计查看 5 位导演档案',
    icon: 'Video',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.directorViewsCount, 5), max: 5 }),
    condition: (a) => a.directorViewsCount >= 5,
  },
  {
    id: 'bronze_director_10',
    tier: 'bronze',
    title: '导演名册',
    desc: '累计查看 10 位导演档案',
    icon: 'BookOpen',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.directorViewsCount, 10), max: 10 }),
    condition: (a) => a.directorViewsCount >= 10,
  },
  {
    id: 'bronze_first_studio',
    tier: 'bronze',
    title: '厂牌初探',
    desc: '首次进入一家片商的作品展架',
    icon: 'Building2',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.studioViewsCount, 1), max: 1 }),
    condition: (a) => a.studioViewsCount >= 1,
  },
  {
    id: 'bronze_studio_5',
    tier: 'bronze',
    title: '五大品牌',
    desc: '累计探索 5 家不同片商',
    icon: 'Building',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.studioViewsCount, 5), max: 5 }),
    condition: (a) => a.studioViewsCount >= 5,
  },
  {
    id: 'bronze_studio_10',
    tier: 'bronze',
    title: '厂牌寻踪',
    desc: '累计探索 10 家不同片商',
    icon: 'Compass',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.studioViewsCount, 10), max: 10 }),
    condition: (a) => a.studioViewsCount >= 10,
  },
  {
    id: 'bronze_first_search',
    tier: 'bronze',
    title: '关键词探针',
    desc: '在搜索框完成第 1 次检索',
    icon: 'Search',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount, 1), max: 1 }),
    condition: (a) => a.searchesCount >= 1,
  },
  {
    id: 'bronze_search_10',
    tier: 'bronze',
    title: '熟能生巧',
    desc: '累计执行搜索 10 次',
    icon: 'Sparkles',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount, 10), max: 10 }),
    condition: (a) => a.searchesCount >= 10,
  },
  {
    id: 'bronze_search_25',
    tier: 'bronze',
    title: '检索大师',
    desc: '累计执行搜索 25 次',
    icon: 'Zap',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount, 25), max: 25 }),
    condition: (a) => a.searchesCount >= 25,
  },
  {
    id: 'bronze_first_favorite',
    tier: 'bronze',
    title: '心动瞬间',
    desc: '收藏第 1 部影片或演员',
    icon: 'Heart',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 1), max: 1 }),
    condition: (a) => a.favoritesAddedCount >= 1,
  },
  {
    id: 'bronze_favorite_5',
    tier: 'bronze',
    title: '珍藏五选',
    desc: '收藏项目达到 5 项',
    icon: 'Bookmark',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 5), max: 5 }),
    condition: (a) => a.favoritesAddedCount >= 5,
  },
  {
    id: 'bronze_favorite_10',
    tier: 'bronze',
    title: '私家珍品',
    desc: '收藏项目达到 10 项',
    icon: 'FolderHeart',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 10), max: 10 }),
    condition: (a) => a.favoritesAddedCount >= 10,
  },
  {
    id: 'bronze_favorite_20',
    tier: 'bronze',
    title: '珍藏成簇',
    desc: '收藏项目达到 20 项',
    icon: 'Package',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.favoritesAddedCount, 20), max: 20 }),
    condition: (a) => a.favoritesAddedCount >= 20,
  },
  {
    id: 'bronze_first_rating',
    tier: 'bronze',
    title: '评委时刻',
    desc: '首次为一部作品赋予星级评分',
    icon: 'Star',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 1), max: 1 }),
    condition: (a) => a.ratingsCount >= 1,
  },
  {
    id: 'bronze_rating_5',
    tier: 'bronze',
    title: '严苛标尺',
    desc: '完成 5 次私密打分',
    icon: 'CheckCircle2',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 5), max: 5 }),
    condition: (a) => a.ratingsCount >= 5,
  },
  {
    id: 'bronze_rating_10',
    tier: 'bronze',
    title: '十佳评选',
    desc: '完成 10 次私密打分',
    icon: 'Sliders',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 10), max: 10 }),
    condition: (a) => a.ratingsCount >= 10,
  },
  {
    id: 'bronze_rating_20',
    tier: 'bronze',
    title: '阅历见证',
    desc: '完成 20 次私密打分',
    icon: 'Award',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.ratingsCount, 20), max: 20 }),
    condition: (a) => a.ratingsCount >= 20,
  },
  {
    id: 'bronze_night_owl_1',
    tier: 'bronze',
    title: '午夜放映厅',
    desc: '在深夜（23:00 - 05:00）探索影视库',
    icon: 'Moon',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.nightOwlViewsCount, 1), max: 1 }),
    condition: (a) => a.nightOwlViewsCount >= 1,
  },
  {
    id: 'bronze_night_owl_5',
    tier: 'bronze',
    title: '深夜游侠',
    desc: '在深夜累计探索 5 次',
    icon: 'Ghost',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.nightOwlViewsCount, 5), max: 5 }),
    condition: (a) => a.nightOwlViewsCount >= 5,
  },
  {
    id: 'bronze_night_owl_10',
    tier: 'bronze',
    title: '极夜沉醉',
    desc: '在深夜累计探索 10 次',
    icon: 'Sparkle',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.nightOwlViewsCount, 10), max: 10 }),
    condition: (a) => a.nightOwlViewsCount >= 10,
  },
  {
    id: 'bronze_first_tag',
    tier: 'bronze',
    title: '个性印记',
    desc: '首次创建自定义分类标签',
    icon: 'Tag',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.tagsCreatedCount, 1), max: 1 }),
    condition: (a) => a.tagsCreatedCount >= 1,
  },
  {
    id: 'bronze_tag_3',
    tier: 'bronze',
    title: '标签整理者',
    desc: '累计创建 3 个专属分类标签',
    icon: 'Tags',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.tagsCreatedCount, 3), max: 3 }),
    condition: (a) => a.tagsCreatedCount >= 3,
  },
  {
    id: 'bronze_first_translation',
    tier: 'bronze',
    title: '跨越语界',
    desc: '首次使用 AI 翻译剧情简介或分集',
    icon: 'Languages',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.translationsCount, 1), max: 1 }),
    condition: (a) => a.translationsCount >= 1,
  },
  {
    id: 'bronze_translation_5',
    tier: 'bronze',
    title: '语言之桥',
    desc: '累计使用 AI 翻译 5 次',
    icon: 'Globe',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.translationsCount, 5), max: 5 }),
    condition: (a) => a.translationsCount >= 5,
  },
  {
    id: 'bronze_streak_2',
    tier: 'bronze',
    title: '再次相遇',
    desc: '探索打卡达到 2 天',
    icon: 'Calendar',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.activeDays.length, 2), max: 2 }),
    condition: (a) => a.activeDays.length >= 2,
  },
  {
    id: 'bronze_streak_3',
    tier: 'bronze',
    title: '三日连珠',
    desc: '探索打卡达到 3 天',
    icon: 'Flame',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.activeDays.length, 3), max: 3 }),
    condition: (a) => a.activeDays.length >= 3,
  },
  {
    id: 'bronze_streak_5',
    tier: 'bronze',
    title: '工作日全勤',
    desc: '探索打卡达到 5 天',
    icon: 'Trophy',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.activeDays.length, 5), max: 5 }),
    condition: (a) => a.activeDays.length >= 5,
  },
  {
    id: 'bronze_focus_1h',
    tier: 'bronze',
    title: '一小时时光',
    desc: '累计活跃探索时长达到 1 小时',
    icon: 'Clock',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(Math.floor(a.totalFocusTimeSeconds / 3600), 1), max: 1 }),
    condition: (a) => a.totalFocusTimeSeconds >= 3600,
  },
  {
    id: 'bronze_focus_3h',
    tier: 'bronze',
    title: '三小时沉浸',
    desc: '累计活跃探索时长达到 3 小时',
    icon: 'Watch',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(Math.floor(a.totalFocusTimeSeconds / 3600), 3), max: 3 }),
    condition: (a) => a.totalFocusTimeSeconds >= 10800,
  },
  {
    id: 'bronze_focus_5h',
    tier: 'bronze',
    title: '悠然时光',
    desc: '累计活跃探索时长达到 5 小时',
    icon: 'Sun',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(Math.floor(a.totalFocusTimeSeconds / 3600), 5), max: 5 }),
    condition: (a) => a.totalFocusTimeSeconds >= 18000,
  },
  {
    id: 'bronze_plugin_bt',
    tier: 'bronze',
    title: '寻宝罗盘',
    desc: '启用 BT 磁力资源搜索扩展',
    icon: 'Compass',
    unlockedAt: null,
    progress: () => ({ current: pluginsConfig.value.btSearchEnabled ? 1 : 0, max: 1 }),
    condition: () => pluginsConfig.value.btSearchEnabled,
  },
  {
    id: 'bronze_plugin_toggle',
    tier: 'bronze',
    title: '极客定制',
    desc: '访问插件管理面板体验模块化扩展',
    icon: 'Sliders',
    unlockedAt: null,
    progress: () => ({ current: 1, max: 1 }),
    condition: () => true, // Will unlock when user visits plugin page
  },
  {
    id: 'bronze_db_scanned',
    tier: 'bronze',
    title: '数据库探险',
    desc: '在设置中查看本地离线数据库状态',
    icon: 'HardDrive',
    unlockedAt: null,
    progress: () => ({ current: 1, max: 1 }),
    condition: () => true,
  },
  {
    id: 'bronze_lang_switch',
    tier: 'bronze',
    title: '国际视界',
    desc: '多语种菜单与国际化功能就绪',
    icon: 'Globe2',
    unlockedAt: null,
    progress: () => ({ current: 1, max: 1 }),
    condition: () => true,
  },
  {
    id: 'bronze_filter_category',
    tier: 'bronze',
    title: '分类滤镜',
    desc: '探索 53 种原子分类体系',
    icon: 'Filter',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.searchesCount > 0 ? 1 : 0, 1), max: 1 }),
    condition: (a) => a.searchesCount >= 1,
  },
  {
    id: 'bronze_filter_year',
    tier: 'bronze',
    title: '年代追溯',
    desc: '探索不同历史年份的影视瑰宝',
    icon: 'History',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.movieViewsCount > 2 ? 1 : 0, 1), max: 1 }),
    condition: (a) => a.movieViewsCount >= 3,
  },
  {
    id: 'bronze_filter_duration',
    tier: 'bronze',
    title: '长度把控',
    desc: '探索不同时长规格的完整作品',
    icon: 'Timer',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.movieViewsCount > 4 ? 1 : 0, 1), max: 1 }),
    condition: (a) => a.movieViewsCount >= 5,
  },
  {
    id: 'bronze_grid_adjust',
    tier: 'bronze',
    title: '视野掌控',
    desc: '享受自由调节网格列数的瀑布流视觉',
    icon: 'LayoutGrid',
    unlockedAt: null,
    progress: () => ({ current: 1, max: 1 }),
    condition: () => true,
  },
  {
    id: 'bronze_alias_finder',
    tier: 'bronze',
    title: '千面一人',
    desc: '在演员档案中探索多艺名与生平印记',
    icon: 'Masks',
    unlockedAt: null,
    progress: (a) => ({ current: Math.min(a.performerViewsCount > 0 ? 1 : 0, 1), max: 1 }),
    condition: (a) => a.performerViewsCount >= 1,
  },
];

// Initialize unlocked timestamps from storage
for (const t of TROPHIES) {
  if (unlockedMap.value[t.id]) {
    t.unlockedAt = unlockedMap.value[t.id];
  }
}

// Current toast notification state
export const currentUnlockedToast = ref<Trophy | null>(null);

let toastTimer: number | null = null;

export function triggerTrophyUnlock(trophy: Trophy) {
  if (unlockedMap.value[trophy.id]) return; // already unlocked

  const now = Date.now();
  unlockedMap.value[trophy.id] = now;
  trophy.unlockedAt = now;
  saveUnlockedMap();

  if (pluginsConfig.value.trophiesEnabled) {
    // Play Web Audio Pure Algorithmic Chime only if sound enabled
    if (pluginsConfig.value.trophiesSoundEnabled) {
      playTrophyUnlockSound();
    }

    // Show Toast
    currentUnlockedToast.value = trophy;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => {
      currentUnlockedToast.value = null;
    }, 4500);
  }
}

export function resetUnlockedTrophies() {
  unlockedMap.value = {};
  saveUnlockedMap();
  for (const t of TROPHIES) {
    t.unlockedAt = null;
  }
}

// Check all trophies against current analytics
export function evaluateTrophies(a: UserAnalytics = analytics.value) {
  for (const t of TROPHIES) {
    if (!unlockedMap.value[t.id]) {
      if (t.condition(a)) {
        triggerTrophyUnlock(t);
      }
    }
  }
}

// Hook into analytics updates
onAnalyticsEvent((a) => {
  evaluateTrophies(a);
});

export const trophyStats = computed(() => {
  const total = TROPHIES.length;
  const unlocked = TROPHIES.filter(t => Boolean(unlockedMap.value[t.id])).length;
  const platinum = TROPHIES.filter(t => t.tier === 'platinum' && unlockedMap.value[t.id]).length;
  const gold = TROPHIES.filter(t => t.tier === 'gold' && unlockedMap.value[t.id]).length;
  const silver = TROPHIES.filter(t => t.tier === 'silver' && unlockedMap.value[t.id]).length;
  const bronze = TROPHIES.filter(t => t.tier === 'bronze' && unlockedMap.value[t.id]).length;

  return {
    total,
    unlocked,
    percentage: Math.round((unlocked / total) * 100),
    platinum,
    gold,
    silver,
    bronze,
  };
});
