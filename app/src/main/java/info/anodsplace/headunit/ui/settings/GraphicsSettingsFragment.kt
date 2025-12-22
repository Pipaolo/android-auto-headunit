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

        // Initialize preferences with current values from Settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // DPI
        val currentDpi = settings.manualDpi
        prefs.edit().putString("manual_dpi", currentDpi.toString()).apply()

        // Render surface
        prefs.edit().putString("render_surface", settings.renderSurface.value.toString()).apply()

        // Preserve aspect ratio
        prefs.edit().putBoolean("preserve_aspect_ratio", settings.preserveAspectRatio).apply()

        // Margins
        prefs.edit()
            .putInt("margin_top", settings.marginTop)
            .putInt("margin_bottom", settings.marginBottom)
            .putInt("margin_left", settings.marginLeft)
            .putInt("margin_right", settings.marginRight)
            .apply()

        AppLog.i { "GraphicsSettings: initialized preferences" }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener(this)

        // Sync current preference values to Settings on resume
        syncAllToSettings(prefs)
    }

    override fun onPause() {
        super.onPause()
        // Sync one more time on pause to ensure values are saved
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        syncAllToSettings(prefs)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun syncAllToSettings(prefs: SharedPreferences) {
        // DPI
        val dpiString = prefs.getString("manual_dpi", "0") ?: "0"
        settings.manualDpi = dpiString.toIntOrNull() ?: 0

        // Render surface
        val renderSurfaceString = prefs.getString("render_surface", "0") ?: "0"
        settings.renderSurface = Settings.RenderSurface.fromInt(renderSurfaceString.toIntOrNull() ?: 0)

        // Preserve aspect ratio
        settings.preserveAspectRatio = prefs.getBoolean("preserve_aspect_ratio", true)

        // Margins
        settings.marginTop = prefs.getInt("margin_top", 0)
        settings.marginBottom = prefs.getInt("margin_bottom", 0)
        settings.marginLeft = prefs.getInt("margin_left", 0)
        settings.marginRight = prefs.getInt("margin_right", 0)

        AppLog.i { "GraphicsSettings: synced all preferences to Settings" }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return

        when (key) {
            "manual_dpi" -> {
                val dpiString = sharedPreferences.getString(key, "0") ?: "0"
                settings.manualDpi = dpiString.toIntOrNull() ?: 0
            }
            "render_surface" -> {
                val value = sharedPreferences.getString(key, "0") ?: "0"
                settings.renderSurface = Settings.RenderSurface.fromInt(value.toIntOrNull() ?: 0)
            }
            "preserve_aspect_ratio" -> {
                settings.preserveAspectRatio = sharedPreferences.getBoolean(key, true)
            }
            "margin_top" -> {
                settings.marginTop = sharedPreferences.getInt(key, 0)
            }
            "margin_bottom" -> {
                settings.marginBottom = sharedPreferences.getInt(key, 0)
            }
            "margin_left" -> {
                settings.marginLeft = sharedPreferences.getInt(key, 0)
            }
            "margin_right" -> {
                settings.marginRight = sharedPreferences.getInt(key, 0)
            }
        }
    }

    companion object {
        fun newInstance() = GraphicsSettingsFragment()
    }
}