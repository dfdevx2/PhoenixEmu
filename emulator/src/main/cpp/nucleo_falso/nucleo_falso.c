/*
 * ============================================================================
 * NUCLEO FALSO -- o juiz da Fase 4
 * ============================================================================
 *
 * Isto NAO emula nada. E um nucleo libretro que finge ser um emulador e emite
 * de proposito coisas que voce conhece de cor, para que um erro do frontend
 * deixe de ser silencioso.
 *
 * Os bugs classicos desta fase nao aparecem em build nenhum -- aparecem duas
 * semanas depois, jogando, sem pista da causa. Com este nucleo eles aparecem
 * em cinco segundos e dizem qual e:
 *
 *   SINTOMA NA TELA/ALTO-FALANTE          O QUE ESTA ERRADO NO FRONTEND
 *   -------------------------------------  -----------------------------------
 *   cores trocadas (vermelho vira azul)    formato de pixel
 *   imagem torta na diagonal               pitch (bytes por linha) ignorado
 *   borda branca incompleta ou cortada     geometria / recorte
 *   contador pula numeros                  quadros sendo perdidos
 *   contador repete o mesmo numero          quadros duplicados
 *   tom desafinado (nao e La)              taxa de amostragem errada
 *   estalos no som                          buffer de audio furando
 *   flash e clique em momentos diferentes   video e audio fora de sincronia
 *
 * Compila para o host (Linux x86-64) e para Android arm64 sem mudanca.
 * Nao depende de nada alem da libc e de -lm.
 *
 * O formato de pixel e escolhido aqui embaixo. O Mesen usa XRGB8888, que e o
 * padrao deste arquivo; recompile com -DFORMATO_RGB565 para exercitar o outro
 * caminho, que e o que a maioria dos nucleos de SNES usa.
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include <stdint.h>
#include "libretro.h"

/* M_PI nao e padrao C: com -std=c99 estrito (glibc e bionic) ele some, e o
 * erro aparece como "M_PI undeclared" no meio de um build que nao tem nada a
 * ver com audio. */
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* ----------------------------------------------------------------- formato */
#ifdef FORMATO_RGB565
   typedef uint16_t pixel_t;
   #define FORMATO_ESCOLHIDO RETRO_PIXEL_FORMAT_RGB565
   #define NOME_DO_FORMATO   "RGB565"
   /* 5 bits R, 6 G, 5 B */
   static inline pixel_t cor(unsigned r, unsigned g, unsigned b) {
      return (pixel_t)(((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
   }
#else
   typedef uint32_t pixel_t;
   #define FORMATO_ESCOLHIDO RETRO_PIXEL_FORMAT_XRGB8888
   #define NOME_DO_FORMATO   "XRGB8888"
   static inline pixel_t cor(unsigned r, unsigned g, unsigned b) {
      return (pixel_t)((r << 16) | (g << 8) | b);
   }
#endif

/* -------------------------------------------------------------- geometria
 *
 * 320x240 a 60 Hz e 48000 Hz. Numeros redondos de proposito: 48000/60 da
 * exatamente 800 amostras por quadro, sem resto. Se o seu frontend entregar
 * 799 ou 801, o erro e dele, nao de arredondamento meu.
 */
#define LARGURA    320
#define ALTURA     240
#define FPS        60.0
#define TAXA_AUDIO 48000.0
#define AMOSTRAS_POR_QUADRO ((unsigned)(TAXA_AUDIO / FPS))   /* 800 */

/* O tom e um La de 440 Hz. Desafinou, a taxa esta errada. */
#define FREQ_TOM 440.0

static pixel_t  *quadro;
static int16_t  *audio;
static unsigned  contador;
static double    fase;

static retro_video_refresh_t        cb_video;
static retro_audio_sample_t         cb_audio1;
static retro_audio_sample_batch_t   cb_audio_lote;
static retro_input_poll_t           cb_poll;
static retro_input_state_t          cb_estado;
static retro_environment_t          cb_ambiente;

/* ------------------------------------------------------------------ fonte
 *
 * Digitos 3x5. Existe para o contador de quadros ser LIDO, nao estimado:
 * "pulou de 412 para 414" e um fato; "parece que engasgou" nao e.
 */
static const unsigned char FONTE[10][5] = {
   {7,5,5,5,7}, {2,6,2,2,7}, {7,1,7,4,7}, {7,1,7,1,7}, {5,5,7,1,1},
   {7,4,7,1,7}, {7,4,7,5,7}, {7,1,1,1,1}, {7,5,7,5,7}, {7,5,7,1,7}
};

static void ponto(unsigned x, unsigned y, pixel_t c) {
   if (x < LARGURA && y < ALTURA) quadro[y * LARGURA + x] = c;
}

static void bloco(unsigned x0, unsigned y0, unsigned w, unsigned h, pixel_t c) {
   unsigned x, y;
   for (y = y0; y < y0 + h; y++)
      for (x = x0; x < x0 + w; x++) ponto(x, y, c);
}

/* Desenha um digito ampliado por `escala`. */
static void digito(unsigned n, unsigned x0, unsigned y0, unsigned escala, pixel_t c) {
   unsigned linha, col;
   if (n > 9) return;
   for (linha = 0; linha < 5; linha++)
      for (col = 0; col < 3; col++)
         if (FONTE[n][linha] & (4 >> col))
            bloco(x0 + col * escala, y0 + linha * escala, escala, escala, c);
}

static void numero(unsigned v, unsigned x0, unsigned y0, unsigned escala, pixel_t c) {
   char texto[12];
   int i;
   snprintf(texto, sizeof(texto), "%u", v);
   for (i = 0; texto[i]; i++)
      digito((unsigned)(texto[i] - '0'), x0 + (unsigned)i * escala * 4, y0, escala, c);
}

/* ---------------------------------------------------------------- desenho */
static void desenhar(void) {
   const pixel_t PRETO   = cor(0, 0, 0);
   const pixel_t BRANCO  = cor(255, 255, 255);
   const pixel_t VERMELHO= cor(255, 0, 0);
   const pixel_t VERDE   = cor(0, 255, 0);
   const pixel_t AZUL    = cor(0, 0, 255);
   const pixel_t AMARELO = cor(255, 255, 0);

   /* Um flash branco de tela cheia a cada segundo, no MESMO quadro em que o
    * clique de audio sai. Ver e ouvir juntos significa sincronizado; um
    * atraso perceptivel entre os dois e o seu buffer de video ou de som. */
   const int eh_flash = (contador % 60) == 0;

   unsigned i, x, y;

   for (i = 0; i < LARGURA * ALTURA; i++) quadro[i] = eh_flash ? BRANCO : PRETO;

   if (!eh_flash) {
      /* Cantos: se o vermelho aparecer onde devia estar azul, o formato de
       * pixel ou a ordem dos canais esta trocada. Nao ha como nao ver. */
      bloco(0, 0, 48, 36, VERMELHO);                 /* superior esquerdo */
      bloco(LARGURA - 48, 0, 48, 36, VERDE);         /* superior direito  */
      bloco(0, ALTURA - 36, 48, 36, AZUL);           /* inferior esquerdo */
      bloco(LARGURA - 48, ALTURA - 36, 48, 36, BRANCO); /* inferior direito */

      /* Borda de 1px. Se o frontend ignorar o pitch e assumir largura*bpp, a
       * imagem sai enviesada e esta borda vira uma diagonal. */
      for (x = 0; x < LARGURA; x++) { ponto(x, 0, AMARELO); ponto(x, ALTURA - 1, AMARELO); }
      for (y = 0; y < ALTURA; y++) { ponto(0, y, AMARELO); ponto(LARGURA - 1, y, AMARELO); }

      /* Barra vertical que anda 1 pixel por quadro. Movimento continuo e o
       * jeito mais rapido de enxergar engasgo sem contar numero nenhum. */
      x = contador % LARGURA;
      for (y = 40; y < ALTURA - 40; y++) { ponto(x, y, BRANCO); ponto((x + 1) % LARGURA, y, BRANCO); }

      /* O contador em si, grande e legivel de longe. */
      numero(contador, 60, 100, 6, BRANCO);
   }
}

/* ----------------------------------------------------------------- audio
 *
 * Senoide continua: a fase NAO reseta entre quadros, senao haveria um estalo
 * artificial a cada 800 amostras e voce acharia que o bug e seu.
 */
static void gerar_audio(void) {
   const double passo = 2.0 * M_PI * FREQ_TOM / TAXA_AUDIO;
   const int clique = (contador % 60) == 0;
   unsigned i;
   for (i = 0; i < AMOSTRAS_POR_QUADRO; i++) {
      double v = sin(fase) * 0.25;
      int16_t amostra;
      fase += passo;
      if (fase > 2.0 * M_PI) fase -= 2.0 * M_PI;
      /* No quadro do flash, as 80 primeiras amostras viram um estalo seco --
       * o par sonoro do flash branco, para conferir sincronia. */
      if (clique && i < 80) v = (i % 2) ? 0.7 : -0.7;
      amostra = (int16_t)(v * 32767.0);
      audio[i * 2]     = amostra;   /* esquerdo  */
      audio[i * 2 + 1] = amostra;   /* direito   */
   }
}

/* ------------------------------------------------------- API do libretro */
void retro_init(void) {
   quadro   = (pixel_t *)calloc(LARGURA * ALTURA, sizeof(pixel_t));
   audio    = (int16_t *)calloc(AMOSTRAS_POR_QUADRO * 2, sizeof(int16_t));
   contador = 0;
   fase     = 0.0;
}

void retro_deinit(void) {
   free(quadro); quadro = NULL;
   free(audio);  audio  = NULL;
}

unsigned retro_api_version(void) { return RETRO_API_VERSION; }

void retro_get_system_info(struct retro_system_info *info) {
   memset(info, 0, sizeof(*info));
   info->library_name     = "Phoenix Nucleo Falso";
   info->library_version  = "1.0";
   info->need_fullpath    = false;
   /* Aceita qualquer extensao: o conteudo do arquivo e ignorado de proposito,
    * entao da para testar o carregamento com qualquer arquivo que voce tenha. */
   info->valid_extensions = "nes|sfc|smc|bin|rom|txt";
   info->block_extract    = false;
}

void retro_get_system_av_info(struct retro_system_av_info *info) {
   memset(info, 0, sizeof(*info));
   info->geometry.base_width   = LARGURA;
   info->geometry.base_height  = ALTURA;
   info->geometry.max_width    = LARGURA;
   info->geometry.max_height   = ALTURA;
   info->geometry.aspect_ratio = 4.0f / 3.0f;
   info->timing.fps            = FPS;
   info->timing.sample_rate    = TAXA_AUDIO;
}

void retro_set_environment(retro_environment_t cb) {
   bool sem_jogo = true;
   cb_ambiente = cb;
   /* Permite rodar sem conteudo: da para validar video e audio antes de o
    * seletor de ROM existir. */
   cb(RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME, &sem_jogo);
}

void retro_set_video_refresh(retro_video_refresh_t cb)      { cb_video     = cb; }
void retro_set_audio_sample(retro_audio_sample_t cb)        { cb_audio1    = cb; }
void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb) { cb_audio_lote = cb; }
void retro_set_input_poll(retro_input_poll_t cb)            { cb_poll      = cb; }
void retro_set_input_state(retro_input_state_t cb)          { cb_estado    = cb; }

void retro_set_controller_port_device(unsigned port, unsigned device) {
   (void)port; (void)device;
}

void retro_reset(void) { contador = 0; fase = 0.0; }

void retro_run(void) {
   if (cb_poll) cb_poll();

   /* O botao A (RETRO_DEVICE_ID_JOYPAD_A) congela o contador. E o teste de
    * entrada: se apertar e o numero parar, o caminho do controle ate o nucleo
    * esta inteiro. */
   int congelado = 0;
   if (cb_estado)
      congelado = cb_estado(0, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_A);

   desenhar();
   gerar_audio();

   if (cb_video) cb_video(quadro, LARGURA, ALTURA, LARGURA * sizeof(pixel_t));
   if (cb_audio_lote) cb_audio_lote(audio, AMOSTRAS_POR_QUADRO);

   if (!congelado) contador++;
}

bool retro_load_game(const struct retro_game_info *jogo) {
   enum retro_pixel_format formato = FORMATO_ESCOLHIDO;
   (void)jogo;   /* o conteudo e ignorado: isto nao emula nada */

   /* Se o frontend recusar o formato, nao ha o que fazer -- e exatamente o
    * caminho que o nucleo real vai tomar, entao vale falhar aqui tambem. */
   if (cb_ambiente && !cb_ambiente(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT, &formato)) {
      fprintf(stderr, "[nucleo falso] frontend recusou %s\n", NOME_DO_FORMATO);
      return false;
   }
   return true;
}

bool retro_load_game_special(unsigned tipo, const struct retro_game_info *info, size_t num) {
   (void)tipo; (void)info; (void)num;
   return false;
}

void retro_unload_game(void) { }

unsigned retro_get_region(void) { return RETRO_REGION_NTSC; }

/* Estado salvo: so o contador e a fase. Serve para validar o gerenciador de
 * save states de ponta a ponta -- salve, deixe correr, restaure, e o numero
 * na tela tem de voltar exatamente para onde estava. */
size_t retro_serialize_size(void) { return sizeof(unsigned) + sizeof(double); }

bool retro_serialize(void *dados, size_t tamanho) {
   if (tamanho < retro_serialize_size()) return false;
   memcpy(dados, &contador, sizeof(unsigned));
   memcpy((char *)dados + sizeof(unsigned), &fase, sizeof(double));
   return true;
}

bool retro_unserialize(const void *dados, size_t tamanho) {
   if (tamanho < retro_serialize_size()) return false;
   memcpy(&contador, dados, sizeof(unsigned));
   memcpy(&fase, (const char *)dados + sizeof(unsigned), sizeof(double));
   return true;
}

void  retro_cheat_reset(void) { }
void  retro_cheat_set(unsigned i, bool ativo, const char *codigo) { (void)i; (void)ativo; (void)codigo; }
void *retro_get_memory_data(unsigned id) { (void)id; return NULL; }
size_t retro_get_memory_size(unsigned id) { (void)id; return 0; }
