<script setup lang="ts">
import { ref } from 'vue';
import { pluginsConfig, savePluginsConfig, type BtSearchConfig } from '../../services/pluginManager';
import { t } from '../../i18n';
import { Compass, Blocks, Trash2, Sparkles } from '@lucide/vue';

const newBtTemplateName = ref('');
const newBtTemplateUrl = ref('');

function updateBtEngine(engine: BtSearchConfig['engine']) {
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      engine
    }
  });
}

function selectCustomBtTemplate(id: string) {
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      activeCustomId: id,
    }
  });
}

function addCustomBtTemplate() {
  const name = newBtTemplateName.value.trim();
  const template = newBtTemplateUrl.value.trim();
  if (!name || !template) return;
  const newTmpl = {
    id: `custom-${Date.now()}`,
    name,
    template: template.includes('{query}') ? template : `${template}{query}`,
  };
  const list = [...(pluginsConfig.value.btSearchConfig.customTemplates || []), newTmpl];
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      customTemplates: list,
      activeCustomId: newTmpl.id,
    }
  });
  newBtTemplateName.value = '';
  newBtTemplateUrl.value = '';
}

function deleteCustomBtTemplate(id: string) {
  const list = (pluginsConfig.value.btSearchConfig.customTemplates || []).filter(t => t.id !== id);
  let activeId = pluginsConfig.value.btSearchConfig.activeCustomId;
  if (activeId === id) {
    activeId = list[0]?.id || '';
  }
  savePluginsConfig({
    btSearchConfig: {
      ...pluginsConfig.value.btSearchConfig,
      customTemplates: list,
      activeCustomId: activeId,
    }
  });
}

function toggleWebJump(key: 'bftvPerformerEnabled' | 'bftvMovieEnabled' | 'googleSearchEnabled') {
  savePluginsConfig({
    webJumpConfig: {
      ...pluginsConfig.value.webJumpConfig,
      [key]: !pluginsConfig.value.webJumpConfig[key],
    },
  });
}
</script>

