<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { X, Film, Clock, Heart, Building2, Tag, Layers, Clapperboard, Star, Bookmark, CheckCircle2, Plus, Sparkles, Languages, Loader2 } from '@lucide/vue';
import type { Movie, UserTag } from '../types';
import { getImageUrl } from '../utils/image';
import { claimEscape } from '../utils/escape';
import { api, IS_TAURI } from '../api';

const props = defineProps<{
  movie: Movie | null;
  isFavorite?: boolean;
  /** Preferred synopsis language; falls back to the original when untranslated. */
  lang?: 'zh' | 'en';
  /**
   * Stacking order supplied by the parent. Detail views can open each other
   * (a performer's filmography links back to a movie and vice versa), so a
   * fixed z-index would let whichever modal happens to sit later in the DOM
   * cover the one the user just opened.
   */
  zIndex?: number;
  /**
   * Whether this is the topmost detail view. Both modals stay mounted while the
   * user navigates between them, and each listens for Escape on `window`, so
   * without this a single Escape would dismiss the whole stack.
   */
  isTop?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select-performer', performerId: number): void;
  (e: 'filter-studio', studioName: string): void;
  (e: 'toggle-favorite', movie: Movie): void;
  (e: 'user-data-changed', movieId: number): void;
}>();

const activeCoverIndex = ref(0);

/**
 * The private star rating / tags / notes block is an aside, not part of the
 * film's own data, so it stays collapsed behind a button in the header instead
 * of pushing the synopsis and cast off the first screenful.
 */
const showPrivate = ref(false);

// Synopses carry both the original English and (once translated) the Chinese text.
const zhDescription = ref<string | null>(null);
const showOriginal = ref(false);
const isTranslating = ref(false);
const translateError = ref('');

watch(() => props.movie, (m) => {
  zhDescription.value = m?.description_zh || null;
  showOriginal.value = false;
  translateError.value = '';
  showPrivate.value = false;
}, { immediate: true });

const hasZh = computed(() => Boolean(zhDescription.value?.trim()));

/** Chinese when available and wanted; the original otherwise. */
const displayedDescription = computed(() => {
  if (hasZh.value && !showOriginal.value) return zhDescription.value || '';
  return props.movie?.description || '';
});

async function translateNow() {
  if (!props.movie) return;
  isTranslating.value = true;
  translateError.value = '';
  const result = await api.translateMovie(props.movie.id);
  if (result) {
    zhDescription.value = result;
    showOriginal.value = false;
    emit('user-data-changed', props.movie.id);
  } else {
    translateError.value = '翻译失败，请确认已在终端配置好翻译服务的 API Key';
  }
  isTranslating.value = false;
}
const userRating = ref<number | null>(null);
const userStatus = ref<string | null>(null);
const userNotes = ref('');
const selectedTagIds = ref<number[]>([]);
const availableTags = ref<UserTag[]>([]);
const isCreatingTag = ref(false);
const newTagName = ref('');
const newTagColor = ref('#f59e0b');
const saveSuccess = ref(false);

/** Drives the badge on the toggle: the button should show at a glance
 *  whether this film already carries private annotations. */
const hasPrivateData = computed(() =>
  userRating.value != null ||
  Boolean(userStatus.value) ||
  Boolean(userNotes.value.trim()) ||
  selectedTagIds.value.length > 0
);

async function loadUserData() {
  if (!props.movie) return;
  // Available tags
  availableTags.value = await api.getUserTags();

  // Movie user data
  const data = await api.getMovieUserData(props.movie.id);
  if (data) {
    userRating.value = data.rating ?? null;
    userStatus.value = data.status ?? null;
    userNotes.value = data.notes ?? '';
    selectedTagIds.value = (data.tags || []).map((t: UserTag) => t.id);
  } else {
    userRating.value = null;
    userStatus.value = null;
    userNotes.value = '';
    selectedTagIds.value = [];
  }
}

watch(() => props.movie, () => {
  activeCoverIndex.value = 0;
  loadUserData();
}, { immediate: true });

async function persistUserData() {
  if (!props.movie) return;
  await api.saveMovieUserData(props.movie.id, {
    rating: userRating.value,
    status: userStatus.value,
    notes: userNotes.value,
    tag_ids: selectedTagIds.value,
  });
  saveSuccess.value = true;
  setTimeout(() => { saveSuccess.value = false; }, 2000);
  emit('user-data-changed', props.movie.id);
}

