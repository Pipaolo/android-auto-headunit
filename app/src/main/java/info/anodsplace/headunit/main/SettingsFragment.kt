package info.anodsplace.headunit.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import info.anodsplace.headunit.R
import info.anodsplace.headunit.aap.protocol.proto.Control
import info.anodsplace.headunit.databinding.FragmentSettingsBinding
import info.anodsplace.headunit.decoder.MicRecorder
import info.anodsplace.headunit.utils.Settings

/**
 * @author algavris
 * @date 13/06/2017
 */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    lateinit var settings: Settings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.keymapButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_content, KeymapFragment())
                .commit()
        }

        settings = Settings(requireContext())

        binding.gpsNavigationButton.text = getString(R.string.gps_for_navigation, if (settings.useGpsForNavigation) getString(R.string.enabled) else getString(R.string.disabled) )
        binding.gpsNavigationButton.tag = settings.useGpsForNavigation
        binding.gpsNavigationButton.setOnClickListener {
            val newValue = it.tag != true
            it.tag = newValue
            settings.useGpsForNavigation = newValue
            (it as Button).text = getString(R.string.gps_for_navigation, if (newValue) getString(R.string.enabled) else getString(R.string.disabled) )
        }

        val sampleRate = settings.micSampleRate
        binding.micSampleRateButton.text = getString(R.string.mic_sample_rate, sampleRate/1000)
        binding.micSampleRateButton.tag = sampleRate
        binding.micSampleRateButton.setOnClickListener {
            val newValue = Settings.MicSampleRates[it.tag]!!

            val recorder: MicRecorder? = try { MicRecorder(newValue, requireContext().applicationContext) } catch (e: Exception) { null }

            if (recorder == null) {
                Toast.makeText(activity, "Value not supported: $newValue", Toast.LENGTH_LONG).show()
            } else {
                settings.micSampleRate = newValue
                (it as Button).text = getString(R.string.mic_sample_rate, newValue / 1000)
                it.tag = newValue
            }
        }


        val nightMode = settings.nightMode
        val nightModeTitles = resources.getStringArray(R.array.night_mode)
        binding.nightModeButton.text = getString(R.string.night_mode, nightModeTitles[nightMode.value])
        binding.nightModeButton.tag = nightMode.value
        binding.nightModeButton.setOnClickListener {
            val newValue = Settings.NightModes[it.tag]!!
            val newMode = Settings.NightMode.fromInt(newValue)!!
            (it as Button).text = getString(R.string.night_mode, nightModeTitles[newMode.value])
            it.tag = newValue
            settings.nightMode = newMode
        }

        binding.btAddressButton.text = getString(R.string.bluetooth_address_s, settings.bluetoothAddress)
        binding.btAddressButton.setOnClickListener {
            val editView = EditText(activity)
            editView.setText(settings.bluetoothAddress)
            AlertDialog.Builder(activity)
                .setTitle(R.string.enter_bluetooth_mac)
                .setView(editView)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    settings.bluetoothAddress = editView.text.toString().trim()
                    dialog.dismiss()
                }.show()
        }

        binding.resolution.text = getString(R.string.resolution, settings.resolution)
        val items = arrayOf(
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480.name,
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720.name,
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080.name
        )
        val values = arrayOf(
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480,
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720,
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
        )

        binding.resolution.setOnClickListener {
            AlertDialog.Builder(activity)
                    .setTitle(R.string.change_resolution)
                    .setSingleChoiceItems(items, items.indexOf(settings.resolution.name)) { dialog, which ->
                        settings.resolution = values[which]
                        binding.resolution.text = getString(R.string.resolution, settings.resolution)
                        dialog.dismiss()
                    }
                    .show()
        }
    }
}
