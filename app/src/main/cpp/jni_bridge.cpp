#include <jni.h>
#include <android/log.h>
#include "usb_connection.h"
#include <unordered_map>
#include <mutex>

#define LOG_TAG "JNI_Bridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Connection handle management
struct ConnectionHandle {
    std::unique_ptr<aap::UsbConnection> connection;
};

std::mutex handlesMutex;
std::unordered_map<jlong, std::unique_ptr<ConnectionHandle>> handles;
jlong nextHandle = 1;

// Cached JNI references
JavaVM* javaVm = nullptr;
jclass nativeUsbClass = nullptr;
jmethodID onRawDataMethod = nullptr;
jmethodID onErrorMethod = nullptr;

// Get JNIEnv for current thread
JNIEnv* getEnv() {
    JNIEnv* env = nullptr;
    if (javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        javaVm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// Callback for raw USB data
void callRawDataCallback(const uint8_t* data, size_t length) {
    JNIEnv* env = getEnv();
    if (!env || !nativeUsbClass || !onRawDataMethod) {
        LOGE("callRawDataCallback: JNI not ready");
        return;
    }

    jbyteArray jdata = env->NewByteArray(static_cast<jsize>(length));
    if (jdata) {
        env->SetByteArrayRegion(jdata, 0, static_cast<jsize>(length),
                                reinterpret_cast<const jbyte*>(data));
        env->CallStaticVoidMethod(nativeUsbClass, onRawDataMethod,
                                  jdata, static_cast<jint>(length));
        env->DeleteLocalRef(jdata);
    }
}

// Callback for errors
void callErrorCallback(int errorCode, const char* message) {
    JNIEnv* env = getEnv();
    if (!env || !nativeUsbClass || !onErrorMethod) return;

    jstring jmessage = env->NewStringUTF(message);
    if (jmessage) {
        env->CallStaticVoidMethod(nativeUsbClass, onErrorMethod, errorCode, jmessage);
        env->DeleteLocalRef(jmessage);
    }
}

ConnectionHandle* getHandle(jlong handle) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    auto it = handles.find(handle);
    if (it != handles.end()) {
        return it->second.get();
    }
    return nullptr;
}

} // anonymous namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called");
    javaVm = vm;

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNIEnv");
        return JNI_ERR;
    }

    // Cache class and method references
    jclass localClass = env->FindClass("info/anodsplace/headunit/connection/NativeUsb");
    if (!localClass) {
        LOGE("Failed to find NativeUsb class");
        return JNI_ERR;
    }
    nativeUsbClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);

    onRawDataMethod = env->GetStaticMethodID(nativeUsbClass, "onRawData", "([BI)V");
    onErrorMethod = env->GetStaticMethodID(nativeUsbClass, "onError", "(ILjava/lang/String;)V");

    if (!onRawDataMethod || !onErrorMethod) {
        LOGE("Failed to find callback methods");
        return JNI_ERR;
    }

    LOGI("JNI initialized successfully");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload called");

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (nativeUsbClass) {
            env->DeleteGlobalRef(nativeUsbClass);
            nativeUsbClass = nullptr;
        }
    }

    // Clean up all handles
    std::lock_guard<std::mutex> lock(handlesMutex);
    handles.clear();

    javaVm = nullptr;
}

JNIEXPORT jlong JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeOpen(
        JNIEnv* env, jclass clazz, jint fileDescriptor) {

    LOGI("nativeOpen called with fd=%d", fileDescriptor);

    auto handle = std::make_unique<ConnectionHandle>();
    handle->connection = std::make_unique<aap::UsbConnection>();

    // Set up raw data callback - Kotlin handles parsing
    handle->connection->setRawDataCallback(callRawDataCallback);
    handle->connection->setErrorCallback(callErrorCallback);

    // Open USB device
    if (!handle->connection->open(fileDescriptor)) {
        LOGE("Failed to open USB device: %s", handle->connection->getLastError());
        return 0;
    }

    // Store handle
    std::lock_guard<std::mutex> lock(handlesMutex);
    jlong handleId = nextHandle++;
    handles[handleId] = std::move(handle);

    LOGI("USB device opened successfully, handle=%ld", (long)handleId);
    return handleId;
}

JNIEXPORT void JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeClose(
        JNIEnv* env, jclass clazz, jlong handle) {

    LOGI("nativeClose called for handle=%ld", (long)handle);

    std::lock_guard<std::mutex> lock(handlesMutex);
    auto it = handles.find(handle);
    if (it != handles.end()) {
        // Destructor will clean up
        handles.erase(it);
    }
}

JNIEXPORT void JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeStartReading(
        JNIEnv* env, jclass clazz, jlong handle) {

    LOGI("nativeStartReading called for handle=%ld", (long)handle);

    ConnectionHandle* h = getHandle(handle);
    if (h) {
        h->connection->startReading();
    }
}

JNIEXPORT void JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeStopReading(
        JNIEnv* env, jclass clazz, jlong handle) {

    LOGI("nativeStopReading called for handle=%ld", (long)handle);

    ConnectionHandle* h = getHandle(handle);
    if (h) {
        h->connection->stopReading();
    }
}

JNIEXPORT jint JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeWrite(
        JNIEnv* env, jclass clazz, jlong handle, jbyteArray data, jint length) {

    LOGD("nativeWrite called: handle=%ld, length=%d", (long)handle, length);

    ConnectionHandle* h = getHandle(handle);
    if (!h) {
        LOGE("nativeWrite: invalid handle %ld", (long)handle);
        return -1;
    }

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) {
        LOGE("nativeWrite: failed to get byte array");
        return -1;
    }

    int result = h->connection->write(reinterpret_cast<uint8_t*>(bytes), length);
    LOGD("nativeWrite: result=%d", result);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return result;
}

JNIEXPORT jint JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeRead(
        JNIEnv* env, jclass clazz, jlong handle, jbyteArray data, jint length, jint timeoutMs) {

    LOGD("nativeRead called: handle=%ld, length=%d, timeout=%d", (long)handle, length, timeoutMs);

    ConnectionHandle* h = getHandle(handle);
    if (!h) {
        LOGE("nativeRead: invalid handle");
        return -1;
    }

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) {
        LOGE("nativeRead: failed to get byte array");
        return -1;
    }

    int result = h->connection->read(reinterpret_cast<uint8_t*>(bytes), length, timeoutMs);

    // Copy data back to Java array
    env->ReleaseByteArrayElements(data, bytes, 0);

    LOGD("nativeRead: result=%d", result);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_info_anodsplace_headunit_connection_NativeUsb_nativeIsOpen(
        JNIEnv* env, jclass clazz, jlong handle) {

    ConnectionHandle* h = getHandle(handle);
    return h && h->connection->isOpen() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
