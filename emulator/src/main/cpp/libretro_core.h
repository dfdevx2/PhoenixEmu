#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

/**
 * Carregador de nucleo libretro.
 *
 * Os nucleos sao .so independentes, carregados com dlopen e consultados com
 * dlsym. Esta classe existe para que o resto do codigo nunca toque em
 * ponteiros crus: ela resolve a tabela de funcoes uma vez e falha cedo se
 * algo estiver faltando.
 *
 * ESTADO: esqueleto. Assinaturas prontas, corpos por escrever (Fase 4a).
 *
 * PRE-REQUISITO: coloque o `libretro.h` oficial nesta pasta. Nao o incluo
 * aqui de proposito -- e um header de terceiros, e a copia certa e a da
 * versao do nucleo que voce for usar:
 *   https://github.com/libretro/libretro-common/blob/master/include/libretro.h
 */
namespace phoenix {

// Tipos das funcoes obrigatorias da API libretro. Quando o libretro.h estiver
// na pasta, troque estes typedefs pelos tipos reais do header.
using retro_init_t = void (*)();
using retro_deinit_t = void (*)();
using retro_api_version_t = unsigned (*)();
using retro_run_t = void (*)();
using retro_reset_t = void (*)();
using retro_unload_game_t = void (*)();
using retro_serialize_size_t = size_t (*)();

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

private:
    void *handle_ = nullptr;

    retro_init_t init_ = nullptr;
    retro_deinit_t deinit_ = nullptr;
    retro_api_version_t apiVersion_ = nullptr;
    retro_run_t run_ = nullptr;
    retro_reset_t reset_ = nullptr;
    retro_serialize_size_t serializeSize_ = nullptr;

    template <typename T>
    bool resolver(T &destino, const char *nome);
};

} // namespace phoenix
