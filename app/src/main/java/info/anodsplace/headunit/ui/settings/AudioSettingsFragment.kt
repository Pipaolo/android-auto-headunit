package info.anodsplace.headunit.ui.settings

import info.anodsplace.headunit.R

class AudioSettingsFragment : BaseSettingsFragment() {

    override val titleResId: Int = R.string.settings_audio

    override val preferencesResId: Int = R.xml.preferences_audio

    companion object {
        fun newInstance() = AudioSettingsFragment()
    }
}
