package it.federicorapetti.recalls

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import it.federicorapetti.recalls.image.PdfPhotoDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecallsApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.appScope.launch {
            container.scheduler.schedulePeriodic(container.settings.intervalHours.first())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient }))
                add(PdfPhotoDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
