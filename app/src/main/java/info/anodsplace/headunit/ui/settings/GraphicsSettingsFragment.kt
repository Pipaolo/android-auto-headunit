package info.anodsplace.headunit.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceManager
import info.anodsplace.headunit.R
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.Settings

class GraphicsSettingsFragment : BaseSettingsFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override val titleResId: Int = R.string.settings_graphics

    override val preferencesResId: Int = R.xml.preferences_graphics
    
    private lateinit var settings: Settings

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())
        
        // Initialize the preference with current value from Settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentDpi = settings.manualDpi
        prefs.edit().putString("manual_dpi", currentDpi.toString()).apply()
        AppLog.i { "GraphicsSettings: initialized manual_dpi preference to $currentDpi" }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        // Sync current preference value to Settings on resume
        syncDpiToSettings(prefs)
    }

    override fun onPause() {
        super.onPause()
        // Sync one more time on pause to ensure value is saved
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        syncDpiToSettings(prefs)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun syncDpiToSettings(prefs: SharedPreferences) {
        val dpiString = prefs.getString("manual_dpi", "0") ?: "0"
        val dpi = dpiString.toIntOrNull() ?: 0
        settings.manualDpi = dpi
        AppLog.i { "GraphicsSettings: synced manual_dpi=$dpi to Settings" }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "manual_dpi" && sharedPreferences != null) {
            syncDpiToSettings(sharedPreferences)
        }
    }

    companion object {
        fun newInstance() = GraphicsSettingsFragment()
    }
}
