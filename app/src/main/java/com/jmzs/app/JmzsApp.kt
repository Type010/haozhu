package com.jmzs.app

import android.app.Application
import com.jmzs.app.data.AppContainer

class JmzsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
