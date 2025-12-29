#include "usb_connection.h"
#include <android/log.h>
#include <cstdarg>
#include <cstring>

#define LOG_TAG "UsbConnection"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace aap {

// Buffer size for ring buffer (500KB - about 100ms of audio)
constexpr size_t READ_BUFFER_SIZE = 512 * 1024;

// USB timeouts
constexpr int WRITE_TIMEOUT_MS = 1000;

UsbConnection::UsbConnection()
    : readBuffer_(READ_BUFFER_SIZE)
{}

UsbConnection::~UsbConnection() {
    close();
}

bool UsbConnection::open(int fd) {
    if (deviceHandle_) {
        setError("Already open");
        return false;
    }

    LOGI("Opening USB connection with fd=%d", fd);

    // Initialize libusb
    int rc = libusb_init(&context_);
    if (rc != LIBUSB_SUCCESS) {
        setError("libusb_init failed: %s", libusb_error_name(rc));
        return false;
    }

    // Set debug level
    libusb_set_option(context_, LIBUSB_OPTION_LOG_LEVEL, LIBUSB_LOG_LEVEL_WARNING);

    // Wrap the Android file descriptor
    rc = libusb_wrap_sys_device(context_, (intptr_t)fd, &deviceHandle_);
    if (rc != LIBUSB_SUCCESS) {
        setError("libusb_wrap_sys_device failed: %s", libusb_error_name(rc));
        libusb_exit(context_);
        context_ = nullptr;
        return false;
    }

    LOGI("Device wrapped successfully");

    // Find endpoints
    if (!findEndpoints()) {
        libusb_close(deviceHandle_);
        deviceHandle_ = nullptr;
        libusb_exit(context_);
        context_ = nullptr;
        return false;
    }

    // Claim interface
    rc = libusb_claim_interface(deviceHandle_, 0);
    if (rc != LIBUSB_SUCCESS) {
        setError("libusb_claim_interface failed: %s", libusb_error_name(rc));
        libusb_close(deviceHandle_);
        deviceHandle_ = nullptr;
        libusb_exit(context_);
        context_ = nullptr;
        return false;
    }

    LOGI("USB connection opened successfully");
    LOGI("  IN endpoint: 0x%02x, OUT endpoint: 0x%02x", inEndpoint_, outEndpoint_);
    LOGI("  Max packet size: %d", maxPacketSize_);

    return true;
}

void UsbConnection::close() {
    stopReading();

    if (deviceHandle_) {
        LOGI("Closing USB connection");
        libusb_release_interface(deviceHandle_, 0);
        libusb_close(deviceHandle_);
        deviceHandle_ = nullptr;
    }

    if (context_) {
        libusb_exit(context_);
        context_ = nullptr;
    }
}

