package com.meta.brain.file.recovery.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3 // 3 pages

    override fun createFragment(position: Int): Fragment {
        return OnboardingPageFragment.newInstance(position)
    }
}

