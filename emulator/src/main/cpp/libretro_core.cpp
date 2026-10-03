#include "libretro_core.h"
#include "video_renderer.h"
#include "audio_output.h"
#include <android/log.h>
#include <dlfcn.h>
#include <sstream>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PhoenixCore", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PhoenixCore", __VA_ARGS__)

extern bool cb_environment(unsigned cmd, void *data);
extern int g_formato_pixel;
extern std::atomic<bool> g_pular_video;
extern std::atomic<int> g_ff_velocidade;

static void cb_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data == nullptr) return; // Repita o quadro anterior
    if (g_pular_video.load(std::memory_order_relaxed)) return;
    phoenix_video_desenhar_quadro(data, width, height, pitch);
}
static void cb_audio_sample(int16_t left, int16_t right) {
    if (g_ff_velocidade.load(std::memory_order_relaxed) > 1) return;
    int16_t frame[2] = {left, right};
    phoenix_audio_enviar(frame, 1);
}
static size_t cb_audio_sample_batch(const int16_t *data, size_t frames) {
    if (g_ff_velocidade.load(std::memory_order_relaxed) > 1) return frames;
    return phoenix_audio_enviar(data, frames);
}
static void cb_input_poll() {}

extern std::atomic<uint32_t> g_botoes[2];

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port > 1) return 0;
    if (device == RETRO_DEVICE_JOYPAD) {
        uint32_t mascara = g_botoes[port].load(std::memory_order_relaxed);
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
            return mascara;
        }
        return (mascara >> id) & 1;
    }
    return 0;
}

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

    handle_ = dlopen(caminhoDoSo.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle_ == nullptr) {
        LOGE("dlopen falhou: %s", dlerror());
        return false;
    }

    const bool ok =
        resolver(set_environment_, "retro_set_environment") &&
        resolver(set_video_refresh_, "retro_set_video_refresh") &&
        resolver(set_audio_sample_, "retro_set_audio_sample") &&
        resolver(set_audio_sample_batch_, "retro_set_audio_sample_batch") &&
        resolver(set_input_poll_, "retro_set_input_poll") &&
        resolver(set_input_state_, "retro_set_input_state") &&
        resolver(init_, "retro_init") &&
        resolver(deinit_, "retro_deinit") &&
        resolver(api_version_, "retro_api_version") &&
        resolver(get_system_info_, "retro_get_system_info") &&
        resolver(get_system_av_info_, "retro_get_system_av_info") &&
        resolver(set_controller_port_device_, "retro_set_controller_port_device") &&
        resolver(reset_, "retro_reset") &&
        resolver(run_, "retro_run") &&
        resolver(serialize_size_, "retro_serialize_size") &&
        resolver(serialize_, "retro_serialize") &&
        resolver(unserialize_, "retro_unserialize") &&
        resolver(cheat_reset_, "retro_cheat_reset") &&
        resolver(cheat_set_, "retro_cheat_set") &&
        resolver(load_game_, "retro_load_game") &&
        resolver(load_game_special_, "retro_load_game_special") &&
        resolver(unload_game_, "retro_unload_game") &&
        resolver(get_region_, "retro_get_region") &&
        resolver(get_memory_data_, "retro_get_memory_data") &&
        resolver(get_memory_size_, "retro_get_memory_size");

    if (!ok) {
        descarregar();
        return false;
    }

    if (api_version_() != 1) {
        LOGE("Versao de API do libretro nao suportada!");
        descarregar();
        return false;
    }

    LOGI("Nucleo carregado, API libretro %u", api_version_());
    return true;
}

bool LibretroCore::iniciar() {
    if (init_ == nullptr) return false;

    set_environment_(cb_environment);
    init_();

    set_video_refresh_(cb_video_refresh);
    set_audio_sample_(cb_audio_sample);
    set_audio_sample_batch_(cb_audio_sample_batch);
    set_input_poll_(cb_input_poll);
    set_input_state_(cb_input_state);

    return true;
}

void LibretroCore::rodarQuadro() {
    if (run_ != nullptr) run_();
}

bool LibretroCore::carregarJogo(const void* dados, size_t tamanho, const char* caminho) {
    if (dados == nullptr && tamanho == 0 && (caminho == nullptr || caminho[0] == '\0')) {
        return load_game_(nullptr);
    }
    retro_game_info info = {};
    info.path = caminho;
    info.data = dados;
    info.size = tamanho;
    info.meta = "";
    bool ok = load_game_(&info);
    if (ok && serialize_size_) {
        size_t sz = serialize_size_();
        LOGI("serialize_size: %zu", sz);
    }
    return ok;
}

