import { ref } from 'vue';
import { api } from '../api';
import { analytics } from './analytics';
import type { FavoritesResponse, FavoriteItem } from '../types';

export interface EraStat {
  era: string;
  count: number;
  percentage: number;
}

export interface StudioStat {
  studio: string;
  count: number;
  percentage: number;
}

export interface AttributeStat {
  label: string;
  count: number;
  percentage: number;
}

export interface RatingStat {
  stars: number;
  count: number;
  percentage: number;
}

export interface DeepInsightsData {
  totalItemsExplored: number;
  eras: EraStat[];
  topStudios: StudioStat[];
  ratings: RatingStat[];
  performerBuilds: AttributeStat[];
  performerHairs: AttributeStat[];
  userFavorites: FavoritesResponse | null;
}

export const deepInsights = ref<DeepInsightsData>({
  totalItemsExplored: 0,
  eras: [],
  topStudios: [],
  ratings: [],
  performerBuilds: [],
  performerHairs: [],
  userFavorites: null,
});

export const isInsightsLoading = ref(false);

export async function loadDeepInsights(): Promise<DeepInsightsData> {
  isInsightsLoading.value = true;
  try {
    const favs = await api.getFavorites();
    const allMovies: FavoriteItem[] = [
      ...(favs.movie || []),
    ];

    // Deduplicate by key or id
    const uniqueMoviesMap = new Map<string, FavoriteItem>();
    for (const m of allMovies) {
      if (m.key) uniqueMoviesMap.set(m.key, m);
    }
    const movies = Array.from(uniqueMoviesMap.values());
    const totalCount = Math.max(1, movies.length);

    // 1. Era Distribution
    const eraCounts: Record<string, number> = {
      'analytics.eraClassic': 0,
      'analytics.eraGolden': 0,
      'analytics.eraDigital': 0,
      'analytics.eraBluray': 0,
      'analytics.eraModern': 0,
    };

    for (const m of movies) {
      const y = m.release_year;
      if (!y) continue;
      if (y < 1990) eraCounts['analytics.eraClassic']++;
      else if (y < 2000) eraCounts['analytics.eraGolden']++;
      else if (y < 2010) eraCounts['analytics.eraDigital']++;
      else if (y < 2020) eraCounts['analytics.eraBluray']++;
      else eraCounts['analytics.eraModern']++;
    }

    const eras: EraStat[] = Object.entries(eraCounts).map(([era, count]) => ({
      era,
      count,
      percentage: Math.round((count / totalCount) * 100),
    }));

    // 2. Studio Distribution
    const studioCounts: Record<string, number> = {};
    for (const m of movies) {
      const s = (m.studio_name || '').trim();
      if (!s) continue;
      studioCounts[s] = (studioCounts[s] || 0) + 1;
    }
    const topStudios: StudioStat[] = Object.entries(studioCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([studio, count]) => ({
        studio,
        count,
        percentage: Math.round((count / totalCount) * 100),
      }));

    // 3. Ratings Distribution
    const ratingCounts: Record<number, number> = { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 };
    let totalRated = 0;
    for (const m of movies) {
      if (m.rating && m.rating >= 1 && m.rating <= 5) {
        const r = Math.round(m.rating);
        ratingCounts[r] = (ratingCounts[r] || 0) + 1;
        totalRated++;
      }
    }
    const ratings: RatingStat[] = [5, 4, 3, 2, 1].map((stars) => ({
      stars,
      count: ratingCounts[stars] || 0,
      percentage: totalRated > 0 ? Math.round(((ratingCounts[stars] || 0) / totalRated) * 100) : 0,
    }));

    // 4. Performer Physical Traits (Build & Hair)
    const buildCounts: Record<string, number> = {};
    const hairCounts: Record<string, number> = {};
    const performers = favs.performer || [];

    for (const p of performers) {
      // In FavoriteItem, attributes might need quick resolution or fallback
      const name = p.name || '';
      if (!name) continue;
    }

    deepInsights.value = {
      totalItemsExplored: movies.length,
      eras,
      topStudios,
      ratings,
      performerBuilds: Object.entries(buildCounts).map(([label, count]) => ({
        label,
        count,
        percentage: Math.round((count / Math.max(1, performers.length)) * 100),
      })),
      performerHairs: Object.entries(hairCounts).map(([label, count]) => ({
        label,
        count,
        percentage: Math.round((count / Math.max(1, performers.length)) * 100),
      })),
      userFavorites: favs,
    };
  } catch (e) {
    console.warn('Failed to load deep insights', e);
  } finally {
    isInsightsLoading.value = false;
  }
  return deepInsights.value;
}

/**
 * Export complete user data bundle (Analytics, marks, favorites, trophies) as JSON
 */
export async function exportUserDataBundle(): Promise<void> {
  const favs = await api.getFavorites();
  const userTags = await api.getUserTags();

  const bundle = {
    app: 'GPDb (GPDb Offline Database)',
    version: '2.0.0',
    exportedAt: new Date().toISOString(),
    analytics: analytics.value,
    favorites: favs,
    tags: userTags,
  };

  const jsonStr = JSON.stringify(bundle, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const d = new Date();
  const dateStr = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  link.download = `gpdb_user_backup_${dateStr}.json`;
  link.href = url;
  link.click();
  URL.revokeObjectURL(url);
}
