package io.github.jpsubway.app

import android.app.Application
import io.github.jpsubway.app.data.sync.TimetableSyncWorker
import io.github.jpsubway.app.di.AppContainer

class JpSubwayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TimetableSyncWorker.schedule(this)
    }
}