std::string LibretroCore::obterInfo() {
    retro_system_info sysInfo = {};
    get_system_info_(&sysInfo);

    retro_system_av_info avInfo = {};
    get_system_av_info_(&avInfo);

    std::stringstream ss;
    ss << "Library: " << (sysInfo.library_name ? sysInfo.library_name : "N/A")
       << " v" << (sysInfo.library_version ? sysInfo.library_version : "N/A") << "\n";
    ss << "Resolution: " << avInfo.geometry.base_width << "x" << avInfo.geometry.base_height << "\n";
    ss << "FPS: " << avInfo.timing.fps << "\n";
    ss << "Audio Rate: " << avInfo.timing.sample_rate << "\n";

    std::string pixel_str = "Unknown";
    if (g_formato_pixel == 1) pixel_str = "XRGB8888";
    else if (g_formato_pixel == 2) pixel_str = "RGB565";
    else if (g_formato_pixel == 0) pixel_str = "0RGB1555";

    ss << "Pixel Format: " << pixel_str;

    return ss.str();
}

double LibretroCore::obterFps() {
    if (!get_system_av_info_) return 60.0;
    retro_system_av_info avInfo = {};
    get_system_av_info_(&avInfo);
    return avInfo.timing.fps;
}

float LibretroCore::obterAspectRatio() {
    if (!get_system_av_info_) return 4.0f / 3.0f;
    retro_system_av_info avInfo = {};
    get_system_av_info_(&avInfo);
    float aspect = avInfo.geometry.aspect_ratio;
    if (aspect <= 0.0f) {
        aspect = static_cast<float>(avInfo.geometry.base_width) /
                 static_cast<float>(avInfo.geometry.base_height);
    }
    return aspect;
}

double LibretroCore::obterSampleRate() {
    if (!get_system_av_info_) return 44100.0;
    retro_system_av_info avInfo = {};
    get_system_av_info_(&avInfo);
    return avInfo.timing.sample_rate;
}

bool LibretroCore::precisaDeFullPath() {
    if (!get_system_info_) return false;
    retro_system_info info = {};
    get_system_info_(&info);
    return info.need_fullpath;
}

void LibretroCore::carregarSram(const std::string &caminho) {
    if (!get_memory_size_ || !get_memory_data_) return;
    size_t size = get_memory_size_(RETRO_MEMORY_SAVE_RAM);
    void *data = get_memory_data_(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || data == nullptr) return;

    FILE *f = fopen(caminho.c_str(), "rb");
    if (f) {
        fread(data, 1, size, f);
        fclose(f);
    }
}

void LibretroCore::salvarSram(const std::string &caminho) {
    if (!get_memory_size_ || !get_memory_data_) return;
    size_t size = get_memory_size_(RETRO_MEMORY_SAVE_RAM);
    void *data = get_memory_data_(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || data == nullptr) return;

    FILE *f = fopen(caminho.c_str(), "wb");
    if (f) {
        fwrite(data, 1, size, f);
        fclose(f);
    }
}

void LibretroCore::reiniciar() {
    if (reset_) reset_();
}

void LibretroCore::definirPausa(bool /*pausado*/) {
    // A pausa real e controlada pelo laço em jni_bridge.cpp via g_pausado.
    // Esta funcao existe como ponto de extensao se o nucleo precisar
    // de callbacks (ex.: retro_pause / retro_unpause).
}

size_t LibretroCore::tamanhoEstado() const {
    if (!serialize_size_) return 0;
    size_t sz = serialize_size_();
    return sz;
}

bool LibretroCore::salvarEstado(void *buf, size_t tam) const {
    if (!serialize_ || !serialize_size_) return false;
    size_t sz = serialize_size_();
    if (sz == 0 || sz > tam || buf == nullptr) return false;
    return serialize_(buf, tam) ? true : false;
}

bool LibretroCore::carregarEstado(const void *buf, size_t tam) const {
    if (!unserialize_ || !serialize_size_) return false;
    size_t sz = serialize_size_();
    if (sz == 0 || sz > tam || buf == nullptr) return false;
    return unserialize_(buf, tam) ? true : false;
}

void LibretroCore::descarregar() {
    if (handle_ == nullptr) return;
    if (deinit_ != nullptr) deinit_();

    dlclose(handle_);
    handle_ = nullptr;

    set_environment_ = nullptr;
    set_video_refresh_ = nullptr;
    set_audio_sample_ = nullptr;
    set_audio_sample_batch_ = nullptr;
    set_input_poll_ = nullptr;
    set_input_state_ = nullptr;
    init_ = nullptr;
    deinit_ = nullptr;
    api_version_ = nullptr;
    get_system_info_ = nullptr;
    get_system_av_info_ = nullptr;
    set_controller_port_device_ = nullptr;
    reset_ = nullptr;
    run_ = nullptr;
    serialize_size_ = nullptr;
    serialize_ = nullptr;
    unserialize_ = nullptr;
    cheat_reset_ = nullptr;
    cheat_set_ = nullptr;
    load_game_ = nullptr;
    load_game_special_ = nullptr;
    unload_game_ = nullptr;
    get_region_ = nullptr;
    get_memory_data_ = nullptr;
    get_memory_size_ = nullptr;
}

} // namespace phoenix
