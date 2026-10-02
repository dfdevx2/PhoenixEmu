#include "audio_output.h"
#include <oboe/Oboe.h>
#include <atomic>
#include <vector>
#include <cstring>
#include <algorithm>
#include <mutex>
#include "verificador_audio.h"

namespace {
    std::vector<int16_t> g_ring;
    std::atomic<size_t> g_head{0};
    std::atomic<size_t> g_tail{0};
    size_t g_capacity = 0;
    size_t g_frames_per_video_frame = 0;

    double g_sample_rate_saved = 44100.0;

    std::shared_ptr<oboe::AudioStream> g_stream;
    std::mutex g_stream_mutex;

    void phoenix_audio_reabrir();

    class AudioCallback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
    public:
        oboe::DataCallbackResult onAudioReady(
                oboe::AudioStream *audioStream,
                void *audioData,
                int32_t numFrames) override {

            auto* dst = static_cast<int16_t*>(audioData);
            size_t head = g_head.load(std::memory_order_acquire);
            size_t tail = g_tail.load(std::memory_order_relaxed);

            size_t available = (head >= tail) ? (head - tail) : (g_capacity - tail + head);
            size_t to_read = std::min(static_cast<size_t>(numFrames), available);

            if (to_read > 0) {
                if (tail + to_read <= g_capacity) {
                    std::memcpy(dst, &g_ring[tail * 2], to_read * 2 * sizeof(int16_t));
                } else {
                    size_t part1 = g_capacity - tail;
                    size_t part2 = to_read - part1;
                    std::memcpy(dst, &g_ring[tail * 2], part1 * 2 * sizeof(int16_t));
                    std::memcpy(dst + part1 * 2, &g_ring[0], part2 * 2 * sizeof(int16_t));
                }
                g_tail.store((tail + to_read) % g_capacity, std::memory_order_release);
                audio_saude_consumiu(numFrames, to_read);
            } else {
                audio_saude_consumiu(numFrames, 0);
            }

            if (to_read < static_cast<size_t>(numFrames)) {
                std::memset(dst + to_read * 2, 0, (numFrames - to_read) * 2 * sizeof(int16_t));
            }

            return oboe::DataCallbackResult::Continue;
        }

        void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
            __android_log_print(ANDROID_LOG_ERROR, "PhoenixAudio", "Stream desconectado: %s", oboe::convertToText(error));
            phoenix_audio_reabrir();
        }
    };

    AudioCallback g_callback;

    void phoenix_audio_reabrir() {
        std::lock_guard<std::mutex> lock(g_stream_mutex);
        if (g_stream) {
            g_stream.reset();
        }

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::I16)
               ->setChannelCount(2)
               ->setSampleRate(static_cast<int32_t>(g_sample_rate_saved))
               ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
               ->setDataCallback(&g_callback)
               ->setErrorCallback(&g_callback);

        oboe::Result result = builder.openStream(g_stream);
        if (result == oboe::Result::OK && g_stream) {
            audio_saude_iniciar();
            g_stream->requestStart();
        } else {
            __android_log_print(ANDROID_LOG_ERROR, "PhoenixAudio", "Falha ao reabrir stream: %s", oboe::convertToText(result));
            if (g_stream) g_stream.reset();
        }
    }
}

extern "C" {

void phoenix_audio_inicializar(double sample_rate, double fps) {
    std::lock_guard<std::mutex> lock(g_stream_mutex);
    if (g_stream) return;

    if (fps <= 0.0) fps = 60.0;
    if (sample_rate <= 0.0) sample_rate = 44100.0;
    g_sample_rate_saved = sample_rate;

    g_frames_per_video_frame = static_cast<size_t>(sample_rate / fps);
    g_capacity = g_frames_per_video_frame * 4 + 1;

    g_ring.resize(g_capacity * 2);
    g_head.store(0);
    g_tail.store(0);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(2)
           ->setSampleRate(static_cast<int32_t>(sample_rate))
           ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
           ->setDataCallback(&g_callback)
           ->setErrorCallback(&g_callback);

    oboe::Result result = builder.openStream(g_stream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, "PhoenixAudio", "Falha ao abrir stream: %s", oboe::convertToText(result));
        if (g_stream) g_stream.reset();
    }
}

void phoenix_audio_iniciar_stream() {
    std::lock_guard<std::mutex> lock(g_stream_mutex);
    if (g_stream) {
        audio_saude_iniciar();
        g_stream->requestStart();
    }
}

bool phoenix_audio_ativo() {
    std::lock_guard<std::mutex> lock(g_stream_mutex);
    if (!g_stream) return false;
    auto state = g_stream->getState();
    return state != oboe::StreamState::Closed &&
           state != oboe::StreamState::Closing &&
           state != oboe::StreamState::Disconnected;
}

size_t phoenix_audio_ocupacao() {
    size_t tail = g_tail.load(std::memory_order_acquire);
    size_t head = g_head.load(std::memory_order_relaxed);
    return (head >= tail) ? (head - tail) : (g_capacity - tail + head);
}

size_t phoenix_audio_enviar(const int16_t *dados, size_t quadros) {
    if (!dados || quadros == 0) return 0;
    if (!phoenix_audio_ativo()) return quadros; // DESCARTA

    size_t tail = g_tail.load(std::memory_order_acquire);
    size_t head = g_head.load(std::memory_order_relaxed);

    size_t free_space = (tail > head) ? (tail - head - 1) : (g_capacity - head + tail - 1);
    size_t to_write = std::min(quadros, free_space);

    if (to_write > 0) {
        if (head + to_write <= g_capacity) {
            std::memcpy(&g_ring[head * 2], dados, to_write * 2 * sizeof(int16_t));
        } else {
            size_t part1 = g_capacity - head;
            size_t part2 = to_write - part1;
            std::memcpy(&g_ring[head * 2], dados, part1 * 2 * sizeof(int16_t));
            std::memcpy(&g_ring[0], dados + part1 * 2, part2 * 2 * sizeof(int16_t));
        }
        g_head.store((head + to_write) % g_capacity, std::memory_order_release);
    }

    size_t current_occupancy = (head + to_write >= tail) ? (head + to_write - tail) : (g_capacity - tail + head + to_write);
    audio_saude_produziu(quadros, to_write, current_occupancy, g_capacity - 1);

    return to_write;
}

void phoenix_audio_finalizar() {
    std::lock_guard<std::mutex> lock(g_stream_mutex);
    if (g_stream) {
        g_stream->requestStop();
        g_stream->close();
        g_stream.reset();
    }
}

size_t phoenix_audio_espaco_livre() {
    size_t tail = g_tail.load(std::memory_order_acquire);
    size_t head = g_head.load(std::memory_order_relaxed);
    return (tail > head) ? (tail - head - 1) : (g_capacity - head + tail - 1);
}

size_t phoenix_audio_tamanho_minimo() {
    return g_frames_per_video_frame;
}

void phoenix_audio_relatar() {
    audio_saude_relatar();
}

} // extern "C"