<template>
  <div class="p-6 rounded-3xl bg-surface/80 border border-line space-y-4 shadow-sm">
    <div class="flex items-start justify-between gap-4">
      <div class="flex items-center gap-3.5">
        <div class="p-3 rounded-2xl bg-sky-500/10 text-sky-400 border border-sky-500/20">
          <Compass class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h3 class="text-base font-bold text-fg">{{ t('plugins.resourceSearch', '资源搜索与外部扩展') }}</h3>
            <span class="text-[10px] px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-400 font-bold border border-sky-500/20">v2.0</span>
          </div>
          <p class="text-xs text-fg-4 mt-0.5">{{ t('plugins.resourceSearchDesc', '在影片、演员及分集页面一键跳转至主流 BT 磁力站点或外部资料网站检索相关资源') }}</p>
        </div>
      </div>

      <!-- Switch -->
      <label class="relative inline-flex items-center cursor-pointer">
        <input
          type="checkbox"
          v-model="pluginsConfig.resourceSearchEnabled"
          @change="savePluginsConfig({ resourceSearchEnabled: pluginsConfig.resourceSearchEnabled })"
          class="sr-only peer"
        />
        <div class="w-11 h-6 bg-surface-3 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-fill"></div>
      </label>
    </div>

    <!-- Configuration options (if enabled) -->
    <div v-if="pluginsConfig.resourceSearchEnabled" class="pt-4 border-t border-line/60 space-y-6 animate-fade-in">
      <!-- 1. BT Magnet Search Engines -->
      <div class="space-y-3">
        <div class="flex items-center gap-2">
          <Compass class="w-4 h-4 text-sky-400" />
          <div class="text-xs font-bold text-fg">BT 磁力搜索引擎预设：</div>
        </div>
        <div class="flex gap-2 flex-wrap">
          <button
            v-for="eng in [
              { id: 'bt4g', label: 'BT4G (默认推荐)' },
              { id: 'btsearch', label: 'BTSearch (love)' },
              { id: 'sukebei', label: 'Sukebei (Nyaa)' },
              { id: '1337x', label: '1337x' },
              { id: 'torrentgalaxy', label: 'TorrentGalaxy' },
              { id: 'custom', label: '自定义多模版库' }
            ]"
            :key="eng.id"
            @click="updateBtEngine(eng.id as any)"
            :class="[
              'px-3 py-1.5 rounded-xl text-xs font-semibold border transition cursor-pointer',
              pluginsConfig.btSearchConfig.engine === eng.id
                ? 'bg-accent-fill text-on-fill border-accent shadow'
                : 'bg-surface-2/80 text-fg-3 border-line hover:bg-surface-3 hover:text-fg'
            ]"
          >
            {{ eng.label }}
          </button>
        </div>

        <!-- Multiple Custom Templates Management -->
        <div v-if="pluginsConfig.btSearchConfig.engine === 'custom'" class="pt-2 space-y-3">
          <div class="text-[11px] text-fg-4">
            已保存的自定义检索模板列表（支持点击选中作为当前默认，搜索时将把 {query} 自动替换为影片名）：
          </div>

          <div class="space-y-2">
            <div
              v-for="tmpl in (pluginsConfig.btSearchConfig.customTemplates || [])"
              :key="tmpl.id"
              @click="selectCustomBtTemplate(tmpl.id)"
              class="p-3 rounded-2xl border transition flex items-center justify-between gap-3 cursor-pointer"
              :class="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id
                ? 'bg-accent-fill/10 border-accent text-fg shadow-sm'
                : 'bg-surface-2/70 border-line hover:border-line-strong text-fg-3 hover:text-fg'"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div
                  class="w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0"
                  :class="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id
                    ? 'border-accent bg-accent'
                    : 'border-line-strong bg-transparent'"
                >
                  <div v-if="pluginsConfig.btSearchConfig.activeCustomId === tmpl.id" class="w-1.5 h-1.5 rounded-full bg-white"></div>
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-bold truncate">{{ tmpl.name }}</div>
                  <div class="text-[10px] text-fg-4 font-mono truncate mt-0.5">{{ tmpl.template }}</div>
                </div>
              </div>

              <button
                @click.stop="deleteCustomBtTemplate(tmpl.id)"
                class="p-1.5 rounded-lg text-fg-4 hover:text-danger hover:bg-danger-fill/10 transition shrink-0 cursor-pointer"
                title="删除该模板"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <!-- Add new template form -->
          <div class="p-4 rounded-2xl bg-sunken/60 border border-line-strong/60 space-y-3">
            <div class="text-xs font-bold text-fg-2">添加新的自定义搜索站点：</div>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <input
                v-model="newBtTemplateName"
                placeholder="站点名称 (如: RuTracker)"
                class="bg-surface border border-line rounded-xl px-3 py-2 text-xs text-fg outline-none focus:border-accent"
              />
              <input
                v-model="newBtTemplateUrl"
                placeholder="检索 URL 模版 (含 {query})"
                class="sm:col-span-2 bg-surface border border-line rounded-xl px-3 py-2 text-xs text-fg font-mono outline-none focus:border-accent"
              />
            </div>
            <div class="flex justify-end">
              <button
                @click="addCustomBtTemplate"
                :disabled="!newBtTemplateName.trim() || !newBtTemplateUrl.trim()"
                class="px-4 py-1.5 rounded-xl bg-accent-fill text-on-fill text-xs font-bold hover:bg-accent-fill/90 transition disabled:opacity-40 cursor-pointer"
              >
                添加模版
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. Web Jump External Sites -->
      <div class="space-y-3 pt-3 border-t border-line/60">
        <div class="flex items-center gap-2">
          <Blocks class="w-4 h-4 text-amber-400" />
          <div class="text-xs font-bold text-fg">外部网站资料跳转与直达按钮：</div>
        </div>
        <p class="text-xs text-fg-4">
          启用后将在对应条目（影片或演员）页面展示一键直达按钮，与 BT 搜索按钮同排展示。各按钮可独立启用或关闭，互不干扰。
        </p>

        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 pt-1">
          <!-- BFTV Performer Jump -->
          <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
            <div class="space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full bg-amber-400"></span>
                  在 BFTV 搜索演员资料
                </span>
                <input
                  type="checkbox"
                  :checked="pluginsConfig.webJumpConfig.bftvPerformerEnabled"
                  @change="toggleWebJump('bftvPerformerEnabled')"
                  class="rounded accent-accent cursor-pointer"
                />
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                在演员档案页展示按钮，直达 BoyfriendTV 对应艺名的专页与高级搜索页。
              </p>
            </div>
            <div class="text-[10px] text-fg-5 font-mono truncate">
              boyfriendtv.com/pornstars/?q={name}
            </div>
          </div>

          <!-- BFTV Movie Jump -->
          <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
            <div class="space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full bg-orange-400"></span>
                  在 BFTV 搜索影片资料
                </span>
                <input
                  type="checkbox"
                  :checked="pluginsConfig.webJumpConfig.bftvMovieEnabled"
                  @change="toggleWebJump('bftvMovieEnabled')"
                  class="rounded accent-accent cursor-pointer"
                />
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                在影片详情页展示按钮，自动提取影片主标题（剥离序号与副标题）后精准检索。
              </p>
            </div>
            <div class="text-[10px] text-fg-5 font-mono truncate">
              boyfriendtv.com/search/?q={clean_title}
            </div>
          </div>

          <!-- Google Web Jump -->
          <div class="p-4 rounded-2xl bg-surface-2/60 border border-line flex flex-col justify-between gap-3">
            <div class="space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-fg flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full bg-blue-400"></span>
                  Google 快速全网搜索
                </span>
                <input
                  type="checkbox"
                  :checked="pluginsConfig.webJumpConfig.googleSearchEnabled"
                  @change="toggleWebJump('googleSearchEnabled')"
                  class="rounded accent-accent cursor-pointer"
                />
              </div>
              <p class="text-[11px] text-fg-4 leading-relaxed">
                以条目原名直接在 Google 进行全网检索，方便查找第三方影评与演员履历。
              </p>
            </div>
            <div class="text-[10px] text-fg-5 font-mono truncate">
              google.com/search?q={query}
            </div>
          </div>
        </div>

        <!-- Future Roadmap Notice -->
        <div class="p-3 rounded-xl bg-accent-fill/5 border border-accent-fill/15 flex items-center gap-2 text-xs text-fg-3">
          <Sparkles class="w-4 h-4 text-accent shrink-0" />
          <span>待办清单：后续版本将持续支持接入更多垂直百科站点（如 IAFD、AEBN、Radar 等）。</span>
        </div>
      </div>
    </div>
  </div>
</template>
