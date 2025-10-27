package com.meta.brain.file.recovery

import androidx.lifecycle.LifecycleOwner
import com.meta.brain.module.base.MetaBrainApp
import com.meta.brain.module.data.DataManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : MetaBrainApp() {

    override fun onCreate() {
        super.onCreate()
    }


    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Keep DataManager initialization for MetaBrain module
        DataManager.setStartActivity(MainActivity::class.java)
    }
}