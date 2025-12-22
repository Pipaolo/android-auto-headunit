package info.anodsplace.headunit.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.R

class OnboardingPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleRes = arguments?.getInt(ARG_TITLE_RES) ?: return
        val descriptionRes = arguments?.getInt(ARG_DESCRIPTION_RES) ?: return
        val iconRes = arguments?.getInt(ARG_ICON_RES) ?: return

        view.findViewById<TextView>(R.id.titleText).setText(titleRes)
        view.findViewById<TextView>(R.id.descriptionText).setText(descriptionRes)
        view.findViewById<ImageView>(R.id.iconImage).setImageResource(iconRes)
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_DESCRIPTION_RES = "description_res"
        private const val ARG_ICON_RES = "icon_res"

        fun newInstance(titleRes: Int, descriptionRes: Int, iconRes: Int): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleRes)
                    putInt(ARG_DESCRIPTION_RES, descriptionRes)
                    putInt(ARG_ICON_RES, iconRes)
                }
            }
        }
    }
}
