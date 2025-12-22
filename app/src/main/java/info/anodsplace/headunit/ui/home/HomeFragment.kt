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

        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager

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

    override fun onResume() {
        super.onResume()
        usbReceiver = UsbReceiver(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(usbReceiver, UsbReceiver.createFilter(), Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(usbReceiver, UsbReceiver.createFilter())
        }
    }

    override fun onPause() {
        super.onPause()
        usbReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
        usbReceiver = null
    }

    private fun onUsbConnectClicked() {
        // Check if already connected
        if (App.provide(requireContext()).transport.isAlive) {
            Toast.makeText(requireContext(), "Already connected", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            Toast.makeText(requireContext(), R.string.usb_no_devices_found, Toast.LENGTH_SHORT).show()
            return
        }

        showDeviceSelectionDialog(deviceList.values.toList())
    }

    private fun showDeviceSelectionDialog(devices: List<UsbDevice>) {
        val deviceNames = devices.map { "${it.uniqueName}\n${it.statusText}" }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.usb_select_device_title)
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices[which]
                onDeviceSelected(selectedDevice)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onDeviceSelected(device: UsbDevice) {
        updateUsbStatus(getString(R.string.usb_status_connecting))

        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            pendingDevice = device
            requestUsbPermission(device)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        updateUsbStatus(getString(R.string.usb_status_requesting_permission))

        val intent = Intent(UsbReceiver.ACTION_USB_DEVICE_PERMISSION).apply {
            putExtra(UsbManager.EXTRA_DEVICE, device)
            putExtra(UsbReceiver.EXTRA_CONNECT, true)
            setPackage(requireContext().packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
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