function setRating(val: number) {
  userRating.value = userRating.value === val ? null : val;
  persistUserData();
}

function setStatus(st: string) {
  userStatus.value = userStatus.value === st ? null : st;
  persistUserData();
}

function toggleTag(tagId: number) {
  if (selectedTagIds.value.includes(tagId)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => id !== tagId);
  } else {
    selectedTagIds.value.push(tagId);
  }
  persistUserData();
}

async function handleCreateTag() {
  if (!newTagName.value.trim()) return;
  const created = await api.createUserTag(newTagName.value.trim(), newTagColor.value);
  if (created) {
    availableTags.value.push(created);
    selectedTagIds.value.push(created.id);
    newTagName.value = '';
    isCreatingTag.value = false;
    persistUserData();
  }
}

const currentCover = computed(() => {
  if (props.movie?.covers && props.movie.covers.length > 0) {
    return props.movie.covers[activeCoverIndex.value] || props.movie.cover_full;
  }
  return props.movie?.cover_full || '';
});

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || props.isTop === false) return;
  if (!claimEscape(e)) return;
  // While the private-annotation panel is open it owns Escape.
  if (showPrivate.value) {
    showPrivate.value = false;
    return;
  }
  emit('close');
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <div
    v-if="movie"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8 bg-black/80 backdrop-blur-md animate-fade-in"
    :style="{ zIndex: zIndex ?? 50 }"
    @click.self="emit('close')"
  >
    <!-- Modal Card -->
    <div
      class="relative w-full max-w-4xl max-h-[90vh] bg-zinc-900 border border-zinc-700/80 rounded-3xl shadow-2xl overflow-y-auto flex flex-col darkScrollbars text-zinc-100"
    >
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-20 w-8 h-8 rounded-full bg-black/60 hover:bg-black/90 border border-white/20 flex items-center justify-center text-zinc-300 hover:text-white transition"
      >
        <X class="w-4 h-4" />
      </button>

      <!-- Hero Section with blurred backdrop -->
      <div class="relative w-full overflow-hidden bg-zinc-950 p-6 md:p-8 border-b border-zinc-800">
        <!-- Blurred background image -->
        <div
          v-if="movie.cover_full"
          class="absolute inset-0 bg-cover bg-center blur-2xl opacity-25 scale-110 pointer-events-none"
          :style="{ backgroundImage: `url(${movie.cover_full})` }"
        ></div>

        <!-- Foreground Content -->
        <div class="relative z-10 flex flex-col md:flex-row gap-6 items-start">
          <!-- Poster Container with Multi-Cover Switching -->
          <div class="flex flex-col items-center gap-2 shrink-0">
            <div class="w-44 md:w-56 aspect-[3/4] rounded-2xl overflow-hidden shadow-2xl border border-zinc-700/60 bg-zinc-950 relative group">
              <img
                v-if="currentCover"
                :src="currentCover"
                :alt="movie.title"
                referrerpolicy="no-referrer"
                class="w-full h-full object-cover transition-all duration-300"
              />
              <div v-else class="w-full h-full flex flex-col items-center justify-center text-zinc-600 p-4 text-center">
                <Film class="w-12 h-12 mb-2 stroke-1" />
                <span class="text-xs">无封面</span>
              </div>
            </div>

            <!-- Front / Back Cover Switcher Pills -->
            <div v-if="movie.covers && movie.covers.length > 1" class="flex gap-1.5 p-1 bg-zinc-900/90 border border-zinc-800 rounded-xl shadow">
              <button
                v-for="(_, idx) in movie.covers"
                :key="idx"
                @click="activeCoverIndex = idx"
                :class="[
                  'px-3 py-1 rounded-lg text-xs font-semibold transition',
                  activeCoverIndex === idx
                    ? 'bg-amber-500 text-black shadow'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800'
                ]"
              >
                {{ idx === 0 ? '正面封面' : idx === 1 ? '封底背面' : `封面 ${idx + 1}` }}
              </button>
            </div>
          </div>

          <!-- Metadata -->
          <div class="flex-1 space-y-4">
            <div class="flex items-center gap-2 flex-wrap">
              <span v-if="movie.release_year" class="px-2.5 py-1 rounded-lg text-xs font-bold bg-amber-500/10 text-amber-400 border border-amber-500/30">
                {{ movie.release_year }}
              </span>
              <span v-if="movie.duration_mins" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-zinc-800 text-zinc-300 border border-zinc-700 flex items-center gap-1.5">
                <Clock class="w-3.5 h-3.5" />
                {{ movie.duration_mins }} 分钟
              </span>
              <span v-if="movie.category" class="px-2.5 py-1 rounded-lg text-xs font-medium bg-zinc-800 text-zinc-300 border border-zinc-700">
                {{ movie.category }}
              </span>
              <!-- Private annotations live behind this button (aside, not film data) -->
              <button
                @click="showPrivate = !showPrivate"
                :class="[
                  'ml-auto px-3 py-1 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
                  showPrivate
                    ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                    : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-amber-400'
                ]"
                :title="showPrivate ? '收起我的私密评星与标记' : '展开我的私密评星、标签与笔记（仅本地可见）'"
              >
                <Sparkles class="w-3.5 h-3.5" />
                <span>我的标记</span>
                <span
                  v-if="hasPrivateData"
                  class="w-1.5 h-1.5 rounded-full bg-amber-400"
                  title="已有私密记录"
                ></span>
              </button>

              <button
                @click="emit('toggle-favorite', movie)"
                :class="[
                  'px-3 py-1 rounded-lg text-xs font-medium border flex items-center gap-1.5 transition',
                  isFavorite
                    ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                    : 'bg-zinc-800 hover:bg-zinc-700 border-zinc-700 text-zinc-400 hover:text-rose-400'
                ]"
              >
                <Heart class="w-3.5 h-3.5" :fill="isFavorite ? 'currentColor' : 'none'" />
                <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
              </button>
            </div>

            <h1 class="text-2xl md:text-3xl font-extrabold text-white tracking-tight">
              {{ movie.title }}
            </h1>

            <!-- Studio & Director Pills -->
            <div class="flex items-center gap-3 flex-wrap">
              <div v-if="movie.studio_name" class="flex items-center gap-2">
                <Building2 class="w-4 h-4 text-zinc-400" />
                <button
                  @click="emit('filter-studio', movie.studio_name)"
                  class="text-xs font-semibold text-amber-400 hover:underline bg-zinc-800/80 px-2.5 py-1 rounded-md border border-zinc-700/60"
                >
                  {{ movie.studio_name }}
                </button>
              </div>

              <div v-if="movie.director_name" class="flex items-center gap-2 bg-zinc-900 border border-zinc-800 px-2.5 py-1 rounded-md text-xs text-zinc-300">
                <Clapperboard class="w-3.5 h-3.5 text-amber-400" />
                <span class="text-zinc-500">导演:</span>
                <span class="font-medium text-zinc-200">{{ movie.director_name }}</span>
              </div>
            </div>

            <!-- Description (Chinese when translated, original otherwise) -->
            <div
              v-if="movie.description || hasZh"
              class="text-xs md:text-sm text-zinc-300 leading-relaxed bg-zinc-950/60 border border-zinc-800/60 p-4 rounded-xl"
            >
              <div class="flex items-center justify-between gap-2 mb-1.5">
                <div class="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Languages class="w-3 h-3" />
                  剧情简介
                  <span v-if="hasZh && !showOriginal" class="text-emerald-500/80 normal-case">中文</span>
                  <span v-else-if="hasZh" class="text-zinc-600 normal-case">原文</span>
                </div>

                <div class="flex items-center gap-2">
                  <button
                    v-if="hasZh"
                    @click="showOriginal = !showOriginal"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-zinc-200 border border-zinc-700 transition"
                  >
                    {{ showOriginal ? '显示中文' : '显示原文' }}
                  </button>
                  <button
                    v-else-if="movie.description && !IS_TAURI"
                    @click="translateNow"
                    :disabled="isTranslating"
                    class="text-[10px] px-2 py-0.5 rounded-md bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 transition flex items-center gap-1 disabled:opacity-50"
                  >
                    <Loader2 v-if="isTranslating" class="w-3 h-3 animate-spin" />
                    <Languages v-else class="w-3 h-3" />
                    {{ isTranslating ? '翻译中...' : '翻译成中文' }}
                  </button>
                  <!-- Desktop build has no server process to relay the request -->
                  <span
                    v-else-if="movie.description"
                    class="text-[10px] text-zinc-600"
                    title="桌面版请在项目目录运行 python3 translate.py 批量翻译"
                  >
                    未翻译
                  </span>
                </div>
              </div>

              <p class="whitespace-pre-line">{{ displayedDescription }}</p>

              <div v-if="translateError" class="text-[11px] text-rose-400 mt-2">
                {{ translateError }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- User Private Annotations & Custom Tags Section (toggled from the header) -->
      <div v-if="showPrivate" class="p-6 md:p-8 border-b border-zinc-800 bg-zinc-950/40 space-y-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
            <Sparkles class="w-4 h-4 text-amber-400" />
            <span>我的私密评星与标记 (仅本地可见)</span>
          </div>
          <div class="flex items-center gap-3">
            <span v-if="saveSuccess" class="text-xs text-emerald-400 font-medium animate-fade-in flex items-center gap-1">
              <CheckCircle2 class="w-3.5 h-3.5" /> 已自动保存
            </span>
            <button
              @click="showPrivate = false"
              class="w-6 h-6 rounded-lg bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 flex items-center justify-center text-zinc-400 hover:text-white transition"
              title="收起"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <!-- Left: Rating & Status -->
          <div class="p-4 rounded-2xl bg-zinc-900/80 border border-zinc-800 space-y-3">
            <!-- 5-Star interactive rater -->
            <div>
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5 flex items-center justify-between">
                <span>私密星级评分</span>
                <span class="text-amber-400 font-bold font-mono">{{ userRating ? `${userRating.toFixed(1)} 星` : '未评' }}</span>
              </div>
              <div class="flex items-center gap-1.5">
                <button
                  v-for="star in 5"
                  :key="star"
                  @click="setRating(star)"
                  class="p-1 hover:scale-125 transition-transform"
                  :title="`评分 ${star} 星`"
                >
                  <Star
                    class="w-6 h-6 transition-colors"
                    :class="userRating && userRating >= star ? 'text-amber-400 fill-amber-400' : 'text-zinc-600 hover:text-amber-300'"
                  />
                </button>
                <button
                  v-if="userRating"
                  @click="setRating(userRating)"
                  class="ml-2 text-[10px] text-zinc-500 hover:text-zinc-300 transition"
                >
                  清除
                </button>
              </div>
            </div>

            <!-- Status selector -->
            <div class="pt-2 border-t border-zinc-800">
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5">片单状态</div>
              <div class="flex gap-2">
                <button
                  v-for="st in [
                    { id: 'wishlist', label: '想看', icon: Bookmark },
                    { id: 'watched', label: '已看', icon: CheckCircle2 },
                    { id: 'favorite', label: '喜爱', icon: Heart }
                  ]"
                  :key="st.id"
                  @click="setStatus(st.id)"
                  :class="[
                    'flex-1 py-1.5 px-2 rounded-xl text-xs font-medium border flex items-center justify-center gap-1.5 transition',
                    userStatus === st.id
                      ? 'bg-amber-500 text-black border-amber-400 font-bold shadow'
                      : 'bg-zinc-800/80 text-zinc-400 border-zinc-700/60 hover:text-zinc-200 hover:bg-zinc-800'
                  ]"
                >
                  <component :is="st.icon" class="w-3.5 h-3.5" :fill="userStatus === st.id ? 'currentColor' : 'none'" />
                  <span>{{ st.label }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Right: Custom Tags & Personal Note -->
          <div class="p-4 rounded-2xl bg-zinc-900/80 border border-zinc-800 space-y-3">
            <!-- Custom Tags -->
            <div>
              <div class="text-[11px] text-zinc-400 font-medium mb-1.5 flex items-center justify-between">
                <span>自定义标签</span>
                <button
                  @click="isCreatingTag = !isCreatingTag"
                  class="text-[10px] text-amber-400 hover:text-amber-300 flex items-center gap-0.5"
                >
                  <Plus class="w-3 h-3" /> 新建标签
                </button>
              </div>

              <!-- Create new tag inline form -->
              <div v-if="isCreatingTag" class="flex items-center gap-2 mb-2 p-2 rounded-lg bg-zinc-950 border border-zinc-800">
                <input
                  v-model="newTagName"
                  type="text"
                  placeholder="标签名称"
                  @keyup.enter="handleCreateTag"
                  class="flex-1 bg-transparent text-xs text-zinc-200 outline-none placeholder-zinc-600"
                />
                <input
                  v-model="newTagColor"
                  type="color"
                  class="w-5 h-5 rounded cursor-pointer bg-transparent border-0"
                />
                <button
                  @click="handleCreateTag"
                  class="px-2 py-0.5 rounded bg-amber-500 text-black text-[11px] font-bold"
                >
                  添加
                </button>
              </div>

              <!-- Tag pills list -->
              <div class="flex flex-wrap gap-1.5 min-h-[28px]">
                <button
                  v-for="t in availableTags"
                  :key="t.id"
                  @click="toggleTag(t.id)"
                  :class="[
                    'px-2 py-0.5 rounded-lg text-xs font-medium border transition flex items-center gap-1',
                    selectedTagIds.includes(t.id)
                      ? 'shadow'
                      : 'opacity-50 hover:opacity-100'
                  ]"
                  :style="{
                    color: t.color,
                    borderColor: `${t.color}60`,
                    backgroundColor: selectedTagIds.includes(t.id) ? `${t.color}25` : 'transparent'
                  }"
                >
                  <span>{{ t.name }}</span>
                  <span v-if="selectedTagIds.includes(t.id)">✓</span>
                </button>
                <div v-if="availableTags.length === 0 && !isCreatingTag" class="text-xs text-zinc-600 italic">
                  点击右上角「新建标签」添加个人分类
                </div>
              </div>
            </div>

            <!-- Notes textarea -->
            <div class="pt-2 border-t border-zinc-800">
              <div class="text-[11px] text-zinc-400 font-medium mb-1">私密笔记 / 简评</div>
              <textarea
                v-model="userNotes"
                @blur="persistUserData"
                rows="2"
                placeholder="记录观后感、精彩节点或备忘..."
                class="w-full bg-zinc-950/80 border border-zinc-700/80 rounded-xl p-2.5 text-xs text-zinc-200 placeholder-zinc-600 outline-none focus:border-amber-500 transition resize-none"
              ></textarea>
            </div>
          </div>
        </div>
      </div>

      <!-- Cast Section -->
      <div v-if="movie.performers && movie.performers.length > 0" class="p-6 md:p-8 border-b border-zinc-800 space-y-3">
        <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
          <Tag class="w-4 h-4 text-amber-400" />
          <span>演职人员 ({{ movie.performers.length }})</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="p in movie.performers"
            :key="p.id"
            @click="emit('select-performer', p.id)"
            class="group flex items-center gap-2 px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-amber-500/10 border border-zinc-700 hover:border-amber-500/40 text-xs font-medium text-zinc-200 hover:text-amber-400 transition"
          >
            <div class="w-6 h-6 rounded-full overflow-hidden bg-zinc-700 flex items-center justify-center text-[10px] font-bold text-zinc-300 group-hover:bg-amber-500 group-hover:text-black transition shrink-0">
              <img
                v-if="p.image_url"
                :src="getImageUrl(p.image_url)"
                :alt="p.name"
                loading="lazy"
                referrerpolicy="no-referrer"
                class="w-full h-full object-cover object-top"
                @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')"
              />
              <span v-else>{{ p.name.charAt(0).toUpperCase() }}</span>
            </div>
            <span>{{ p.name }}</span>
          </button>
        </div>
      </div>

      <!-- Chapters / Scenes Section -->
      <div v-if="movie.episodes && movie.episodes.length > 0" class="p-6 md:p-8 space-y-4">
        <div class="flex items-center gap-2 text-sm font-bold text-zinc-300">
          <Layers class="w-4 h-4 text-amber-400" />
          <span>收录章节 / 场景片段 ({{ movie.episodes.length }})</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div
            v-for="ep in movie.episodes"
            :key="ep.id"
            class="flex flex-col bg-zinc-950/80 rounded-2xl border border-zinc-800/80 overflow-hidden hover:border-zinc-700 transition"
          >
            <!-- Scene thumbnail -->
            <div v-if="ep.thumbnail_url" class="relative w-full aspect-video bg-zinc-900 overflow-hidden">
              <img :src="getImageUrl(ep.thumbnail_url)" :alt="ep.title" loading="lazy" referrerpolicy="no-referrer" class="w-full h-full object-cover" />
            </div>

            <!-- Scene Info -->
            <div class="p-4 space-y-2 flex-1 flex flex-col justify-between">
              <div>
                <div class="text-xs font-bold text-amber-300">{{ ep.title }}</div>
                <div v-if="ep.description" class="text-xs text-zinc-400 mt-1 line-clamp-3 leading-relaxed">
                  {{ ep.description }}
                </div>
              </div>
              <div v-if="ep.action_notes" class="text-[10px] text-zinc-500 bg-zinc-900 px-2 py-1 rounded font-mono">
                动作标签: {{ ep.action_notes }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.98); }
  to { opacity: 1; transform: scale(1); }
}
.animate-fade-in {
  animation: fadeIn 0.18s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
</style>
