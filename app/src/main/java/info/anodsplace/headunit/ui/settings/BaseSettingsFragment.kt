package info.anodsplace.headunit.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import info.anodsplace.headunit.R

abstract class BaseSettingsFragment : Fragment() {

    @get:StringRes
    protected abstract val titleResId: Int

    @get:XmlRes
    protected abstract val preferencesResId: Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up header
        view.findViewById<TextView>(R.id.settings_title).setText(titleResId)
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            (activity as? SettingsActivity)?.navigateBack()
        }

        // Load preferences fragment into container
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.preferences_container, PreferencesFragment.newInstance(preferencesResId))
                .commit()
        }
    }

    class PreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(arguments?.getInt(ARG_PREFERENCES_RES) ?: 0, rootKey)
        }

        companion object {
            private const val ARG_PREFERENCES_RES = "preferences_res"

            fun newInstance(@XmlRes preferencesResId: Int): PreferencesFragment {
                return PreferencesFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_PREFERENCES_RES, preferencesResId)
                    }
                }
            }
        }
    }
}
