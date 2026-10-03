#pragma once
#include <cstdint>
#include <cstddef>

extern "C" {
    void phoenix_audio_inicializar(double sample_rate, double fps);
    void phoenix_audio_iniciar_stream();
    size_t phoenix_audio_enviar(const int16_t* dados, size_t quadros);
    void phoenix_audio_finalizar();
    size_t phoenix_audio_espaco_livre();
    size_t phoenix_audio_ocupacao();
    bool phoenix_audio_ativo();
    void phoenix_audio_pausar();
    void phoenix_audio_retomar();
    void phoenix_audio_flush_ring_buffer();
    size_t phoenix_audio_tamanho_minimo();
    void phoenix_audio_relatar();
}
