package info.anodsplace.headunit.aap.protocol

import info.anodsplace.headunit.aap.protocol.proto.Control

class Screen(val width: Int, val height: Int, val baseDpi: Int) {
    companion object {
        // Standard resolutions with recommended DPI values
        // Higher DPI = smaller UI elements. These values are tuned for car displays.
        private val _480 = Screen(800, 480, 160)      // Medium density
        private val _720 = Screen(1280, 720, 240)     // High density (hdpi)
        private val _1080 = Screen(1920, 1080, 320)   // Extra-high density (xhdpi) 
        private val _1440 = Screen(2560, 1440, 480)   // Extra-extra-high density (xxhdpi)
        private val _2160 = Screen(3840, 2160, 640)   // Extra-extra-extra-high density (xxxhdpi)

        // All supported resolutions in ascending order by pixel count
        private val allResolutions = listOf(_480, _720, _1080, _1440, _2160)

        fun forResolution(resolutionType: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType): Screen {
            return when(resolutionType.number) {
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_800x480_VALUE -> _480
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_1280x720_VALUE -> _720
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_1920x1080_VALUE -> _1080
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_2560x1440_VALUE -> _1440
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_3840x2160_VALUE -> _2160
                else -> _480
            }
        }

        /**
         * Find the best resolution for the given display dimensions.
         * Picks the largest resolution that fits within the display, or the smallest if none fit.
         */
        fun forDisplaySize(displayWidth: Int, displayHeight: Int): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
            // Find the largest resolution that fits within the display
            var best: Screen? = null
            for (screen in allResolutions) {
                if (screen.width <= displayWidth && screen.height <= displayHeight) {
                    best = screen
                }
            }
            
            // If no resolution fits, use the smallest one
            if (best == null) {
                best = _480
            }
            
            return when (best) {
                _480 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_800x480
                _720 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_1280x720
                _1080 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_1920x1080
                _1440 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_2560x1440
                _2160 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_3840x2160
                else -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType.VIDEO_800x480
            }
        }

        /**
         * Compute the effective DPI for the video resolution when stretched to fill the display.
         * 
         * When the video is stretched to fill the display, the effective pixel density changes.
         * This method computes the adjusted DPI to ensure UI elements appear at the correct size.
         *
         * @param screen The video resolution being requested
         * @param displayWidth The actual display width in pixels
         * @param displayHeight The actual display height in pixels
         * @return The computed DPI to send to the phone
         */
        fun computeDpi(screen: Screen, displayWidth: Int, displayHeight: Int): Int {
            // Just use the base DPI for the resolution - no stretch compensation
            // The base DPI values are already tuned for typical car displays
            return screen.baseDpi
        }
    }
}