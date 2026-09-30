/*
 * ============================================================================
 * VERIFICADOR 2 -- saude do buffer de audio
 * ============================================================================
 *
 * Por que existe: "o som ta estalando" nao e diagnostico. Com este contador o
 * relato vira "14 underruns em 60 s, ocupacao media 38%", e ai da para saber
 * se o buffer e pequeno, se o nucleo esta lento ou se o pacing esta errado --
 * tres causas diferentes com o MESMO sintoma no ouvido.
 *
 * Underrun  = o Oboe pediu amostras e o buffer nao tinha. Vira estalo.
 * Overrun   = o nucleo produziu mais rapido que o consumo e o buffer encheu.
 *             Vira atraso crescente entre imagem e som, e depois descarte.
 *
 * Um app saudavel, rodando o nucleo falso por 60 s, tem de marcar ZERO dos
 * dois. Qualquer numero diferente de zero e bug, nao "e assim mesmo".
 *
 * Uso:
 *   audio_saude_iniciar();
 *   ... no produtor:  audio_saude_produziu(n_quadros, ocupacao, capacidade);
 *   ... no consumidor: audio_saude_consumiu(pedidos, entregues);
 *   ... uma vez por segundo: audio_saude_relatar();
 */
#ifndef VERIFICADOR_AUDIO_H
#define VERIFICADOR_AUDIO_H

#include <android/log.h>
#include <stdint.h>
#include <time.h>

#ifndef AUDIO_TAG
#define AUDIO_TAG "PhoenixAudio"
#endif

typedef struct {
   uint64_t underruns;       /* pediu e nao tinha                        */
   uint64_t overruns;        /* produziu e nao coube                     */
   uint64_t quadros_prod;
   uint64_t quadros_cons;
   uint64_t amostras_perdidas;
   double   soma_ocupacao;   /* para a media                             */
   uint64_t leituras;
   int64_t  t0_ms;
} AudioSaude;

static AudioSaude audio_saude;

static int64_t audio_saude_agora_ms(void) {
   struct timespec ts;
   clock_gettime(CLOCK_MONOTONIC, &ts);
   return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void audio_saude_iniciar(void) {
   AudioSaude z = {0};
   audio_saude = z;
   audio_saude.t0_ms = audio_saude_agora_ms();
}

/* Chame depois de escrever no ring buffer. `coube` e quantos quadros
 * realmente entraram -- se for menor que `quadros`, houve overrun. */
static void audio_saude_produziu(uint32_t quadros, uint32_t coube,
                                 uint32_t ocupacao, uint32_t capacidade) {
   audio_saude.quadros_prod += quadros;
   if (coube < quadros) {
      audio_saude.overruns++;
      audio_saude.amostras_perdidas += (quadros - coube);
   }
   if (capacidade > 0) {
      audio_saude.soma_ocupacao += (double)ocupacao / (double)capacidade;
      audio_saude.leituras++;
   }
}

/* Chame dentro do callback do Oboe, depois de ler do ring buffer. */
static void audio_saude_consumiu(uint32_t pedidos, uint32_t entregues) {
   audio_saude.quadros_cons += entregues;
   if (entregues < pedidos) audio_saude.underruns++;
}

static void audio_saude_relatar(void) {
   int64_t agora = audio_saude_agora_ms();
   double  seg   = (double)(agora - audio_saude.t0_ms) / 1000.0;
   double  ocup  = audio_saude.leituras
                 ? (audio_saude.soma_ocupacao / (double)audio_saude.leituras) * 100.0
                 : 0.0;
   /* Producao e consumo tem de andar juntos. Uma deriva que CRESCE com o
    * tempo e o sinal de que a taxa do nucleo e a do dispositivo nao batem --
    * o bug mais chato desta fase, porque soa bem nos primeiros segundos. */
   int64_t deriva = (int64_t)audio_saude.quadros_prod - (int64_t)audio_saude.quadros_cons;

   __android_log_print(
      audio_saude.underruns || audio_saude.overruns ? ANDROID_LOG_WARN : ANDROID_LOG_INFO,
      AUDIO_TAG,
      "%.0fs | underruns=%llu overruns=%llu | ocupacao media %.0f%% | "
      "deriva %lld quadros (%.1f ms) | perdidas=%llu",
      seg,
      (unsigned long long)audio_saude.underruns,
      (unsigned long long)audio_saude.overruns,
      ocup,
      (long long)deriva,
      (double)deriva / 48.0,
      (unsigned long long)audio_saude.amostras_perdidas);
}

#endif /* VERIFICADOR_AUDIO_H */
