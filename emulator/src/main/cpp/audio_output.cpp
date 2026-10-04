#include "audio_output.h"
#include <oboe/Oboe.h>
#include <atomic>
#include <vector>
#include <cstring>
#include <algorithm>
#include <mutex>
#include <chrono>
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

    // Contador atômico de falhas consecutivas do stream (ErrorTimeout/Disconnected)
    std::atomic<int> g_falhas_consecutivas{0};
    // Frames desde a última falha (para resetar falhas_consecutivas)
    uint64_t g_falhas_ultimo_delta = 0;
    // Tempo do último underrun detectado (para verificar se o áudio recuperou)
    std::chrono::steady_clock::time_point g_ultimo_underron_time;
    // Contador de underruns no momento da última verificação
    uint64_t g_ultimo_underron_count = 0;
    // Tempo da última tentativa de reconexão
    std::chrono::steady_clock::time_point g_ultimo_reconectar;
    // Flag: áudio morto — pacing por relógio de vídeo
    std::atomic<bool> g_audio_morto{false};
    // Frames realmente consumidos pelo callback de áudio
    std::atomic<uint64_t> g_frames_consumidos{0};
    // Frames no último check de recuperação (para calcular delta)
    uint64_t g_frames_ultimo_check = 0;
    // Quando o stream ficou ativo pela última vez (para timeout de 3 s)
    std::chrono::steady_clock::time_point g_stream_ativo_ultimo;

    void phoenix_audio_reabrir();

    class AudioCallback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
    public:
        oboe::DataCallbackResult onAudioReady(
                oboe::AudioStream *audioStream,
                void *audioData,
                int32_t numFrames) override {

            // Conta toda invocação do callback, independente de haver dados.
            // Necessário para detecção de recuperação do áudio morto.
            g_frames_consumidos.fetch_add(static_cast<uint64_t>(numFrames), std::memory_order_relaxed);

            // Quando áudio morto, descarta amostras do core para o ring buffer não encher.
            if (g_audio_morto.load(std::memory_order_acquire)) {
                std::memset(audioData, 0, static_cast<size_t>(numFrames) * 2 * sizeof(int16_t));
                return oboe::DataCallbackResult::Continue;
            }

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
            int falhas = ++g_falhas_consecutivas;
            if (falhas >= 3 && !g_audio_morto.load(std::memory_order_acquire)) {
                g_audio_morto.store(true, std::memory_order_release);
                __android_log_print(ANDROID_LOG_ERROR, "PhoenixAudio", "audio morto, pacing por relógio");
            }
            phoenix_audio_reabrir();
        }
    };

    AudioCallback g_callback;

    void phoenix_audio_reabrir() {
        // Cooldown de 10 s: só reabre se tempo suficiente passou desde a última tentativa
        if (g_audio_morto.load(std::memory_order_acquire)) {
            auto agora = std::chrono::steady_clock::now();
            auto diff = std::chrono::duration_cast<std::chrono::seconds>(agora - g_ultimo_reconectar).count();
            if (diff < 10) {
                // Força g_stream a nulo para que phoenix_audio_ativo() retorne false
                std::lock_guard<std::mutex> lock(g_stream_mutex);
                if (g_stream) g_stream.reset();
                return;
            }
            g_ultimo_reconectar = agora;
        }

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
            g_frames_ultimo_check = g_frames_consumidos.load(std::memory_order_relaxed);
            g_stream_ativo_ultimo = std::chrono::steady_clock::now();
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
    g_frames_ultimo_check = 0;

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

    if (g_audio_morto.load(std::memory_order_acquire)) {
        return quadros; // DESCARTA amostras quando áudio está morto
    }

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

void phoenix_audio_pausar() {
    std::lock_guard<std::mutex> lock(g_stream_mutex);
    if (g_stream && g_stream->getState() == oboe::StreamState::Started) {
        g_stream->requestPause();
    }
}

void phoenix_audio_flush_ring_buffer() {
    size_t old_head = g_head.load(std::memory_order_relaxed);
    size_t old_tail = g_tail.load(std::memory_order_acquire);
    g_head.store(old_tail, std::memory_order_release);
}

bool phoenix_audio_audio_morto() {
    return g_audio_morto.load(std::memory_order_acquire);
}

void phoenix_audio_reiniciar_saude() {
    g_falhas_consecutivas.store(0, std::memory_order_release);
    audio_saude_iniciar();
    phoenix_audio_flush_ring_buffer();
    g_ultimo_underron_time = std::chrono::steady_clock::now();
}

bool phoenix_audio_tentar_reabrir_se_morto() {
    if (!g_audio_morto.load(std::memory_order_acquire)) return false;

    auto agora = std::chrono::steady_clock::now();
    auto diff = std::chrono::duration_cast<std::chrono::seconds>(agora - g_ultimo_reconectar).count();

    bool stream_nulo = false;
    bool stream_nao_started = false;
    {
        std::lock_guard<std::mutex> lock(g_stream_mutex);
        if (!g_stream) {
            stream_nulo = true;
        } else {
            stream_nao_started = (g_stream->getState() != oboe::StreamState::Started);
        }
    }

    if ((stream_nulo || stream_nao_started) && diff >= 10) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixAudio", "tentando reabrir áudio morto");
        phoenix_audio_reabrir();
        return true;
    }
    return false;
}

