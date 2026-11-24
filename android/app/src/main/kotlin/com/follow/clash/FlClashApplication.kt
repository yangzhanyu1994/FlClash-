package com.follow.clash

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import com.follow.clash.common.GlobalState
import com.follow.clash.widget.HomeWidgetUpdater

class FlClashApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        GlobalState.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    HomeWidgetUpdater.refresh(this@FlClashApplication)
                }

                override fun onLowMemory() = Unit
            },
        )
        HomeWidgetUpdater.refresh(this)
    }
}
