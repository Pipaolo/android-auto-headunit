package info.anodsplace.headunit.ui.settings

import info.anodsplace.headunit.R

class InputSettingsFragment : BaseSettingsFragment() {

    override val titleResId: Int = R.string.settings_input

    override val preferencesResId: Int = R.xml.preferences_input

    companion object {
        fun newInstance() = InputSettingsFragment()
    }
}
