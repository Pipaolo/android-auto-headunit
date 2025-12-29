#include "channel_dispatcher.h"
#include <android/log.h>
#include <pthread.h>
#include <sched.h>
#include <unistd.h>
#include <sys/resource.h>

#define LOG_TAG "ChannelDispatcher"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace aap {

// Queue size limits
constexpr size_t AUDIO_QUEUE_SIZE = 64;   // ~100ms at typical audio frame rate
constexpr size_t VIDEO_QUEUE_SIZE = 16;   // Video frames are larger, fewer needed
constexpr size_t CONTROL_QUEUE_SIZE = 32; // Control messages

// MessageQueue implementation
ChannelDispatcher::MessageQueue::MessageQueue(size_t maxSize)
    : maxSize_(maxSize)
{}

bool ChannelDispatcher::MessageQueue::push(QueuedMessage&& msg) {
    std::unique_lock<std::mutex> lock(mutex_);

    if (shutdown_) {
        return false;
    }

    // Drop oldest if queue is full (for audio, we want latest data)
    if (queue_.size() >= maxSize_) {
        queue_.pop();
        return false; // Indicate drop occurred
    }

    queue_.push(std::move(msg));
    lock.unlock();
    cv_.notify_one();
    return true;
}

bool ChannelDispatcher::MessageQueue::pop(QueuedMessage& msg) {
    std::unique_lock<std::mutex> lock(mutex_);

    cv_.wait(lock, [this] {
        return !queue_.empty() || shutdown_;
    });

    if (shutdown_ && queue_.empty()) {
        return false;
    }

    msg = std::move(queue_.front());
    queue_.pop();
    return true;
}

void ChannelDispatcher::MessageQueue::shutdown() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        shutdown_ = true;
    }
    cv_.notify_all();
}

// ChannelDispatcher implementation
ChannelDispatcher::ChannelDispatcher()
    : audioQueue_(std::make_unique<MessageQueue>(AUDIO_QUEUE_SIZE))
    , videoQueue_(std::make_unique<MessageQueue>(VIDEO_QUEUE_SIZE))
    , controlQueue_(std::make_unique<MessageQueue>(CONTROL_QUEUE_SIZE))
{}

ChannelDispatcher::~ChannelDispatcher() {
    stop();
}

void ChannelDispatcher::setAudioCallback(MessageCallback callback) {
    audioCallback_ = std::move(callback);
}

void ChannelDispatcher::setVideoCallback(MessageCallback callback) {
    videoCallback_ = std::move(callback);
}

void ChannelDispatcher::setControlCallback(MessageCallback callback) {
    controlCallback_ = std::move(callback);
}

void ChannelDispatcher::start() {
    if (running_.exchange(true)) {
        return; // Already running
    }

    LOGD("Starting dispatcher threads");

    audioThread_ = std::thread(&ChannelDispatcher::audioWorker, this);
    videoThread_ = std::thread(&ChannelDispatcher::videoWorker, this);
    controlThread_ = std::thread(&ChannelDispatcher::controlWorker, this);
}

void ChannelDispatcher::stop() {
    if (!running_.exchange(false)) {
        return; // Already stopped
    }

    LOGD("Stopping dispatcher threads");

    // Signal queues to shutdown
    audioQueue_->shutdown();
    videoQueue_->shutdown();
    controlQueue_->shutdown();

    // Wait for threads to finish
    if (audioThread_.joinable()) audioThread_.join();
    if (videoThread_.joinable()) videoThread_.join();
    if (controlThread_.joinable()) controlThread_.join();

    LOGD("Dispatcher threads stopped");
}

void ChannelDispatcher::dispatch(int channel, const uint8_t* data, size_t length) {
    QueuedMessage msg;
    msg.channel = channel;
    msg.data.assign(data, data + length);

    bool dropped = false;
    ChannelPriority priority = getChannelPriority(channel);

    switch (priority) {
        case ChannelPriority::HIGH:
            dropped = !audioQueue_->push(std::move(msg));
            if (dropped) {
                std::lock_guard<std::mutex> lock(statsMutex_);
                stats_.audioQueueDrops++;
            } else {
                std::lock_guard<std::mutex> lock(statsMutex_);
                stats_.audioMessagesDispatched++;
            }
            break;

        case ChannelPriority::MEDIUM:
            dropped = !videoQueue_->push(std::move(msg));
            if (dropped) {
                std::lock_guard<std::mutex> lock(statsMutex_);
                stats_.videoQueueDrops++;
            } else {
                std::lock_guard<std::mutex> lock(statsMutex_);
                stats_.videoMessagesDispatched++;
            }
            break;

        case ChannelPriority::NORMAL:
        default:
            controlQueue_->push(std::move(msg));
            {
                std::lock_guard<std::mutex> lock(statsMutex_);
                stats_.controlMessagesDispatched++;
            }
            break;
    }
}

ChannelDispatcher::Stats ChannelDispatcher::getStats() const {
    std::lock_guard<std::mutex> lock(statsMutex_);
    return stats_;
}

void ChannelDispatcher::setRealtimePriority() {
    // Try to set SCHED_FIFO for real-time audio processing
    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_FIFO);

    int result = pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);
    if (result != 0) {
        // Fall back to high nice value
        LOGD("Could not set SCHED_FIFO (error %d), using high priority nice", result);
        nice(-19);
    } else {
        LOGD("Audio thread running with SCHED_FIFO priority %d", param.sched_priority);
    }
}

void ChannelDispatcher::audioWorker() {
    pthread_setname_np(pthread_self(), "AAP-Audio");
    setRealtimePriority();

    LOGD("Audio worker started");

    QueuedMessage msg;
    while (audioQueue_->pop(msg)) {
        if (audioCallback_) {
            audioCallback_(msg.channel, msg.data.data(), msg.data.size());
        }
    }

    LOGD("Audio worker stopped");
}

void ChannelDispatcher::videoWorker() {
    pthread_setname_np(pthread_self(), "AAP-Video");

    LOGD("Video worker started");

    QueuedMessage msg;
    while (videoQueue_->pop(msg)) {
        if (videoCallback_) {
            videoCallback_(msg.channel, msg.data.data(), msg.data.size());
        }
    }

    LOGD("Video worker stopped");
}

void ChannelDispatcher::controlWorker() {
    pthread_setname_np(pthread_self(), "AAP-Control");

    LOGD("Control worker started");

    QueuedMessage msg;
    while (controlQueue_->pop(msg)) {
        if (controlCallback_) {
            controlCallback_(msg.channel, msg.data.data(), msg.data.size());
        }
    }

    LOGD("Control worker stopped");
}

} // namespace aap
