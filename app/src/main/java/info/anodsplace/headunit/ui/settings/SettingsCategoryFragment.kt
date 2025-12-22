package info.anodsplace.headunit.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.R

class SettingsCategoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.tile_graphics).setOnClickListener {
            navigateToDetail(GraphicsSettingsFragment.newInstance(), "graphics")
        }

        view.findViewById<View>(R.id.tile_audio).setOnClickListener {
            navigateToDetail(AudioSettingsFragment.newInstance(), "audio")
        }

        view.findViewById<View>(R.id.tile_gps).setOnClickListener {
            navigateToDetail(GpsSettingsFragment.newInstance(), "gps")
        }

        view.findViewById<View>(R.id.tile_input).setOnClickListener {
            navigateToDetail(InputSettingsFragment.newInstance(), "input")
        }

        view.findViewById<View>(R.id.tile_other).setOnClickListener {
            navigateToDetail(OtherSettingsFragment.newInstance(), "other")
        }

        view.findViewById<View>(R.id.tile_back).setOnClickListener {
            (activity as? SettingsActivity)?.navigateBack()
        }
    }

    private fun navigateToDetail(fragment: Fragment, tag: String) {
        (activity as? SettingsActivity)?.showDetailFragment(fragment, tag)
    }

    companion object {
        fun newInstance() = SettingsCategoryFragment()
    }
}
