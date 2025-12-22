package info.anodsplace.headunit.aap.protocol.messages

import com.google.protobuf.MessageLite
import info.anodsplace.headunit.aap.AapMessage
import info.anodsplace.headunit.aap.KeyCode
import info.anodsplace.headunit.aap.protocol.AudioConfigs
import info.anodsplace.headunit.aap.protocol.Channel
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.aap.protocol.proto.Control
import info.anodsplace.headunit.aap.protocol.proto.Media
import info.anodsplace.headunit.aap.protocol.proto.Sensors
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.Settings
import info.anodsplace.headunit.view.AspectRatioCalculator
import info.anodsplace.headunit.view.Margins

class ServiceDiscoveryResponse(settings: Settings, @Suppress("UNUSED_PARAMETER") densityDpi: Int, displayWidth: Int, displayHeight: Int) : AapMessage(Channel.ID_CTR, Control.ControlMsgType.SERVICEDISCOVERYRESPONSE_VALUE, makeProto(settings, displayWidth, displayHeight)) {

    companion object {
        private fun makeProto(settings: Settings, displayWidth: Int, displayHeight: Int): MessageLite {
            val services = mutableListOf<Control.Service>()

            // Use the user's selected resolution from settings
            val resolution = settings.resolution
            val screen = Screen.forResolution(resolution)
            
            // Calculate letterbox margins and adjusted DPI if aspect ratio preservation is enabled
            val letterboxResult = if (settings.preserveAspectRatio) {
                AspectRatioCalculator.calculate(screen, displayWidth, displayHeight)
            } else {
                AspectRatioCalculator.Result(
                    letterboxMargins = Margins.ZERO,
                    adjustedDpi = screen.baseDpi,
                    effectiveHeight = displayHeight
                )
            }
            
            // Get user-defined margins from settings
            val userMargins = Margins(
                top = settings.marginTop,
                bottom = settings.marginBottom,
                left = settings.marginLeft,
                right = settings.marginRight
            )
            
            // Combine letterbox and user margins
            val combinedMargins = AspectRatioCalculator.combineMargins(
                letterboxResult.letterboxMargins,
                userMargins
            )
            
            // Use manual DPI if set, otherwise use letterbox-adjusted DPI
            val effectiveDpi = if (settings.manualDpi > 0) settings.manualDpi else letterboxResult.adjustedDpi
            
            // Store the computed resolution for ProjectionView to use
            settings.activeResolution = resolution
            
            AppLog.i { "Display: ${displayWidth}x${displayHeight}, resolution: ${screen.width}x${screen.height}, DPI: $effectiveDpi (manual: ${settings.manualDpi}, adjusted: ${letterboxResult.adjustedDpi}), margins: top=${combinedMargins.top}, bottom=${combinedMargins.bottom}" }

            val sensors = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_SEN
                service.sensorSourceService = Control.Service.SensorSourceService.newBuilder().also { sources ->
                    sources.addSensors(makeSensorType(Sensors.SensorType.DRIVING_STATUS))
                    if (settings.useGpsForNavigation) sources.addSensors(makeSensorType(Sensors.SensorType.LOCATION))
                    if (settings.nightMode != Settings.NightMode.NONE) sources.addSensors(makeSensorType(Sensors.SensorType.NIGHT))
                }.build()
            }.build()

            services.add(sensors)

            val video = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_VID
                service.mediaSinkService = Control.Service.MediaSinkService.newBuilder().also {
                    it.availableType = Media.MediaCodecType.VIDEO
                    it.audioType = Media.AudioStreamType.AUDIO_STREAM_NONE
                    it.availableWhileInCall = true
                    it.addVideoConfigs(Control.Service.MediaSinkService.VideoConfiguration.newBuilder().apply {
                        marginHeight = combinedMargins.vertical
                        marginWidth = combinedMargins.horizontal
                        codecResolution = resolution
                        frameRate = Control.Service.MediaSinkService.VideoConfiguration.VideoFrameRateType.FPS_30
                        density = effectiveDpi
                    }.build())
                }.build()
            }.build()

