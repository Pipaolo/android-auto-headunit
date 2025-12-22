package info.anodsplace.headunit.ui.settings

import info.anodsplace.headunit.R

class GpsSettingsFragment : BaseSettingsFragment() {

    override val titleResId: Int = R.string.settings_gps

    override val preferencesResId: Int = R.xml.preferences_gps

    companion object {
        fun newInstance() = GpsSettingsFragment()
    }
}
