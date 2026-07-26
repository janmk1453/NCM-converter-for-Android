package com.mcn.fix

import android.app.Application
import com.mcn.fix.util.FairMemoryManager

class McnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FairMemoryManager.initialize(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 60) {
            FairMemoryManager.releaseMemoryNow()
        }
    }
}
