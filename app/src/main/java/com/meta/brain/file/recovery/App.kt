package com.meta.brain.file.recovery

import androidx.lifecycle.LifecycleOwner
import com.meta.brain.module.base.MetaBrainApp
import com.meta.brain.module.data.DataManager
import com.meta.brain.file.recovery.ui.intro.IntroActivity
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class
App : MetaBrainApp() {
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        DataManager.setStartActivity(IntroActivity::class.java)
    }
}