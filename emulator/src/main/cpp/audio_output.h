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
    bool phoenix_audio_audio_morto();
    void phoenix_audio_reiniciar_saude();
    bool phoenix_audio_verificar_recuperacao();
    void phoenix_audio_reset_falhas_consumo_normal();
    bool phoenix_audio_tentar_reabrir_se_morto();
    void phoenix_audio_definir_volume(float volume);
}
