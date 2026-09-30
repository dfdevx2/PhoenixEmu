/**
 * Áudio: Oboe em modo de baixa latencia.
 *
 * ESTADO: esqueleto (Fase 4b).
 *
 * O ponto que costuma ser subestimado: o NES roda a ~60,1 Hz e o SNES a
 * ~60,09 Hz, enquanto as telas rodam a 60, 90 ou 120 Hz exatos. Sem
 * correcao, ou o buffer de audio esvazia (estalos) ou enche (atraso
 * crescente). A solucao e sincronizar PELO AUDIO: o nivel de preenchimento
 * do ring buffer guia o ritmo do loop de emulacao, com um ajuste dinamico e
 * pequeno da taxa de amostragem.
 *
 * O ring buffer e SPSC (um produtor, um consumidor) e lock-free: a thread do
 * emulador escreve, o callback do Oboe le. Um mutex aqui geraria bloqueio na
 * thread de audio, que e exatamente o que nao pode acontecer.
 */
#include <android/log.h>
#include <cstddef>
#include <cstdint>

extern "C" {

void phoenix_audio_inicializar(int /*taxaDoNucleo*/) {
    // TODO: AudioStreamBuilder com PerformanceMode::LowLatency,
    //       SharingMode::Exclusive, callback lendo do ring buffer.
}

size_t phoenix_audio_enviar(const int16_t * /*dados*/, size_t quadros) {
    // TODO: escrever no ring buffer; devolve quantos quadros couberam.
    return quadros;
}

void phoenix_audio_finalizar() {
    // TODO: parar e fechar o stream.
}

} // extern "C"
