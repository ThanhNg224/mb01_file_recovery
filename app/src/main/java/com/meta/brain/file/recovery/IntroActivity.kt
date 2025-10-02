package com.meta.brain.file.recovery.ui.intro

import android.util.Log
import androidx.activity.viewModels
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.IntroActivityBinding
import com.meta.brain.module.ads.AdEvent
import com.meta.brain.module.ads.AdsController
import com.meta.brain.module.base.DataBindActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IntroActivity : DataBindActivity<IntroActivityBinding>(R.layout.intro_activity) {

    private val viewModel: IntroViewModel by viewModels()

    override fun initView() {
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener {
            viewModel.onStartButtonClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.showAdEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                showInterstitialAd()
            }
        }

        viewModel.startTouchCount.observe(this) { count ->
            Log.d("IntroActivity", "Start button touched $count time(s)")
        }

        viewModel.navigateToHome.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                startHome()
            }
        }
    }

    private fun showInterstitialAd() {
        AdsController.showInter(this, object : AdEvent() {
            override fun onComplete() {
                viewModel.onAdCompleted()
            }
        })
    }

    private fun startHome() {
        // TODO: Navigate to HomeActivity when it's created
        // startActivity(Intent(this, HomeActivity::class.java))
        // finish()
    }
}
