package io.github.fianna76.studypack

import android.app.Application
import io.github.fianna76.studypack.di.AppContainer

class StudyPackApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