bool phoenix_audio_verificar_recuperacao() {
    if (!g_audio_morto.load(std::memory_order_acquire)) return false;

    // Verifica se o stream existe e está Started
    if (phoenix_audio_ativo()) {
        uint64_t frames_atual = g_frames_consumidos.load(std::memory_order_relaxed);
        uint64_t frames_ultimo = g_frames_ultimo_check;
        auto diff = frames_atual - frames_ultimo;

        auto agora = std::chrono::steady_clock::now();
        auto diff_tempo = std::chrono::duration_cast<std::chrono::seconds>(agora - g_stream_ativo_ultimo).count();

        if (diff >= 48000) {
            __android_log_print(ANDROID_LOG_INFO, "PhoenixAudio", "audio recuperou, pacing normal");
            g_audio_morto.store(false, std::memory_order_release);
            g_falhas_consecutivas.store(0, std::memory_order_release);
            audio_saude_iniciar();
            phoenix_audio_flush_ring_buffer();
            return true;
        }

        if (diff_tempo > 3) {
            // Tempo limite atingido sem avanço suficiente: conta como nova falha
            g_audio_morto.store(true, std::memory_order_release);
            __android_log_print(ANDROID_LOG_ERROR, "PhoenixAudio", "audio ainda morto após 3 s, falha na recuperação");
            g_falhas_consecutivas.store(0, std::memory_order_release);
            // Atualiza tracking para não repetir o log todo segundo
            g_stream_ativo_ultimo = agora;
            g_frames_ultimo_check = frames_atual;
            // Reseta tracking para próxima tentativa de reabrir
            g_ultimo_underron_time = std::chrono::steady_clock::time_point{};
            g_ultimo_underron_count = 0;
            return false;
        }

        // Ainda dentro da janela de 3 s: atualiza tracking e espera
        g_ultimo_underron_count = frames_atual;
        g_ultimo_underron_time = agora;
        return false;
    }

    // Stream inativo: reseta tracking para próxima tentativa
    g_ultimo_underron_time = std::chrono::steady_clock::time_point{};
    g_ultimo_underron_count = 0;
    return false;
}

void phoenix_audio_retomar() {
    std::lock_guard<std::mutex> lock(g_stream_mutex);

    // A) g_stream nulo: retorna sem fazer nada.
    if (!g_stream) {
        return;
    }

    auto estadoAntes = g_stream->getState();

    // B) estado Started: retorna sem fazer nada.
    if (estadoAntes == oboe::StreamState::Started) {
        return;
    }

    // C) estado Pausing: espera até 200 ms por Paused.
    if (estadoAntes == oboe::StreamState::Pausing) {
        oboe::AudioStream *raw = g_stream.get();
        oboe::StreamState prev = oboe::StreamState::Pausing;
        auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(200);
        while (std::chrono::steady_clock::now() < deadline) {
            oboe::StreamState next = oboe::StreamState::Paused;
            oboe::Result res = raw->waitForStateChange(prev, &next, 50);
            if (res == oboe::Result::OK || next == oboe::StreamState::Paused) {
                break;
            }
            prev = next;
        }
    }

    // Releitura do estado após espera.
    estadoAntes = g_stream->getState();

    // SÓ reabra se Disconnected/Closed/Uninitialized.
    if (estadoAntes == oboe::StreamState::Disconnected ||
        estadoAntes == oboe::StreamState::Closed ||
        estadoAntes == oboe::StreamState::Uninitialized) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixPausa", "audio retomar: resultado=-1 estadoAntes=%d estadoDepois=-1 (reabrindo)", static_cast<int>(estadoAntes)); // TEMPORARIO: remover
        phoenix_audio_reabrir();
        return;
    }

    // Estado Paused ou Stopped: chama requestStart().
    oboe::Result r = g_stream->requestStart();
    int estadoDepois = static_cast<int>(g_stream->getState());
    __android_log_print(ANDROID_LOG_INFO, "PhoenixPausa", "audio retomar: resultado=%d estadoAntes=%d estadoDepois=%d", static_cast<int>(r), static_cast<int>(estadoAntes), estadoDepois); // TEMPORARIO: remover

    // Se requestStart() retornar erro, reabra o stream.
    if (r != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixPausa", "audio retomar: requestStart falhou (%d), reabrindo", static_cast<int>(r)); // TEMPORARIO: remover
        phoenix_audio_reabrir();
    }
}

void phoenix_audio_reset_falhas_consumo_normal() {
    uint64_t frames_atual = g_frames_consumidos.load(std::memory_order_relaxed);
    int64_t diff = static_cast<int64_t>(frames_atual) - static_cast<int64_t>(g_falhas_ultimo_delta);
    if (diff >= 96000) {
        g_falhas_consecutivas.store(0, std::memory_order_release);
        g_falhas_ultimo_delta = frames_atual;
    }
}

} // extern "C"
