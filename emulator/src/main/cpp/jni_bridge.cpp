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

#include <atomic>
#include <string>
#include <thread>
#include <chrono>
#include <android/native_window_jni.h>

#include "libretro_core.h"
#include "video_renderer.h"
#include "audio_output.h"

std::atomic<uint32_t> g_botoes[2] = {};
std::atomic<bool> g_pedido_reset{false};
std::string g_sram_path;
std::atomic<bool> g_pedido_salvar_sram{false};

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

    auto proximo_relato = clock::now() + std::chrono::seconds(1);
    auto intervalo = std::chrono::nanoseconds(static_cast<long long>(1'000'000'000.0 / fps));
    auto proximo_quadro = clock::now();

    while (g_rodando.load(std::memory_order_acquire)) {
        if (g_pedido_reset.exchange(false, std::memory_order_relaxed)) {
            g_nucleo.reiniciar();
        }

        if (g_pedido_salvar_sram.exchange(false, std::memory_order_relaxed)) {
            if (!g_sram_path.empty()) {
                g_nucleo.salvarSram(g_sram_path);
            }
        }

        if (phoenix_audio_ativo()) {
            if (!stream_iniciado) {
                if (phoenix_audio_ocupacao() >= ALVO_QUADROS * quadros_minimos) {
                    phoenix_audio_iniciar_stream();
                    stream_iniciado = true;
                }
            }

            if (phoenix_audio_ocupacao() < ALVO_QUADROS * quadros_minimos) {
                g_nucleo.rodarQuadro();
                proximo_quadro = clock::now();
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            }
        } else {
            // Fallback para relogio (stream falhou ou desconectou)
            g_nucleo.rodarQuadro();
            proximo_quadro += intervalo;
            std::this_thread::sleep_until(proximo_quadro);
        }

        auto agora = clock::now();
        if (agora >= proximo_relato) {
            if (stream_iniciado && phoenix_audio_ativo()) {
                phoenix_audio_relatar();
            }
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

} // extern "C"
