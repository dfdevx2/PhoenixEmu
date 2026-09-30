#include "libretro_core.h"

#include <android/log.h>
#include <dlfcn.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PhoenixCore", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PhoenixCore", __VA_ARGS__)

namespace phoenix {

LibretroCore::~LibretroCore() { descarregar(); }

template <typename T>
bool LibretroCore::resolver(T &destino, const char *nome) {
    destino = reinterpret_cast<T>(dlsym(handle_, nome));
    if (destino == nullptr) {
        LOGE("Simbolo ausente no nucleo: %s", nome);
        return false;
    }
    return true;
}

bool LibretroCore::carregar(const std::string &caminhoDoSo) {
    descarregar();

    // RTLD_LOCAL de proposito: os nucleos libretro tem simbolos globais com
    // nomes iguais entre si. Com RTLD_GLOBAL, carregar um segundo nucleo no
    // mesmo processo os faria colidir em silencio.
    handle_ = dlopen(caminhoDoSo.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle_ == nullptr) {
        LOGE("dlopen falhou: %s", dlerror());
        return false;
    }

    const bool ok =
        resolver(init_, "retro_init") &&
        resolver(deinit_, "retro_deinit") &&
        resolver(apiVersion_, "retro_api_version") &&
        resolver(run_, "retro_run") &&
        resolver(reset_, "retro_reset") &&
        resolver(serializeSize_, "retro_serialize_size");

    if (!ok) {
        descarregar();
        return false;
    }

    LOGI("Nucleo carregado, API libretro %u", apiVersion_());
    return true;
}

bool LibretroCore::iniciar() {
    // TODO (Fase 4a): instalar os callbacks antes de retro_init.
    //   retro_set_environment  -> minimo viavel para Mesen e Mesen-S:
    //       SET_PIXEL_FORMAT, GET_SYSTEM_DIRECTORY, GET_SAVE_DIRECTORY,
    //       GET_VARIABLE / SET_VARIABLES, GET_LOG_INTERFACE, GET_CAN_DUPE,
    //       SET_GEOMETRY
    //   retro_set_video_refresh -> video_renderer
    //   retro_set_audio_sample_batch -> audio_output (ring buffer)
    //   retro_set_input_poll / retro_set_input_state -> input_state
    if (init_ == nullptr) return false;
    init_();
    return true;
}

void LibretroCore::rodarQuadro() {
    if (run_ != nullptr) run_();
}

void LibretroCore::descarregar() {
    if (handle_ == nullptr) return;
    if (deinit_ != nullptr) deinit_();

    // dlclose nao limpa de forma confiavel o estado GLOBAL de um nucleo
    // libretro. E por isso que a EmulatorActivity roda em processo separado
    // (:emu): encerrar o processo e a unica garantia de sessao limpa.
    dlclose(handle_);
    handle_ = nullptr;
    init_ = nullptr;
    deinit_ = nullptr;
    apiVersion_ = nullptr;
    run_ = nullptr;
    reset_ = nullptr;
    serializeSize_ = nullptr;
}

} // namespace phoenix
