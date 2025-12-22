package info.anodsplace.headunit.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import info.anodsplace.headunit.R
import info.anodsplace.headunit.main.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var dots: Array<ImageView>

    private val pages = listOf(
        OnboardingPage(
            R.string.onboarding_location_title,
            R.string.onboarding_location_desc,
            R.drawable.ic_location_large,
            Manifest.permission.ACCESS_FINE_LOCATION
        ),
        OnboardingPage(
            R.string.onboarding_mic_title,
            R.string.onboarding_mic_desc,
            R.drawable.ic_mic_large,
            Manifest.permission.RECORD_AUDIO
        ),
        OnboardingPage(
            R.string.onboarding_phone_title,
            R.string.onboarding_phone_desc,
            R.drawable.ic_phone_large,
            Manifest.permission.READ_PHONE_STATE
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        dotsContainer = findViewById(R.id.dotsContainer)
        nextButton = findViewById(R.id.nextButton)

        setupViewPager()
        setupDots()
        setupNextButton()
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingPagerAdapter(this, pages)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateNextButton(position)
            }
        })
    }

    private fun setupDots() {
        dots = Array(pages.size) { ImageView(this) }
        val params = LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_size),
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        )
        params.setMargins(
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_spacing),
            0,
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_spacing),
            0
        )

        dots.forEachIndexed { index, dot ->
            dot.setImageResource(R.drawable.dot_indicator)
            dot.layoutParams = params
            dot.alpha = if (index == 0) 1f else 0.5f
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.alpha = if (index == position) 1f else 0.5f
        }
    }

    private fun updateNextButton(position: Int) {
        if (position == pages.size - 1) {
            nextButton.text = getString(R.string.finish)
        } else {
            nextButton.text = getString(R.string.next)
        }
    }

    private fun setupNextButton() {
        nextButton.setOnClickListener {
            val currentPosition = viewPager.currentItem
            val page = pages[currentPosition]

            // Request permission
            if (ContextCompat.checkSelfPermission(this, page.permission)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(page.permission),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                advanceOrFinish()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Advance regardless of grant result
            advanceOrFinish()
        }
    }

    private fun advanceOrFinish() {
        val currentPosition = viewPager.currentItem
        if (currentPosition < pages.size - 1) {
            viewPager.currentItem = currentPosition + 1
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // Save that onboarding is complete
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()

        // Navigate to main
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val PREFS_NAME = "headunit_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val PERMISSION_REQUEST_CODE = 100

        fun isOnboardingComplete(activity: AppCompatActivity): Boolean {
            return activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
    }
}

data class OnboardingPage(
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val permission: String
)
