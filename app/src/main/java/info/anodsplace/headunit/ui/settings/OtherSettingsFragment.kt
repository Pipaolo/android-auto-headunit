package info.anodsplace.headunit.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceManager
import info.anodsplace.headunit.R
import info.anodsplace.headunit.utils.Settings

class OtherSettingsFragment : BaseSettingsFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override val titleResId: Int = R.string.settings_other

    override val preferencesResId: Int = R.xml.preferences_other

    private lateinit var settings: Settings

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())

        // Initialize preferences with current values from Settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putBoolean("driver_position", settings.driverPosition).apply()
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener(this)
        syncAllToSettings(prefs)
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        syncAllToSettings(prefs)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun syncAllToSettings(prefs: SharedPreferences) {
        settings.driverPosition = prefs.getBoolean("driver_position", true)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return

        when (key) {
            "driver_position" -> {
                settings.driverPosition = sharedPreferences.getBoolean(key, true)
            }
        }
    }

    companion object {
        fun newInstance() = OtherSettingsFragment()
    }
}
