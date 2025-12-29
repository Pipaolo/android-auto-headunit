#pragma once

#include <cstdint>
#include <cstddef>

namespace aap {

// AAP Channel IDs (from Channel.kt)
namespace Channel {
    constexpr int ID_CTR = 0;   // Control
    constexpr int ID_SEN = 1;   // Sensor
    constexpr int ID_VID = 2;   // Video
    constexpr int ID_INP = 3;   // Input (touch/keys)
    constexpr int ID_AU1 = 4;   // Audio 1
    constexpr int ID_AU2 = 5;   // Audio 2
    constexpr int ID_AUD = 6;   // Audio main
    constexpr int ID_MIC = 7;   // Microphone
    constexpr int ID_BTH = 8;   // Bluetooth
    constexpr int ID_MPB = 9;   // Music playback metadata
    constexpr int ID_NAV = 10;  // Navigation directions
    constexpr int ID_NOTI = 11; // Notifications
    constexpr int ID_PHONE = 12; // Phone status

    inline bool isAudio(int channel) {
        return channel == ID_AUD || channel == ID_AU1 || channel == ID_AU2;
    }

    inline bool isVideo(int channel) {
        return channel == ID_VID;
    }

    inline bool isInput(int channel) {
        return channel == ID_INP;
    }

    inline const char* name(int channel) {
        switch (channel) {
            case ID_CTR: return "CONTROL";
            case ID_SEN: return "SENSOR";
            case ID_VID: return "VIDEO";
            case ID_INP: return "INPUT";
            case ID_AU1: return "AUDIO1";
            case ID_AU2: return "AUDIO2";
            case ID_AUD: return "AUDIO";
            case ID_MIC: return "MIC";
            case ID_BTH: return "BLUETOOTH";
            case ID_MPB: return "MUSIC_PLAYBACK";
            case ID_NAV: return "NAVIGATION";
            case ID_NOTI: return "NOTIFICATION";
            case ID_PHONE: return "PHONE";
            default: return "UNKNOWN";
        }
    }
}

// Channel priority for dispatching
enum class ChannelPriority {
    HIGH,    // Audio - real-time priority
    MEDIUM,  // Video - important but can tolerate some delay
    NORMAL   // Control, Input, etc.
};

inline ChannelPriority getChannelPriority(int channel) {
    if (Channel::isAudio(channel)) {
        return ChannelPriority::HIGH;
    } else if (Channel::isVideo(channel)) {
        return ChannelPriority::MEDIUM;
    }
    return ChannelPriority::NORMAL;
}

// Encrypted message header (4 bytes)
struct EncryptedHeader {
    uint8_t channel;
    uint8_t flags;
    uint16_t encLength;  // Big-endian in wire format

    static constexpr size_t SIZE = 4;

    void decode(const uint8_t* buf) {
        channel = buf[0];
        flags = buf[1];
        // Big-endian to host
        encLength = (static_cast<uint16_t>(buf[2]) << 8) | buf[3];
    }

    bool isEncrypted() const {
        return (flags & 0x08) == 0x08;
    }
};

// Message data passed to Kotlin
struct Message {
    int channel;
    uint8_t flags;
    const uint8_t* data;
    size_t length;
};

} // namespace aap