            services.add(video)

            val input = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_INP
                service.inputSourceService = Control.Service.InputSourceService.newBuilder().also {
                    it.touchscreen = Control.Service.InputSourceService.TouchConfig.newBuilder().apply {
                        width = screen.width
                        height = screen.height
                    }.build()
                    it.addAllKeycodesSupported(KeyCode.supported)
                }.build()
            }.build()

            services.add(input)

            val mediaAudio = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_AUD
                service.mediaSinkService = Control.Service.MediaSinkService.newBuilder().also {
                    it.availableType = Media.MediaCodecType.AUDIO
                    it.audioType = Media.AudioStreamType.AUDIO_STREAM_MEDIA
                    it.addAudioConfigs(AudioConfigs[Channel.ID_AUD])
                }.build()
            }.build()
            services.add(mediaAudio)

            val speechAudio = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_AU1
                service.mediaSinkService = Control.Service.MediaSinkService.newBuilder().also {
                    it.availableType = Media.MediaCodecType.AUDIO
                    it.audioType = Media.AudioStreamType.AUDIO_STREAM_SPEECH
                    it.addAudioConfigs(AudioConfigs[Channel.ID_AU1])
                }.build()
            }.build()
            services.add(speechAudio)

            val systemAudio = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_AU2
                service.mediaSinkService = Control.Service.MediaSinkService.newBuilder().also {
                    it.availableType = Media.MediaCodecType.AUDIO
                    it.audioType = Media.AudioStreamType.AUDIO_STREAM_SYSTEM
                    it.addAudioConfigs(AudioConfigs[Channel.ID_AU2])
                }.build()
            }.build()
            services.add(systemAudio)

            val microphone = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_MIC
                service.mediaSourceService = Control.Service.MediaSourceService.newBuilder().also {
                    it.type = Media.MediaCodecType.AUDIO
                    it.audioConfig = Media.AudioConfiguration.newBuilder().apply {
                        sampleRate = settings.micSampleRate
                        numberOfBits = 16
                        numberOfChannels = 1
                    }.build()
                }.build()
            }.build()
            services.add(microphone)
            AppLog.i { "Microphone configured: ${settings.micSampleRate}Hz, 16-bit, mono" }

            if (settings.bluetoothAddress.isNotEmpty()) {
                val bluetooth = Control.Service.newBuilder().also { service ->
                    service.id = Channel.ID_BTH
                    service.bluetoothService = Control.Service.BluetoothService.newBuilder().also {
                        it.carAddress = settings.bluetoothAddress
                        it.addAllSupportedPairingMethods(
                                listOf(Control.BluetoothPairingMethod.A2DP,
                                        Control.BluetoothPairingMethod.HFP)
                        )
                    }.build()
                }.build()
                services.add(bluetooth)
            } else {
                AppLog.i { "BT MAC Address is empty. Skip bluetooth service" }
            }

            val mediaPlaybackStatus = Control.Service.newBuilder().also { service ->
                service.id = Channel.ID_MPB
                service.mediaPlaybackService = Control.Service.MediaPlaybackStatusService.newBuilder().build()
            }.build()
            services.add(mediaPlaybackStatus)

            return Control.ServiceDiscoveryResponse.newBuilder().apply {
                make = "Android Auto HeadUnit"
                model = "Harry Phillips"
                year = "2019"
                vehicleId = "Harry Phillips"
                headUnitModel = "Harry Phillips"
                headUnitMake = "Android Auto HeadUnit"
                headUnitSoftwareBuild = "1.0"
                headUnitSoftwareVersion = "1.0"
                driverPosition = settings.driverPosition
                canPlayNativeMediaDuringVr = false
                hideProjectedClock = false
                addAllServices(services)
            }.build()
        }

        private fun makeSensorType(type: Sensors.SensorType): Control.Service.SensorSourceService.Sensor {
            return Control.Service.SensorSourceService.Sensor.newBuilder().setType(type).build()
        }
    }
}
