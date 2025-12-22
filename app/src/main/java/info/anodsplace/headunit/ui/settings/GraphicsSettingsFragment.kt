package info.anodsplace.headunit.ui.settings

import info.anodsplace.headunit.R

class GraphicsSettingsFragment : BaseSettingsFragment() {

    override val titleResId: Int = R.string.settings_graphics

    override val preferencesResId: Int = R.xml.preferences_graphics

    companion object {
        fun newInstance() = GraphicsSettingsFragment()
    }
}
