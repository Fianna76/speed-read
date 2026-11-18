package io.github.fianna76.studypack

import android.app.Application
import io.github.fianna76.studypack.di.AppContainer
import ru.noties.jlatexmath.JLatexMathAndroid

class StudyPackApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // LaTeX fonts load from the library's assets. Its init provider does this too; doing it
        // here as well keeps rendering working where providers are not started (tests).
        JLatexMathAndroid.init(this)
    }
}
