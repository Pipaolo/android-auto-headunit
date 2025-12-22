package info.anodsplace.headunit.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.R
import info.anodsplace.headunit.ui.settings.SettingsActivity

class HomeFragment : Fragment() {

    private var usbStatusText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        usbStatusText = view.findViewById(R.id.tile_usb_status)

        // USB Connect tile
        view.findViewById<View>(R.id.tile_usb).setOnClickListener {
            onUsbConnectClicked()
        }

        // Settings tile
        view.findViewById<View>(R.id.tile_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // Exit tile
        view.findViewById<View>(R.id.tile_exit).setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun onUsbConnectClicked() {
        // TODO: Trigger USB connection via existing AapService
        // For now, just update status text
        updateUsbStatus(getString(R.string.usb_status_connecting))
    }

    fun updateUsbStatus(status: String) {
        usbStatusText?.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usbStatusText = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
