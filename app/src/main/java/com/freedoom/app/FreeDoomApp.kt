package com.freedoom.app

import android.app.Application
import com.freedoom.app.work.UsageSyncWorker

class FreeDoomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageSyncWorker.schedule(this)
    }
}
