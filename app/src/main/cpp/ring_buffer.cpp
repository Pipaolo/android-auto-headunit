#include "ring_buffer.h"
#include <algorithm>
#include <cstring>

RingBuffer::RingBuffer(size_t capacity)
    : capacity_(capacity)
    , writePos_(0)
    , readPos_(0)
{
    // Allocate with alignment for better cache performance
    buffer_ = new (std::align_val_t(64)) uint8_t[capacity];
}

RingBuffer::~RingBuffer() {
    ::operator delete[](buffer_, std::align_val_t(64));
}

size_t RingBuffer::write(const uint8_t* data, size_t length) {
    const size_t currentRead = readPos_.load(std::memory_order_acquire);
    const size_t currentWrite = writePos_.load(std::memory_order_relaxed);

    // Calculate available space
    size_t available;
    if (currentWrite >= currentRead) {
        available = capacity_ - (currentWrite - currentRead) - 1;
    } else {
        available = currentRead - currentWrite - 1;
    }

    const size_t toWrite = std::min(length, available);
    if (toWrite == 0) {
        return 0;
    }

    // Write data, handling wrap-around
    const size_t firstPart = std::min(toWrite, capacity_ - currentWrite);
    std::memcpy(buffer_ + currentWrite, data, firstPart);

    if (toWrite > firstPart) {
        std::memcpy(buffer_, data + firstPart, toWrite - firstPart);
    }

    // Update write position
    const size_t newWrite = (currentWrite + toWrite) % capacity_;
    writePos_.store(newWrite, std::memory_order_release);

    return toWrite;
}

size_t RingBuffer::read(uint8_t* data, size_t maxLength) {
    const size_t currentWrite = writePos_.load(std::memory_order_acquire);
    const size_t currentRead = readPos_.load(std::memory_order_relaxed);

    // Calculate available data
    size_t available;
    if (currentWrite >= currentRead) {
        available = currentWrite - currentRead;
    } else {
        available = capacity_ - currentRead + currentWrite;
    }

    const size_t toRead = std::min(maxLength, available);
    if (toRead == 0) {
        return 0;
    }

    // Read data, handling wrap-around
    const size_t firstPart = std::min(toRead, capacity_ - currentRead);
    std::memcpy(data, buffer_ + currentRead, firstPart);

    if (toRead > firstPart) {
        std::memcpy(data + firstPart, buffer_, toRead - firstPart);
    }

    // Update read position
    const size_t newRead = (currentRead + toRead) % capacity_;
    readPos_.store(newRead, std::memory_order_release);

    return toRead;
}

size_t RingBuffer::peek(uint8_t* data, size_t maxLength) const {
    const size_t currentWrite = writePos_.load(std::memory_order_acquire);
    const size_t currentRead = readPos_.load(std::memory_order_relaxed);

    size_t available;
    if (currentWrite >= currentRead) {
        available = currentWrite - currentRead;
    } else {
        available = capacity_ - currentRead + currentWrite;
    }

    const size_t toPeek = std::min(maxLength, available);
    if (toPeek == 0) {
        return 0;
    }

    const size_t firstPart = std::min(toPeek, capacity_ - currentRead);
    std::memcpy(data, buffer_ + currentRead, firstPart);

    if (toPeek > firstPart) {
        std::memcpy(data + firstPart, buffer_, toPeek - firstPart);
    }

    return toPeek;
}

size_t RingBuffer::skip(size_t length) {
    const size_t currentWrite = writePos_.load(std::memory_order_acquire);
    const size_t currentRead = readPos_.load(std::memory_order_relaxed);

    size_t available;
    if (currentWrite >= currentRead) {
        available = currentWrite - currentRead;
    } else {
        available = capacity_ - currentRead + currentWrite;
    }

    const size_t toSkip = std::min(length, available);
    if (toSkip == 0) {
        return 0;
    }

    const size_t newRead = (currentRead + toSkip) % capacity_;
    readPos_.store(newRead, std::memory_order_release);

    return toSkip;
}

size_t RingBuffer::available() const {
    const size_t currentWrite = writePos_.load(std::memory_order_acquire);
    const size_t currentRead = readPos_.load(std::memory_order_acquire);

    if (currentWrite >= currentRead) {
        return currentWrite - currentRead;
    } else {
        return capacity_ - currentRead + currentWrite;
    }
}

size_t RingBuffer::freeSpace() const {
    return capacity_ - available() - 1;
}

bool RingBuffer::isEmpty() const {
    return available() == 0;
}

bool RingBuffer::isFull() const {
    return freeSpace() == 0;
}

void RingBuffer::clear() {
    readPos_.store(0, std::memory_order_relaxed);
    writePos_.store(0, std::memory_order_release);
}
