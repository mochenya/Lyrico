package com.lonx.lyrico

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.lonx.lyrico.di.appModule
import com.lonx.lyrico.utils.coil.AudioCoverFetcher
import com.lonx.lyrico.utils.coil.AudioCoverKeyer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application(), SingletonImageLoader.Factory{
    companion object {
        @JvmStatic
        lateinit var context: App
    }
    override fun onCreate() {
        super.onCreate()
        context = this
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }
    }
    override fun newImageLoader(context: Context): ImageLoader {
        Log.d("newImageLoader", "newImageLoader")
        return ImageLoader.Builder(context)
            .components {
                add(AudioCoverKeyer())
                add(AudioCoverFetcher.Factory(context.contentResolver))
            }
            .diskCache {
                // 磁盘缓存：最多10MB，目录为应用缓存目录
                DiskCache.Builder()
                    .maxSizeBytes(10 * 1024 * 1024)
                    .directory(context.cacheDir.resolve("image_cache"))
                    .build()
            }
            .crossfade(true)
            .build()
    }
}