bool UsbConnection::findEndpoints() {
    libusb_device* device = libusb_get_device(deviceHandle_);
    if (!device) {
        setError("Could not get device from handle");
        return false;
    }

    struct libusb_config_descriptor* config;
    int rc = libusb_get_active_config_descriptor(device, &config);
    if (rc != LIBUSB_SUCCESS) {
        setError("libusb_get_active_config_descriptor failed: %s", libusb_error_name(rc));
        return false;
    }

    bool foundIn = false, foundOut = false;

    for (int i = 0; i < config->bNumInterfaces && (!foundIn || !foundOut); i++) {
        const libusb_interface& interface = config->interface[i];
        for (int j = 0; j < interface.num_altsetting && (!foundIn || !foundOut); j++) {
            const libusb_interface_descriptor& altsetting = interface.altsetting[j];
            for (int k = 0; k < altsetting.bNumEndpoints; k++) {
                const libusb_endpoint_descriptor& endpoint = altsetting.endpoint[k];

                if ((endpoint.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK) == LIBUSB_TRANSFER_TYPE_BULK) {
                    if ((endpoint.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN) {
                        if (!foundIn) {
                            inEndpoint_ = endpoint.bEndpointAddress;
                            maxPacketSize_ = endpoint.wMaxPacketSize;
                            foundIn = true;
                            LOGD("Found IN endpoint: 0x%02x", inEndpoint_);
                        }
                    } else {
                        if (!foundOut) {
                            outEndpoint_ = endpoint.bEndpointAddress;
                            foundOut = true;
                            LOGD("Found OUT endpoint: 0x%02x", outEndpoint_);
                        }
                    }
                }
            }
        }
    }

    libusb_free_config_descriptor(config);

    if (!foundIn) {
        setError("Could not find bulk IN endpoint");
        return false;
    }
    if (!foundOut) {
        setError("Could not find bulk OUT endpoint");
        return false;
    }

    return true;
}

void UsbConnection::setDispatcher(ChannelDispatcher* dispatcher) {
    std::lock_guard<std::mutex> lock(callbackMutex_);
    dispatcher_ = dispatcher;
}

void UsbConnection::setErrorCallback(ErrorCallback callback) {
    std::lock_guard<std::mutex> lock(callbackMutex_);
    errorCallback_ = std::move(callback);
}

void UsbConnection::startReading() {
    if (running_.exchange(true)) {
        return; // Already running
    }

    if (!deviceHandle_) {
        setError("Device not open");
        running_ = false;
        return;
    }

    LOGI("Starting async USB reading");

    // Allocate and submit transfers
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        Transfer& t = transfers_[i];
        t.transfer = libusb_alloc_transfer(0);
        if (!t.transfer) {
            setError("Failed to allocate transfer %d", i);
            running_ = false;
            return;
        }
        t.buffer = new uint8_t[TRANSFER_SIZE];
        t.connection = this;
        t.pending = false;

        submitTransfer(t);
    }

    // Start event handling thread
    eventThread_ = std::thread(&UsbConnection::eventLoop, this);
}

void UsbConnection::stopReading() {
    if (!running_.exchange(false)) {
        return; // Already stopped
    }

    LOGI("Stopping async USB reading");

    // Cancel pending transfers
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        if (transfers_[i].transfer && transfers_[i].pending) {
            libusb_cancel_transfer(transfers_[i].transfer);
        }
    }

    // Wait for event thread to finish
    if (eventThread_.joinable()) {
        eventThread_.join();
    }

    // Free transfers
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        Transfer& t = transfers_[i];
        if (t.transfer) {
            libusb_free_transfer(t.transfer);
            t.transfer = nullptr;
        }
        if (t.buffer) {
            delete[] t.buffer;
            t.buffer = nullptr;
        }
    }

    LOGI("Async USB reading stopped");
}

void UsbConnection::submitTransfer(Transfer& transfer) {
    libusb_fill_bulk_transfer(
        transfer.transfer,
        deviceHandle_,
        inEndpoint_,
        transfer.buffer,
        TRANSFER_SIZE,
        transferCallback,
        &transfer,
        0  // No timeout for async reads
    );

    int rc = libusb_submit_transfer(transfer.transfer);
    if (rc != LIBUSB_SUCCESS) {
        LOGE("Failed to submit transfer: %s", libusb_error_name(rc));
        transfer.pending = false;
    } else {
        transfer.pending = true;
    }
}

void LIBUSB_CALL UsbConnection::transferCallback(libusb_transfer* transfer) {
    Transfer* t = static_cast<Transfer*>(transfer->user_data);
    t->pending = false;

    if (!t->connection->running_) {
        return;
    }

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED) {
        t->connection->handleTransferComplete(*t, transfer->actual_length);
    } else if (transfer->status == LIBUSB_TRANSFER_CANCELLED) {
        LOGD("Transfer cancelled");
        return;
    } else {
        LOGE("Transfer failed: status=%d", transfer->status);
        // Report error but try to continue
        if (t->connection->errorCallback_) {
            t->connection->errorCallback_(transfer->status, "USB transfer failed");
        }
    }

    // Resubmit for next read
    if (t->connection->running_) {
        t->connection->submitTransfer(*t);
    }
}

void UsbConnection::handleTransferComplete(Transfer& transfer, int actualLength) {
    if (actualLength > 0) {
        processReceivedData(transfer.buffer, actualLength);
    }
}

