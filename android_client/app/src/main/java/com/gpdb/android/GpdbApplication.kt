package com.gpdb.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.gpdb.android.image.GpdbImageLoader

// ============================================================
//  GpdbApplication — 全局 Application 入口
// ============================================================
class GpdbApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * 实现 ImageLoaderFactory 接口，统一向 Coil 提供全局定制的 ImageLoader
     */
    override fun newImageLoader(): ImageLoader {
        return GpdbImageLoader.build(this)
    }
}
