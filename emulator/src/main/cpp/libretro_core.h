#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include "libretro.h"

namespace phoenix {

class LibretroCore {
public:
    LibretroCore() = default;
    ~LibretroCore();

    LibretroCore(const LibretroCore &) = delete;
    LibretroCore &operator=(const LibretroCore &) = delete;

    /** dlopen + resolucao da tabela de simbolos. */
    bool carregar(const std::string &caminhoDoSo);

    /** Instala os callbacks (video, audio, input, environment) e chama retro_init. */
    bool iniciar();

    /** Um quadro. Chamada SEMPRE pela thread do emulador, nunca pela UI. */
    void rodarQuadro();

    void descarregar();

    bool carregado() const { return handle_ != nullptr; }

    bool carregarJogo(const void* dados, size_t tamanho, const char* caminho);

    std::string obterInfo();
    double obterFps();
    float obterAspectRatio();
    double obterSampleRate();

    bool precisaDeFullPath();
    void carregarSram(const std::string &caminho);
    void salvarSram(const std::string &caminho);

    void reiniciar();
    void definirPausa(bool pausado);

    /** Tamanho do estado de save-state (0 se indisponivel). */
    size_t tamanhoEstado() const;
    /** Salva estado em buf (tam deve ser >= tamanhoEstado()). Retorna true se ok. */
    bool salvarEstado(void *buf, size_t tam) const;
    /** Carrega estado a partir de buf (tam bytes). Retorna true se ok. */
    bool carregarEstado(const void *buf, size_t tam) const;

private:
    void *handle_ = nullptr;

    decltype(&retro_set_environment) set_environment_ = nullptr;
    decltype(&retro_set_video_refresh) set_video_refresh_ = nullptr;
    decltype(&retro_set_audio_sample) set_audio_sample_ = nullptr;
    decltype(&retro_set_audio_sample_batch) set_audio_sample_batch_ = nullptr;
    decltype(&retro_set_input_poll) set_input_poll_ = nullptr;
    decltype(&retro_set_input_state) set_input_state_ = nullptr;
    decltype(&retro_init) init_ = nullptr;
    decltype(&retro_deinit) deinit_ = nullptr;
    decltype(&retro_api_version) api_version_ = nullptr;
    decltype(&retro_get_system_info) get_system_info_ = nullptr;
    decltype(&retro_get_system_av_info) get_system_av_info_ = nullptr;
    decltype(&retro_set_controller_port_device) set_controller_port_device_ = nullptr;
    decltype(&retro_reset) reset_ = nullptr;
    decltype(&retro_run) run_ = nullptr;
    decltype(&retro_serialize_size) serialize_size_ = nullptr;
    decltype(&retro_serialize) serialize_ = nullptr;
    decltype(&retro_unserialize) unserialize_ = nullptr;
    decltype(&retro_cheat_reset) cheat_reset_ = nullptr;
    decltype(&retro_cheat_set) cheat_set_ = nullptr;
    decltype(&retro_load_game) load_game_ = nullptr;
    decltype(&retro_load_game_special) load_game_special_ = nullptr;
    decltype(&retro_unload_game) unload_game_ = nullptr;
    decltype(&retro_get_region) get_region_ = nullptr;
    decltype(&retro_get_memory_data) get_memory_data_ = nullptr;
    decltype(&retro_get_memory_size) get_memory_size_ = nullptr;

    template <typename T>
    bool resolver(T &destino, const char *nome);
};

} // namespace phoenix