void UsbConnection::processReceivedData(const uint8_t* data, size_t length) {
    // Write to ring buffer (this is fast, no blocking)
    size_t written = readBuffer_.write(data, length);
    if (written < length) {
        LOGE("Ring buffer overflow, dropped %zu bytes", length - written);
    }

    // Process complete messages from the buffer
    while (true) {
        if (readingHeader_) {
            // Try to read header
            size_t needed = 4 - headerPos_;
            size_t got = readBuffer_.read(headerBuf_ + headerPos_, needed);
            headerPos_ += got;

            if (headerPos_ < 4) {
                break; // Not enough data yet
            }

            // Parse header
            uint8_t channel = headerBuf_[0];
            uint8_t flags = headerBuf_[1];
            uint16_t encLen = (static_cast<uint16_t>(headerBuf_[2]) << 8) | headerBuf_[3];

            // Validate
            if ((flags & 0x08) != 0x08) {
                LOGE("Invalid flags in header: 0x%02x", flags);
                // Try to resync by skipping a byte
                headerBuf_[0] = headerBuf_[1];
                headerBuf_[1] = headerBuf_[2];
                headerBuf_[2] = headerBuf_[3];
                headerPos_ = 3;
                continue;
            }

            if (encLen > 65535) {
                LOGE("Invalid message length: %d", encLen);
                headerPos_ = 0;
                continue;
            }

            // Prepare for message body
            messageExpected_ = encLen;
            messagePos_ = 0;
            messageBuf_.resize(encLen + 4); // Include header for Kotlin
            memcpy(messageBuf_.data(), headerBuf_, 4);
            readingHeader_ = false;
            headerPos_ = 0;
        }

        // Try to read message body
        size_t needed = messageExpected_ - messagePos_;
        size_t got = readBuffer_.read(messageBuf_.data() + 4 + messagePos_, needed);
        messagePos_ += got;

        if (messagePos_ < messageExpected_) {
            break; // Not enough data yet
        }

        // Message complete - dispatch it
        uint8_t channel = messageBuf_[0];

        std::lock_guard<std::mutex> lock(callbackMutex_);
        if (dispatcher_) {
            // Pass the encrypted message data (header + body)
            dispatcher_->dispatch(channel, messageBuf_.data(), messageBuf_.size());
        }

        // Ready for next message
        readingHeader_ = true;
    }
}

int UsbConnection::write(const uint8_t* data, size_t length) {
    if (!deviceHandle_) {
        return -1;
    }

    int transferred = 0;
    int rc = libusb_bulk_transfer(
        deviceHandle_,
        outEndpoint_,
        const_cast<uint8_t*>(data),
        static_cast<int>(length),
        &transferred,
        WRITE_TIMEOUT_MS
    );

    if (rc != LIBUSB_SUCCESS && rc != LIBUSB_ERROR_TIMEOUT) {
        LOGE("Write failed: %s", libusb_error_name(rc));
        return -1;
    }

    return transferred;
}

void UsbConnection::eventLoop() {
    pthread_setname_np(pthread_self(), "AAP-USB-Event");
    LOGD("USB event loop started");

    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 100000; // 100ms timeout

    while (running_) {
        int rc = libusb_handle_events_timeout_completed(context_, &tv, nullptr);
        if (rc != LIBUSB_SUCCESS && rc != LIBUSB_ERROR_TIMEOUT) {
            LOGE("libusb_handle_events error: %s", libusb_error_name(rc));
            if (rc == LIBUSB_ERROR_NO_DEVICE) {
                running_ = false;
                std::lock_guard<std::mutex> lock(callbackMutex_);
                if (errorCallback_) {
                    errorCallback_(rc, "USB device disconnected");
                }
                break;
            }
        }
    }

    LOGD("USB event loop stopped");
}

void UsbConnection::setError(const char* format, ...) {
    va_list args;
    va_start(args, format);
    vsnprintf(lastError_, sizeof(lastError_), format, args);
    va_end(args);
    LOGE("%s", lastError_);
}

} // namespace aap
