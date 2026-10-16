package io.github.fianna76.speedread

import android.app.Application
import io.github.fianna76.speedread.di.AppContainer

class SpeedReadApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
