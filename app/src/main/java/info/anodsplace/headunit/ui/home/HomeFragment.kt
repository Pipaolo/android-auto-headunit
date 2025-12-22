package info.anodsplace.headunit.ui.home

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.App
import info.anodsplace.headunit.R
import info.anodsplace.headunit.aap.AapService
import info.anodsplace.headunit.connection.UsbAccessoryMode
import info.anodsplace.headunit.connection.UsbReceiver
import info.anodsplace.headunit.connection.isInAccessoryMode
import info.anodsplace.headunit.connection.statusText
import info.anodsplace.headunit.connection.uniqueName
import info.anodsplace.headunit.ui.settings.SettingsActivity

class HomeFragment : Fragment(), UsbReceiver.Listener {

    private var usbStatusText: TextView? = null
    private lateinit var usbManager: UsbManager
    private var usbReceiver: UsbReceiver? = null
    private var pendingDevice: UsbDevice? = null

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

    // UsbReceiver.Listener implementation
    override fun onUsbDetach(device: UsbDevice) {
        // Device disconnected - update UI if needed
        pendingDevice = null
    }

    override fun onUsbAttach(device: UsbDevice) {
        // Device attached - could refresh list if dialog is open
    }

    override fun onUsbPermission(granted: Boolean, connect: Boolean, device: UsbDevice) {
        if (granted && connect) {
            connectToDevice(device)
        } else {
            updateUsbStatus(getString(R.string.usb_status_ready))
            pendingDevice = null
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        // Will be implemented in next task
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
