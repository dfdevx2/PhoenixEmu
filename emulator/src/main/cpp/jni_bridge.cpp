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

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregar(
    JNIEnv *env, jobject /*thiz*/, jstring caminhoDoSo) {
    const std::string caminho = paraStdString(env, caminhoDoSo);
    if (!g_nucleo.carregar(caminho)) return JNI_FALSE;
    return g_nucleo.iniciar() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dfdx047_phoenixemu_emulator_NucleoLibretro_nativeCarregarJogo(
    JNIEnv * /*env*/, jobject /*thiz*/, jbyteArray /*rom*/) {
    // TODO (Fase 4a): montar retro_game_info com os bytes recebidos.
    //
    // A ROM vem do Kotlin ja lida (e descompactada, se for zip), porque o
    // lado nativo nao tem como abrir uma URI do SAF. Se o nucleo declarar
    // need_fullpath, copiamos para o cache e passamos o caminho.
    return JNI_FALSE;
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
