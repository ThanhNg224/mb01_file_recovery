package com.meta.brain.file.recovery.ui.intro

import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.IntroActivityBinding
import com.meta.brain.module.base.DataBindActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IntroActivity : DataBindActivity<IntroActivityBinding>(R.layout.intro_activity) {

    override fun initView() {
        // Activity chỉ host NavHostFragment. Không xử lý UI/Ads/Navigation ở đây.
        // Mọi logic: IntroFragment + IntroViewModel.
    }
}
