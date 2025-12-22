package info.anodsplace.headunit.ui.onboarding

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    activity: AppCompatActivity,
    private val pages: List<OnboardingPage>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        return OnboardingPageFragment.newInstance(
            page.titleRes,
            page.descriptionRes,
            page.iconRes
        )
    }
}
