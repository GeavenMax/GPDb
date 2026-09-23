<script setup lang="ts">
import { FolderCheck, ShieldCheck, HardDrive, FileSearch, X } from '@lucide/vue';

defineProps<{
  show: boolean;
  scanning?: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'pickFile'): void;
  (e: 'scanFolders'): void;
}>();
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="show"
        class="fixed inset-0 z-[150] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in select-none"
      >
        <div
          class="relative w-full max-w-lg rounded-3xl bg-surface border border-line shadow-2xl overflow-hidden p-6 md:p-8 space-y-6"
        >
          <!-- Top ambient light -->
          <div class="absolute -top-24 left-1/2 -translate-x-1/2 w-80 h-48 bg-gradient-to-b from-indigo-500/20 to-purple-500/0 blur-3xl pointer-events-none"></div>

          <!-- Header -->
          <div class="flex items-start justify-between gap-4 relative z-10">
            <div class="flex items-center gap-3.5">
              <div class="p-3 rounded-2xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-lg shadow-indigo-500/25">
                <ShieldCheck class="w-6 h-6" />
              </div>
              <div>
                <h3 class="text-lg md:text-xl font-black text-fg tracking-tight">
                  存储权限与本地影库说明
                </h3>
                <p class="text-xs text-fg-4 mt-0.5">
                  GPDb 秉持 100% 本地化与隐私安全原则
                </p>
              </div>
            </div>
            <button
              @click="emit('close')"
              class="p-2 rounded-xl bg-surface-2 hover:bg-surface-3 text-fg-3 hover:text-fg transition cursor-pointer"
            >
              <X class="w-4 h-4" />
            </button>
          </div>

          <!-- Description points -->
          <div class="space-y-3 relative z-10 text-xs text-fg-3 leading-relaxed">
            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <HardDrive class="w-4 h-4 text-indigo-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">为什么需要访问文件夹？</strong>
                为了加载您的本地影库数据库文件（<span class="font-mono text-indigo-300">gevi.db</span>）以及海报、剧照和演员头像缓存，客户端需要读取您存放在本地的特定文件夹（例如“文稿”或“下载”目录）。
              </div>
            </div>

            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <ShieldCheck class="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">零云端上传，完全保护隐私</strong>
                GPDb 为纯单机离线系统，绝不向任何外部服务器上传您的个人观影记录、收藏列表或本地文件。所有数据均存留在您的个人 Mac 上。
              </div>
            </div>

            <div class="p-3.5 rounded-2xl bg-surface-2/60 border border-line flex items-start gap-3">
              <FolderCheck class="w-4 h-4 text-purple-400 shrink-0 mt-0.5" />
              <div>
                <strong class="text-fg block mb-0.5">两种方式，由您决定</strong>
                您可以点击<strong>“手动选取数据库”</strong>仅对单个文件授权（系统不会申请整目录权限）；也可以点击<strong>“授权并扫描常用目录”</strong>快速自动定位。
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="space-y-2 pt-2 relative z-10">
            <div class="flex items-center gap-3">
              <!-- Pick file button -->
              <button
                @click="emit('pickFile')"
                class="flex-1 px-4 py-3 rounded-2xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs shadow-lg shadow-indigo-500/25 transition flex items-center justify-center gap-2 cursor-pointer active:scale-98"
              >
                <FileSearch class="w-4 h-4" />
                <span>手动选取数据库文件</span>
              </button>

              <!-- Scan folders button -->
              <button
                @click="emit('scanFolders')"
                :disabled="scanning"
                class="px-4 py-3 rounded-2xl bg-surface-2 hover:bg-surface-3 border border-line text-fg font-bold text-xs transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 active:scale-98"
              >
                <span>{{ scanning ? '扫描检索中…' : '授权并自动扫描' }}</span>
              </button>
            </div>

            <div class="text-center pt-1">
              <button
                @click="emit('close')"
                class="text-[11px] text-fg-4 hover:text-fg-3 transition cursor-pointer"
              >
                稍后在“应用设置”中手动配置
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
