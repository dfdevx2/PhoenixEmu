/**
 * Ponte JNI.
 *
 * ESTADO: esqueleto (Fase 4a).
 *
 * Regra que vale para tudo aqui: a thread do emulador e nativa e dedicada.
 * Ela chama retro_run() num laco proprio e NUNCA toca em Compose. A
 * comunicacao de volta acontece por estado compartilhado (o bitmask de
 * input, o ring buffer de audio) ou por postagem no looper da UI.
 */
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>
#include <thread>
#include <chrono>
#include <vector>
#include <android/native_window_jni.h>

#include "libretro_core.h"
#include "video_renderer.h"
#include "audio_output.h"

std::atomic<uint32_t> g_botoes[2] = {};
std::atomic<bool> g_pedido_reset{false};
std::string g_sram_path;
std::atomic<bool> g_pedido_salvar_sram{false};

std::atomic<int> g_ff_velocidade{1};
std::atomic<bool> g_pular_video{false};

std::atomic<float> g_stats_fps{0.0f};
std::atomic<float> g_stats_ms_medio{0.0f};
std::atomic<float> g_stats_ms_max{0.0f};

namespace {

phoenix::LibretroCore g_nucleo;
std::thread g_thread_emulador;
std::atomic<bool> g_rodando{false};
ANativeWindow* g_janela = nullptr;

std::string paraStdString(JNIEnv *env, jstring texto) {
    if (texto == nullptr) return {};
    const char *bruto = env->GetStringUTFChars(texto, nullptr);
    std::string resultado(bruto != nullptr ? bruto : "");
    if (bruto != nullptr) env->ReleaseStringUTFChars(texto, bruto);
    return resultado;
}

void lacoEmulador() {
    using clock = std::chrono::steady_clock;
    double fps = g_nucleo.obterFps();
    if (fps <= 0.0) fps = 60.0;
    double sample_rate = g_nucleo.obterSampleRate();
    if (sample_rate <= 0.0) sample_rate = 44100.0;

    phoenix_audio_inicializar(sample_rate, fps);

    size_t quadros_minimos = phoenix_audio_tamanho_minimo();
    constexpr int ALVO_QUADROS = 2;
    bool stream_iniciado = false;
    int ff_anterior = 1;

    auto proximo_relato = clock::now() + std::chrono::seconds(1);
    auto intervalo = std::chrono::nanoseconds(static_cast<long long>(1'000'000'000.0 / fps));
    auto proximo_quadro = clock::now();

    int quadros_contados = 0;
    float tempo_acumulado_ms = 0.0f;
    float tempo_max_ms = 0.0f;

    while (g_rodando.load(std::memory_order_acquire)) {
        if (g_pedido_reset.exchange(false, std::memory_order_relaxed)) {
            g_nucleo.reiniciar();
        }

        if (g_pedido_salvar_sram.exchange(false, std::memory_order_relaxed)) {
            if (!g_sram_path.empty()) {
                g_nucleo.salvarSram(g_sram_path);
            }
        }

        int vel = g_ff_velocidade.load(std::memory_order_relaxed);
        if (vel != ff_anterior) {
            if (ff_anterior > 1 && vel == 1) {
                __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", "ff: terminou");
            }
            ff_anterior = vel;
            proximo_quadro = clock::now();
        }

        if (vel > 1) {
            auto agora_inicio_ff = clock::now();
            if (agora_inicio_ff >= proximo_quadro) {
                for (int i = 0; i < vel; ++i) {
                    g_pular_video.store(i != vel - 1, std::memory_order_relaxed);
                    auto t0 = clock::now();
                    g_nucleo.rodarQuadro();
                    auto t1 = clock::now();

                    float ms = std::chrono::duration<float, std::milli>(t1 - t0).count();
                    tempo_acumulado_ms += ms;
                    if (ms > tempo_max_ms) tempo_max_ms = ms;
                    quadros_contados++;
                }
                g_pular_video.store(false, std::memory_order_relaxed);
                proximo_quadro += intervalo;
            } else {
                std::this_thread::sleep_until(proximo_quadro);
            }
        } else {
            if (phoenix_audio_ativo()) {
                if (!stream_iniciado) {
                    if (phoenix_audio_ocupacao() >= ALVO_QUADROS * quadros_minimos) {
                        phoenix_audio_iniciar_stream();
                        stream_iniciado = true;
                    }
                }

                if (phoenix_audio_ocupacao() < ALVO_QUADROS * quadros_minimos) {
                    auto t0 = clock::now();
                    g_nucleo.rodarQuadro();
                    auto t1 = clock::now();

                    float ms = std::chrono::duration<float, std::milli>(t1 - t0).count();
                    tempo_acumulado_ms += ms;
                    if (ms > tempo_max_ms) tempo_max_ms = ms;
                    quadros_contados++;

                    proximo_quadro = clock::now();
                } else {
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                }
            } else {
                auto t0 = clock::now();
                g_nucleo.rodarQuadro();
                auto t1 = clock::now();

                float ms = std::chrono::duration<float, std::milli>(t1 - t0).count();
                tempo_acumulado_ms += ms;
                if (ms > tempo_max_ms) tempo_max_ms = ms;
                quadros_contados++;

                proximo_quadro += intervalo;
                std::this_thread::sleep_until(proximo_quadro);
            }
        }

        auto agora = clock::now();
        if (agora >= proximo_relato) {
            if (stream_iniciado && phoenix_audio_ativo()) {
                phoenix_audio_relatar();
            }

            g_stats_fps.store(static_cast<float>(quadros_contados), std::memory_order_relaxed);
            g_stats_ms_medio.store(quadros_contados > 0 ? tempo_acumulado_ms / quadros_contados : 0.0f, std::memory_order_relaxed);
            g_stats_ms_max.store(tempo_max_ms, std::memory_order_relaxed);

            quadros_contados = 0;
            tempo_acumulado_ms = 0.0f;
            tempo_max_ms = 0.0f;

            proximo_relato = agora + std::chrono::seconds(1);
        }
    }

    phoenix_audio_finalizar();
}

} // namespace

extern std::string g_system_dir;
extern std::string g_saves_dir;

extern "C" {

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeDefinirPastas(
    JNIEnv *env, jobject /*thiz*/, jstring sistema, jstring saves) {
    g_system_dir = paraStdString(env, sistema);
    g_saves_dir = paraStdString(env, saves);
}

JNIEXPORT jstring JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeInfo(
    JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(g_nucleo.obterInfo().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregar(
    JNIEnv *env, jobject /*thiz*/, jstring caminhoDoSo) {
    const std::string caminho = paraStdString(env, caminhoDoSo);
    if (!g_nucleo.carregar(caminho)) return JNI_FALSE;
    return g_nucleo.iniciar() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregarJogo(
    JNIEnv *env, jobject /*thiz*/, jbyteArray rom, jstring caminhoDoJogo) {
    const std::string caminhoStr = paraStdString(env, caminhoDoJogo);
    const char* caminho = caminhoStr.empty() ? nullptr : caminhoStr.c_str();

    if (rom == nullptr) {
        return g_nucleo.carregarJogo(nullptr, 0, caminho) ? JNI_TRUE : JNI_FALSE;
    }
    jsize tamanho = env->GetArrayLength(rom);
    void *dados = env->GetPrimitiveArrayCritical(rom, nullptr);
    bool ok = g_nucleo.carregarJogo(dados, static_cast<size_t>(tamanho), caminho);
    env->ReleasePrimitiveArrayCritical(rom, dados, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativePrecisaDeFullPath(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return g_nucleo.precisaDeFullPath() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregarSram(
    JNIEnv *env, jobject /*thiz*/, jstring caminho) {
    g_nucleo.carregarSram(paraStdString(env, caminho));
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeSalvarSram(
    JNIEnv *env, jobject /*thiz*/, jstring caminho) {
    g_nucleo.salvarSram(paraStdString(env, caminho));
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeDefinirCaminhoSram(
    JNIEnv *env, jobject /*thiz*/, jstring caminho) {
    g_sram_path = paraStdString(env, caminho);
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativePedirSalvarSram(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_pedido_salvar_sram.store(true, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeIniciarLaco(
    JNIEnv *env, jobject /*thiz*/, jobject surface) {
    if (g_rodando.load(std::memory_order_acquire)) return;

    if (surface != nullptr) {
        g_janela = ANativeWindow_fromSurface(env, surface);
        phoenix_video_inicializar(g_janela);
    }

    g_rodando.store(true, std::memory_order_release);
    g_thread_emulador = std::thread(lacoEmulador);
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativePararLaco(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    if (!g_rodando.load(std::memory_order_acquire)) return;

    g_rodando.store(false, std::memory_order_release);
    if (g_thread_emulador.joinable()) {
        g_thread_emulador.join();
    }

    phoenix_video_finalizar();
    if (g_janela != nullptr) {
        ANativeWindow_release(g_janela);
        g_janela = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeDefinirBotoes(
    JNIEnv * /*env*/, jobject /*thiz*/, jint porta, jint mascara) {
    if (porta < 0 || porta > 1) return;
    g_botoes[porta].store(static_cast<uint32_t>(mascara), std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeDescarregar(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_nucleo.descarregar();
}

JNIEXPORT jfloat JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeObterAspectRatio(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return g_nucleo.obterAspectRatio();
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeReiniciar(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_pedido_reset.store(true, std::memory_order_relaxed);
}

JNIEXPORT jint JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeTamanhoEstado(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_rodando.load(std::memory_order_acquire)) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", "estado: laço rodando");
        return 0;
    }
    size_t sz = g_nucleo.tamanhoEstado();
    return static_cast<jint>(sz);
}

JNIEXPORT jbyteArray JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeSalvarEstado(
    JNIEnv *env, jobject /*thiz*/) {
    if (g_rodando.load(std::memory_order_acquire)) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", "estado: laço rodando");
        return nullptr;
    }
    size_t sz = g_nucleo.tamanhoEstado();
    if (sz == 0) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(sz));
    if (!arr) return nullptr;
    std::vector<jbyte> buf(sz);
    if (!g_nucleo.salvarEstado(buf.data(), sz)) {
        env->DeleteLocalRef(arr);
        return nullptr;
    }
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(sz), buf.data());
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregarEstado(
    JNIEnv *env, jobject /*thiz*/, jbyteArray dados) {
    if (g_rodando.load(std::memory_order_acquire)) {
        __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", "estado: laço rodando");
        return JNI_FALSE;
    }
    if (!dados) return JNI_FALSE;
    jsize tam = env->GetArrayLength(dados);
    if (tam == 0) return JNI_FALSE;
    std::vector<jbyte> buf(tam);
    env->GetByteArrayRegion(dados, 0, tam, buf.data());
    return g_nucleo.carregarEstado(buf.data(), static_cast<size_t>(tam)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeDefinirAvancoRapido(
    JNIEnv * /*env*/, jobject /*thiz*/, jint velocidade) {
    g_ff_velocidade.store(velocidade, std::memory_order_relaxed);
}

JNIEXPORT jfloatArray JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeObterStats(
    JNIEnv *env, jobject /*thiz*/) {
    jfloatArray arr = env->NewFloatArray(3);
    if (arr) {
        float stats[3];
        stats[0] = g_stats_fps.load(std::memory_order_relaxed);
        stats[1] = g_stats_ms_medio.load(std::memory_order_relaxed);
        stats[2] = g_stats_ms_max.load(std::memory_order_relaxed);
        env->SetFloatArrayRegion(arr, 0, 3, stats);
    }
    return arr;
}

} // extern "C"
