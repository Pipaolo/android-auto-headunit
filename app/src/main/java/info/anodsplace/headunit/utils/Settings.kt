package info.anodsplace.headunit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import info.anodsplace.headunit.aap.protocol.proto.Control

import java.util.HashSet

class Settings(context: Context) { // TODO more settings

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var networkAddresses: Set<String>
        get() = prefs.getStringSet("network-addresses", HashSet<String>())!!
        set(addrs) {
            prefs.edit().putStringSet("network-addresses", addrs).apply()
        }

    var bluetoothAddress: String
        get() = prefs.getString("bt-address", "00:12:3D:00:5E:0B")!!
        set(value) = prefs.edit().putString("bt-address", value).apply()

    var lastKnownLocation: Location
        get() {
            val latitude = prefs.getFloat("last-loc-latitude", 32.0864169f).toDouble()
            val longitude = prefs.getFloat("last-loc-longitude", 34.7557871f).toDouble()

            val location = Location("")
            location.latitude = latitude
            location.longitude = longitude
            return location
        }
        set(location) {
            prefs.edit()
                .putFloat("last-loc-latitude", location.latitude.toFloat())
                .putFloat("last-loc-longitude", location.longitude.toFloat())
                .apply()
        }

    var resolution: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType
        get() {
            val number = prefs.getInt("resolution", Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_800x480_VALUE)
            return Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.forNumber(number)
        }
        set(value) { prefs.edit().putInt("resolution", value.number).apply() }

    // The active resolution computed based on display dimensions (set at connection time)
    var activeResolution: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType
        get() {
            val number = prefs.getInt("active-resolution", Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_1920x1080_VALUE)
            return Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.forNumber(number)
        }
        set(value) { prefs.edit().putInt("active-resolution", value.number).apply() }

    // Manual DPI override (0 = auto-compute based on screen stretch)
    var manualDpi: Int
        get() = prefs.getInt("manual-dpi", 0)
        set(value) { prefs.edit().putInt("manual-dpi", value).apply() }

    var micSampleRate: Int
        get() = prefs.getInt("mic-sample-rate", 16000)
        set(sampleRate) {
            prefs.edit().putInt("mic-sample-rate", sampleRate).apply()
        }

    var useGpsForNavigation: Boolean
        get() = prefs.getBoolean("gps-navigation", true)
        set(value) {
            prefs.edit().putBoolean("gps-navigation", value).apply()
        }

    var nightMode: NightMode
        get() {
            val value = prefs.getInt("night-mode", 0)
            val mode = NightMode.fromInt(value)
            return mode!!
        }
        set(nightMode) {
            prefs.edit().putInt("night-mode", nightMode.value).apply()
        }

    var keyCodes: MutableMap<Int, Int>
        get() {
            val set = prefs.getStringSet("key-codes", mutableSetOf())!!
            val map = mutableMapOf<Int, Int>()
            set.forEach {
                val codes = it.split("-")
                map[codes[0].toInt()] = codes[1].toInt()
            }
            return map
        }
        set(codesMap) {
            val list: List<String> = codesMap.map { "${it.key}-${it.value}" }
            prefs.edit().putStringSet("key-codes", list.toSet()).apply()
        }

    // Render surface type (GLES TextureView or SurfaceView)
    var renderSurface: RenderSurface
        get() {
            val value = prefs.getInt("render-surface", RenderSurface.GLES_TEXTURE_VIEW.value)
            return RenderSurface.fromInt(value)
        }
        set(value) { prefs.edit().putInt("render-surface", value.value).apply() }

    // Whether to preserve aspect ratio with letterboxing
    var preserveAspectRatio: Boolean
        get() = prefs.getBoolean("preserve-aspect-ratio", true)
        set(value) { prefs.edit().putBoolean("preserve-aspect-ratio", value).apply() }

    // User-defined margins in pixels
    var marginTop: Int
        get() = prefs.getInt("margin-top", 0)
        set(value) { prefs.edit().putInt("margin-top", value).apply() }

    var marginBottom: Int
        get() = prefs.getInt("margin-bottom", 0)
        set(value) { prefs.edit().putInt("margin-bottom", value).apply() }

    var marginLeft: Int
        get() = prefs.getInt("margin-left", 0)
        set(value) { prefs.edit().putInt("margin-left", value).apply() }

    var marginRight: Int
        get() = prefs.getInt("margin-right", 0)
        set(value) { prefs.edit().putInt("margin-right", value).apply() }

    // Driver position: true = right-hand drive (driver on right), false = left-hand drive (driver on left)
    var driverPosition: Boolean
        get() = prefs.getBoolean("driver-position", false)
        set(value) { prefs.edit().putBoolean("driver-position", value).apply() }

    @SuppressLint("ApplySharedPref")
    fun commit() {
        prefs.edit().commit()
    }

    enum class NightMode(val value: Int) {
        AUTO(0),
        DAY(1),
        NIGHT(2),
        AUTO_WAIT_GPS(3),
        NONE(4);

        companion object {
            private val map = NightMode.values().associateBy(NightMode::value)
            fun fromInt(value: Int) = map[value]
        }
    }

    /**
     * Render surface type for video projection.
     * GLES_TEXTURE_VIEW uses GPU-backed TextureView with GLES 2.0 transforms.
     * SURFACE_VIEW uses the traditional SurfaceView for compatibility.
     */
    enum class RenderSurface(val value: Int) {
        GLES_TEXTURE_VIEW(0),  // Default - better performance with GPU transforms
        SURFACE_VIEW(1);       // Fallback for compatibility

        companion object {
            private val map = RenderSurface.values().associateBy(RenderSurface::value)
            fun fromInt(value: Int) = map[value] ?: GLES_TEXTURE_VIEW
        }
    }

    companion object {
        val MicSampleRates = hashMapOf(
            8000 to 16000,
            16000 to 8000
        )

        val NightModes = hashMapOf(
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 4,
            4 to 0
        )
    }

}
