cmake_minimum_required(VERSION 3.22.1)
project(headunit_usb C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# libusb source directory
set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libusb-1.0.29)

# Build libusb as static library
add_library(usb1.0 STATIC
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/sync.c
    # Linux/Android backend
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c
)

target_include_directories(usb1.0 PUBLIC
    ${LIBUSB_DIR}/libusb
    ${LIBUSB_DIR}/android  # For config.h
)

target_compile_definitions(usb1.0 PRIVATE
    OS_LINUX=1
    HAVE_CLOCK_GETTIME=1
)

# Our native library
add_library(headunit_usb SHARED
    usb_connection.cpp
    ring_buffer.cpp
    channel_dispatcher.cpp
    aap_message.cpp
    jni_bridge.cpp
)

target_include_directories(headunit_usb PRIVATE
    ${LIBUSB_DIR}/libusb
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(headunit_usb
    usb1.0
    android
    log
)

# Optimization flags for release
target_compile_options(headunit_usb PRIVATE
    $<$<CONFIG:Release>:-O3 -ffast-math -flto>
)

target_compile_options(usb1.0 PRIVATE
    $<$<CONFIG:Release>:-O3 -flto>
)
