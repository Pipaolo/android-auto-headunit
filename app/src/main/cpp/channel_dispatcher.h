#pragma once

#include "aap_message.h"
#include <functional>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <atomic>
#include <vector>

namespace aap {

/**
 * Callback type for dispatching messages to Kotlin.
 * Parameters: channel, data pointer, data length
 */
using MessageCallback = std::function<void(int channel, const uint8_t* data, size_t length)>;

/**
 * Channel dispatcher routes AAP messages to priority-based queues
 * and delivers them via callbacks on dedicated threads.
 *
 * - Audio: High priority, real-time thread (SCHED_FIFO if available)
 * - Video: Medium priority, normal thread
 * - Control/Other: Normal priority, normal thread
 */
class ChannelDispatcher {
public:
    ChannelDispatcher();
    ~ChannelDispatcher();

    // Non-copyable
    ChannelDispatcher(const ChannelDispatcher&) = delete;
    ChannelDispatcher& operator=(const ChannelDispatcher&) = delete;

    /**
     * Set callbacks for each channel type.
     * Must be called before start().
     */
    void setAudioCallback(MessageCallback callback);
    void setVideoCallback(MessageCallback callback);
    void setControlCallback(MessageCallback callback);

    /**
     * Start dispatcher threads.
     */
    void start();

    /**
     * Stop dispatcher threads and wait for them to finish.
     */
    void stop();

    /**
     * Dispatch a decrypted message to the appropriate queue.
     * This is called from the USB read thread.
     * Returns immediately after queuing.
     */
    void dispatch(int channel, const uint8_t* data, size_t length);

    /**
     * Get statistics for monitoring.
     */
    struct Stats {
        uint64_t audioMessagesDispatched;
        uint64_t videoMessagesDispatched;
        uint64_t controlMessagesDispatched;
        uint64_t audioQueueDrops;
        uint64_t videoQueueDrops;
    };
    Stats getStats() const;

private:
    // Message structure for internal queuing
    struct QueuedMessage {
        int channel;
        std::vector<uint8_t> data;
    };

    // Thread-safe queue with size limit
    class MessageQueue {
    public:
        explicit MessageQueue(size_t maxSize);

        bool push(QueuedMessage&& msg);  // Returns false if queue full (dropped)
        bool pop(QueuedMessage& msg);    // Blocking pop
        void shutdown();

    private:
        std::queue<QueuedMessage> queue_;
        std::mutex mutex_;
        std::condition_variable cv_;
        size_t maxSize_;
        std::atomic<bool> shutdown_{false};
    };

    // Queues for each priority level
    std::unique_ptr<MessageQueue> audioQueue_;
    std::unique_ptr<MessageQueue> videoQueue_;
    std::unique_ptr<MessageQueue> controlQueue_;

    // Worker threads
    std::thread audioThread_;
    std::thread videoThread_;
    std::thread controlThread_;

    // Callbacks
    MessageCallback audioCallback_;
    MessageCallback videoCallback_;
    MessageCallback controlCallback_;

    // State
    std::atomic<bool> running_{false};

    // Statistics
    mutable std::mutex statsMutex_;
    Stats stats_{};

    // Worker thread functions
    void audioWorker();
    void videoWorker();
    void controlWorker();

    // Set thread priority (platform-specific)
    static void setRealtimePriority();
};

} // namespace aap
