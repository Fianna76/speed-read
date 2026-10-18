package io.github.fianna76.speedread

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.fianna76.speedread.di.AppContainer
import io.github.fianna76.speedread.shortcuts.AppShortcuts

class SpeedReadApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startCustomNewsSources()
        container.startBackgroundNewsScheduling()
        AppShortcuts.publish(this)
    }

    /** News images share the app's HTTP connections; Coil keeps its own image caches. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient.newBuilder().cache(null).build() }))
        }
        .build()
}
