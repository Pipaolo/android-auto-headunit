package info.anodsplace.headunit.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import info.anodsplace.headunit.R
import info.anodsplace.headunit.ui.home.HomeFragment
import info.anodsplace.headunit.ui.onboarding.OnboardingActivity
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.hideSystemUI

class MainActivity : FragmentActivity() {
    var keyListener: KeyListener? = null

    interface KeyListener {
        fun onKeyEvent(event: KeyEvent): Boolean
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if onboarding has been completed
        if (!isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Load HomeFragment
        if (savedInstanceState == null) {
            loadHomeFragment()
        }
    }

    private fun isOnboardingComplete(): Boolean {
        val prefs = getSharedPreferences(OnboardingActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETE, false)
    }

    private fun loadHomeFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, HomeFragment.newInstance())
            .commit()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.hideSystemUI()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i { "onKeyDown: $keyCode "}

        return keyListener?.onKeyEvent(event) ?: super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i { "onKeyUp: $keyCode" }

        return keyListener?.onKeyEvent(event) ?: super.onKeyUp(keyCode, event)
    }
}
