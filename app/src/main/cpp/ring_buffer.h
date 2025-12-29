#pragma once

#include <atomic>
#include <cstdint>
#include <cstddef>

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) ring buffer.
 * Used to pass USB data from the read thread to processing threads
 * without blocking or allocation.
 */
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity);
    ~RingBuffer();

    // Non-copyable
    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    /**
     * Write data to the buffer (producer side).
     * @param data Pointer to data to write
     * @param length Number of bytes to write
     * @return Number of bytes actually written (may be less if buffer full)
     */
    size_t write(const uint8_t* data, size_t length);

    /**
     * Read data from the buffer (consumer side).
     * @param data Pointer to buffer to read into
     * @param maxLength Maximum number of bytes to read
     * @return Number of bytes actually read
     */
    size_t read(uint8_t* data, size_t maxLength);

    /**
     * Peek at data without consuming it.
     * @param data Pointer to buffer to copy into
     * @param maxLength Maximum number of bytes to peek
     * @return Number of bytes actually peeked
     */
    size_t peek(uint8_t* data, size_t maxLength) const;

    /**
     * Skip (discard) bytes from the buffer.
     * @param length Number of bytes to skip
     * @return Number of bytes actually skipped
     */
    size_t skip(size_t length);

    /**
     * Get the number of bytes available to read.
     */
    size_t available() const;

    /**
     * Get the amount of free space for writing.
     */
    size_t freeSpace() const;

    /**
     * Check if buffer is empty.
     */
    bool isEmpty() const;

    /**
     * Check if buffer is full.
     */
    bool isFull() const;

    /**
     * Get the total capacity of the buffer.
     */
    size_t capacity() const { return capacity_; }

    /**
     * Clear the buffer.
     */
    void clear();

private:
    uint8_t* buffer_;
    size_t capacity_;

    // Cache line padding to prevent false sharing
    alignas(64) std::atomic<size_t> writePos_;
    alignas(64) std::atomic<size_t> readPos_;
};
