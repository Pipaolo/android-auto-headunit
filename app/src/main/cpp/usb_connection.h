#pragma once

#include "libusb.h"
#include "channel_dispatcher.h"
#include "ring_buffer.h"
#include <thread>
#include <atomic>
#include <mutex>
#include <functional>

namespace aap {

/**
 * Error callback type.
 * Parameters: error code, error message
 */
using ErrorCallback = std::function<void(int errorCode, const char* message)>;

/**
 * USB connection wrapper using libusb for async I/O.
 *
 * Takes a file descriptor from Android's UsbDeviceConnection and wraps
 * it with libusb for high-performance async transfers.
 */
class UsbConnection {
public:
    UsbConnection();
    ~UsbConnection();

    // Non-copyable
    UsbConnection(const UsbConnection&) = delete;
    UsbConnection& operator=(const UsbConnection&) = delete;

    /**
     * Open the USB device using file descriptor from Android.
     * @param fd File descriptor from UsbDeviceConnection.getFileDescriptor()
     * @return true on success, false on failure
     */
    bool open(int fd);

    /**
     * Close the USB connection and release resources.
     */
    void close();

    /**
     * Check if connection is open.
     */
    bool isOpen() const { return deviceHandle_ != nullptr; }

    /**
     * Set the channel dispatcher for routing incoming messages.
     */
    void setDispatcher(ChannelDispatcher* dispatcher);

    /**
     * Set error callback.
     */
    void setErrorCallback(ErrorCallback callback);

    /**
     * Start async reading from USB.
     * Incoming data will be dispatched via the ChannelDispatcher.
     */
    void startReading();

    /**
     * Stop async reading.
     */
    void stopReading();

    /**
     * Write data to USB (synchronous for now, could be made async).
     * @param data Data to write
     * @param length Length of data
     * @return Number of bytes written, or negative on error
     */
    int write(const uint8_t* data, size_t length);

    /**
     * Get the last error message.
     */
    const char* getLastError() const { return lastError_; }

private:
    // libusb context and device
    libusb_context* context_ = nullptr;
    libusb_device_handle* deviceHandle_ = nullptr;

    // Endpoints (discovered during open)
    uint8_t inEndpoint_ = 0;
    uint8_t outEndpoint_ = 0;
    int maxPacketSize_ = 512;

    // Async transfer handling
    static constexpr int NUM_TRANSFERS = 4;  // Number of concurrent transfers
    static constexpr int TRANSFER_SIZE = 16384;  // 16KB per transfer

    struct Transfer {
        libusb_transfer* transfer = nullptr;
        uint8_t* buffer = nullptr;
        UsbConnection* connection = nullptr;
        bool pending = false;
    };
    Transfer transfers_[NUM_TRANSFERS];

    // Event handling thread
    std::thread eventThread_;
    std::atomic<bool> running_{false};

    // Message parsing state
    RingBuffer readBuffer_;
    uint8_t headerBuf_[4];
    size_t headerPos_ = 0;
    std::vector<uint8_t> messageBuf_;
    size_t messagePos_ = 0;
    size_t messageExpected_ = 0;
    bool readingHeader_ = true;

    // Dispatcher and callbacks
    ChannelDispatcher* dispatcher_ = nullptr;
    ErrorCallback errorCallback_;
    std::mutex callbackMutex_;

    // Error state
    char lastError_[256] = {0};

    // Internal methods
    bool findEndpoints();
    void submitTransfer(Transfer& transfer);
    static void LIBUSB_CALL transferCallback(libusb_transfer* transfer);
    void handleTransferComplete(Transfer& transfer, int actualLength);
    void processReceivedData(const uint8_t* data, size_t length);
    void eventLoop();
    void setError(const char* format, ...);
};

} // namespace aap
