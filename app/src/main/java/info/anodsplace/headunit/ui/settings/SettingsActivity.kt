package info.anodsplace.headunit.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            showCategoryFragment()
        }
    }

    private fun showCategoryFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsCategoryFragment.newInstance())
            .commit()
    }

    fun showDetailFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment, tag)
            .addToBackStack(tag)
            .commit()
    }

    fun navigateBack() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        navigateBack()
    }
}
