package com.gpdb.android.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ============================================================
//  GpdbImageLoader — 全应用单例 ImageLoader
//
//  在 Application 类中初始化一次后，通过 LocalContext 或
//  CompositionLocal 注入给 Coil 的 rememberAsyncImagePainter。
//  注册了 ZipImageFetcher.Factory，
//  拦截所有 GpdbImageData 类型的加载请求。
// ============================================================
object GpdbImageLoader {

    fun build(context: Context): ImageLoader = ImageLoader.Builder(context)
        // ── 注册自定义 Fetcher（优先于内置 Fetcher 匹配）────────
        .components {
            add(ZipImageFetcher.Factory())
        }
        // ── 内存缓存：最多占用运行时最大堆的 20%（移动端保守策略）
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.20)
                .build()
        }
        // ── 磁盘缓存：缓存网络降级图片（ZIP 直读不走此层）────────
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("gpdb_coil_cache"))
                .maxSizeBytes(256L * 1024 * 1024)   // 256 MB
                .build()
        }
        // ── 禁用 RGB_565 降级（封面图需保留 ARGB_8888 质量）────
        .allowRgb565(false)
        // ── 允许硬件位图（GPU 加速渲染）─────────────────────────
        .allowHardware(true)
        // ── 网络客户端超时配置（第三层降级用）───────────────────
        .okHttpClient {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        .build()
}
