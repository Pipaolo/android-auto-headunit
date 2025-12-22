package info.anodsplace.headunit.ui.settings

import info.anodsplace.headunit.R

class OtherSettingsFragment : BaseSettingsFragment() {

    override val titleResId: Int = R.string.settings_other

    override val preferencesResId: Int = R.xml.preferences_other

    companion object {
        fun newInstance() = OtherSettingsFragment()
    }
}
