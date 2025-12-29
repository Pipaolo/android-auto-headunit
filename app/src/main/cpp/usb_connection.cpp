#include "usb_connection.h"
#include <android/log.h>
#include <cstdarg>
#include <cstring>

#define LOG_TAG "UsbConnection"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace aap {

// USB timeouts
constexpr int WRITE_TIMEOUT_MS = 1000;

UsbConnection::UsbConnection() = default;

UsbConnection::~UsbConnection() {
    close();
}

bool UsbConnection::open(int fd) {
    if (deviceHandle_) {
        setError("Already open");
        return false;
    }

    LOGI("Opening USB connection with fd=%d", fd);

    // On Android with wrapped file descriptors, we must disable device discovery
    // since we're using Android's USB subsystem to get the FD
    int rc = libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    if (rc != LIBUSB_SUCCESS) {
        LOGE("libusb_set_option(NO_DEVICE_DISCOVERY) failed: %s (continuing anyway)", libusb_error_name(rc));
    }

    // Initialize libusb
    rc = libusb_init(&context_);
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

    // When using libusb_wrap_sys_device with an Android file descriptor,
    // the interface is already claimed by Android's UsbDeviceConnection.
    // We should NOT call libusb_claim_interface as it will fail with BUSY.
    LOGI("Skipping interface claim - Android already claimed it");

    LOGI("USB connection opened successfully");
    LOGI("  IN endpoint: 0x%02x, OUT endpoint: 0x%02x", inEndpoint_, outEndpoint_);
    LOGI("  Max packet size: %d", maxPacketSize_);

    return true;
}

void UsbConnection::close() {
    stopReading();

    if (deviceHandle_) {
        LOGI("Closing USB connection");
        // Don't release interface - Android owns it, not libusb
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

void UsbConnection::setRawDataCallback(RawDataCallback callback) {
    std::lock_guard<std::mutex> lock(callbackMutex_);
    rawDataCallback_ = std::move(callback);
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
        std::lock_guard<std::mutex> lock(t->connection->callbackMutex_);
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
        // Pass raw data directly to Kotlin for parsing
        std::lock_guard<std::mutex> lock(callbackMutex_);
        if (rawDataCallback_) {
            rawDataCallback_(transfer.buffer, actualLength);
        }
    }
}

int UsbConnection::write(const uint8_t* data, size_t length) {
    if (!deviceHandle_) {
        LOGE("Write failed: device not open");
        return -1;
    }

    LOGD("Write: attempting %zu bytes to endpoint 0x%02x", length, outEndpoint_);

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
        LOGE("Write failed: %s (rc=%d)", libusb_error_name(rc), rc);
        return -1;
    }

    LOGD("Write completed: %d bytes transferred", transferred);
    return transferred;
}

int UsbConnection::read(uint8_t* buffer, size_t length, int timeoutMs) {
    if (!deviceHandle_) {
        LOGE("Read failed: device not open");
        return -1;
    }

    LOGD("Read: attempting up to %zu bytes from endpoint 0x%02x, timeout=%dms", length, inEndpoint_, timeoutMs);

    int transferred = 0;
    int rc = libusb_bulk_transfer(
        deviceHandle_,
        inEndpoint_,
        buffer,
        static_cast<int>(length),
        &transferred,
        timeoutMs
    );

    if (rc == LIBUSB_ERROR_TIMEOUT) {
        LOGD("Read timeout after %dms, transferred %d bytes", timeoutMs, transferred);
        return transferred;
    }

    if (rc != LIBUSB_SUCCESS) {
        LOGE("Read failed: %s (rc=%d)", libusb_error_name(rc), rc);
        return -1;
    }

    LOGD("Read completed: %d bytes", transferred);
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
