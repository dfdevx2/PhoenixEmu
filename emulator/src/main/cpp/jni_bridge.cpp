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

#include "libretro_core.h"

namespace {

phoenix::LibretroCore g_nucleo;

/**
 * Estado dos botoes por porta, atomico.
 *
 * KeyEvent e MotionEvent chegam na thread da UI e escrevem aqui; o nucleo le
 * uma vez por quadro, na thread do emulador. Um inteiro atomico e suficiente
 * e nao custa bloqueio nenhum dos dois lados.
 */
std::atomic<uint32_t> g_botoes[2] = {};

std::string paraStdString(JNIEnv *env, jstring texto) {
    if (texto == nullptr) return {};
    const char *bruto = env->GetStringUTFChars(texto, nullptr);
    std::string resultado(bruto != nullptr ? bruto : "");
    if (bruto != nullptr) env->ReleaseStringUTFChars(texto, bruto);
    return resultado;
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
    JNIEnv *env, jobject /*thiz*/, jbyteArray rom) {
    if (rom == nullptr) {
        return g_nucleo.carregarJogo(nullptr, 0, nullptr) ? JNI_TRUE : JNI_FALSE;
    }
    jsize tamanho = env->GetArrayLength(rom);
    void *dados = env->GetPrimitiveArrayCritical(rom, nullptr);
    bool ok = g_nucleo.carregarJogo(dados, static_cast<size_t>(tamanho), "");
    env->ReleasePrimitiveArrayCritical(rom, dados, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeIniciarLaco(
    JNIEnv * /*env*/, jobject /*thiz*/, jobject /*surface*/) {
    // TODO (Fase 4a): ANativeWindow_fromSurface + criar a thread do emulador.
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativePararLaco(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    // TODO: sinalizar a thread, aguardar join, liberar a janela.
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

} // extern "C"
