/*
 * Confere o nucleo falso.
 *
 * Existe porque o nucleo falso e o juiz de toda a Fase 4: se ELE estiver
 * errado, ele valida bug em vez de expor. Aqui ele e carregado por dlopen --
 * exatamente como o app vai carregar -- e cada promessa que ele faz e medida.
 */
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include "libretro.h"

static int falhas = 0;
static void checa(const char *oque, int ok, const char *detalhe) {
   printf("  %s  %-46s %s\n", ok ? "OK   " : "FALHA", oque, detalhe ? detalhe : "");
   if (!ok) falhas++;
}

/* ------------------------------------------------ estado capturado do nucleo */
static const void *ult_quadro;
static unsigned    ult_larg, ult_alt;
static size_t      ult_pitch;
static int16_t     colhido[48000 * 4];
static size_t      n_colhido;
static size_t      amostras_do_quadro;
static int         formato_pedido = -1;
static int         congelar = 0;

static void v_refresh(const void *dados, unsigned l, unsigned a, size_t pitch) {
   ult_quadro = dados; ult_larg = l; ult_alt = a; ult_pitch = pitch;
}
static size_t a_lote(const int16_t *dados, size_t quadros) {
   amostras_do_quadro = quadros;
   if (n_colhido + quadros * 2 < sizeof(colhido) / sizeof(colhido[0])) {
      memcpy(&colhido[n_colhido], dados, quadros * 2 * sizeof(int16_t));
      n_colhido += quadros * 2;
   }
   return quadros;
}
static void a_um(int16_t e, int16_t d) { (void)e; (void)d; }
static void poll(void) { }
static int16_t estado(unsigned p, unsigned d, unsigned i, unsigned id) {
   (void)p; (void)d; (void)i;
   return (id == RETRO_DEVICE_ID_JOYPAD_A) ? (int16_t)congelar : 0;
}
static bool ambiente(unsigned cmd, void *dados) {
   if (cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT) {
      formato_pedido = *(const enum retro_pixel_format *)dados;
      return true;
   }
   if (cmd == RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME) return true;
   return false;   /* como um frontend minimo faria */
}

/* Le o pixel (x,y) do ultimo quadro, ja sabendo o formato. */
static uint32_t pixel(unsigned x, unsigned y, int formato) {
   if (formato == RETRO_PIXEL_FORMAT_RGB565) {
      const uint16_t *p = (const uint16_t *)((const char *)ult_quadro + y * ult_pitch);
      uint16_t v = p[x];
      return (uint32_t)(((v >> 11) & 31) << 19 | ((v >> 5) & 63) << 10 | (v & 31) << 3);
   }
   const uint32_t *p = (const uint32_t *)((const char *)ult_quadro + y * ult_pitch);
   return p[x] & 0xFFFFFF;
}
#define R(c) (((c) >> 16) & 0xFF)
#define G(c) (((c) >> 8)  & 0xFF)
#define B(c) ( (c)        & 0xFF)

/*
 * Branco por limiar, nunca por igualdade.
 *
 * Em RGB565 o branco puro nao existe: 5 bits de R e B expandem para 0xF8, e o
 * pixel volta como F8FCF8. Comparar com 0xFFFFFF passa em XRGB8888 e falha em
 * 565 -- e a falha pareceria bug do nucleo, nao do teste.
 */
static int eh_branco(uint32_t c) { return R(c) > 200 && G(c) > 200 && B(c) > 200; }

/*
 * Posicao da barra branca.
 *
 * Le na linha 50 de proposito: la a barra e a unica coisa branca. No meio da
 * tela ela convive com os digitos do contador, e a varredura acharia o digito.
 */
static int ler_barra(int formato) {
   for (unsigned x = 1; x < 319; x++)
      if (eh_branco(pixel(x, 50, formato))) return (int)x;
   return -1;
}

int main(int argc, char **argv) {
   const char *caminho = (argc > 1) ? argv[1] : "./nucleo_falso.so";
   void *lib = dlopen(caminho, RTLD_LAZY | RTLD_LOCAL);
   if (!lib) { printf("dlopen falhou: %s\n", dlerror()); return 2; }

   /* Os 25 simbolos que o libretro exige. Faltar um so aparece em runtime. */
   static const char *NECESSARIOS[] = {
      "retro_init","retro_deinit","retro_api_version","retro_get_system_info",
      "retro_get_system_av_info","retro_set_environment","retro_set_video_refresh",
      "retro_set_audio_sample","retro_set_audio_sample_batch","retro_set_input_poll",
      "retro_set_input_state","retro_set_controller_port_device","retro_reset",
      "retro_run","retro_load_game","retro_load_game_special","retro_unload_game",
      "retro_get_region","retro_serialize_size","retro_serialize","retro_unserialize",
      "retro_cheat_reset","retro_cheat_set","retro_get_memory_data","retro_get_memory_size"
   };
   int faltando = 0; char buf[256] = "";
   for (size_t i = 0; i < sizeof(NECESSARIOS)/sizeof(*NECESSARIOS); i++)
      if (!dlsym(lib, NECESSARIOS[i])) { faltando++; snprintf(buf, sizeof buf, "falta %s", NECESSARIOS[i]); }
   checa("os 25 simbolos da API existem", faltando == 0, faltando ? buf : "25/25");

   /* Um typedef por assinatura: em C o nome de um ponteiro de funcao mora
    * DENTRO dos parenteses, entao `tipo nome` nao funciona num macro. */
   typedef unsigned (*f_versao)(void);
   typedef void     (*f_nada)(void);
   typedef void     (*f_avinfo)(struct retro_system_av_info *);
   typedef void     (*f_amb)(retro_environment_t);
   typedef void     (*f_video)(retro_video_refresh_t);
   typedef void     (*f_aud1)(retro_audio_sample_t);
   typedef void     (*f_audl)(retro_audio_sample_batch_t);
   typedef void     (*f_poll)(retro_input_poll_t);
   typedef void     (*f_est)(retro_input_state_t);
   typedef bool     (*f_load)(const struct retro_game_info *);
   typedef size_t   (*f_tam)(void);
   typedef bool     (*f_ser)(void *, size_t);
   typedef bool     (*f_des)(const void *, size_t);

   #define PEGA(tipo, nome) tipo nome = (tipo)dlsym(lib, #nome)
   PEGA(f_versao, retro_api_version);
   PEGA(f_nada,   retro_init);
   PEGA(f_nada,   retro_deinit);
   PEGA(f_nada,   retro_run);
   PEGA(f_avinfo, retro_get_system_av_info);
   PEGA(f_amb,    retro_set_environment);
   PEGA(f_video,  retro_set_video_refresh);
   PEGA(f_aud1,   retro_set_audio_sample);
   PEGA(f_audl,   retro_set_audio_sample_batch);
   PEGA(f_poll,   retro_set_input_poll);
   PEGA(f_est,    retro_set_input_state);
   PEGA(f_load,   retro_load_game);
   PEGA(f_tam,    retro_serialize_size);
   PEGA(f_ser,    retro_serialize);
   PEGA(f_des,    retro_unserialize);

   checa("retro_api_version == 1", retro_api_version() == RETRO_API_VERSION, NULL);

   retro_set_environment(ambiente);
   retro_set_video_refresh(v_refresh);
   retro_set_audio_sample(a_um);
   retro_set_audio_sample_batch(a_lote);
   retro_set_input_poll(poll);
   retro_set_input_state(estado);
   retro_init();

   struct retro_system_av_info av;
   retro_get_system_av_info(&av);
   snprintf(buf, sizeof buf, "%ux%u @ %.2f Hz, audio %.0f Hz",
            av.geometry.base_width, av.geometry.base_height,
            av.timing.fps, av.timing.sample_rate);
   checa("geometria e tempo anunciados", av.geometry.base_width == 320 &&
         av.geometry.base_height == 240 && fabs(av.timing.fps - 60.0) < 1e-9 &&
         fabs(av.timing.sample_rate - 48000.0) < 1e-9, buf);

   struct retro_game_info jogo; memset(&jogo, 0, sizeof jogo);
   checa("retro_load_game aceita", retro_load_game(&jogo), NULL);
   snprintf(buf, sizeof buf, "codigo %d", formato_pedido);
   checa("pediu um formato de pixel ao frontend", formato_pedido >= 0, buf);
   const int fmt = formato_pedido;

   /* ------------------------------------------------- um quadro por retro_run */
   n_colhido = 0;
   retro_run();                                  /* contador 0: quadro do flash */
   snprintf(buf, sizeof buf, "%zu amostras", amostras_do_quadro);
   checa("entrega 800 amostras por quadro", amostras_do_quadro == 800, buf);
   snprintf(buf, sizeof buf, "pitch %zu para largura %u", ult_pitch, ult_larg);
   checa("pitch coerente com o formato",
         ult_pitch == ult_larg * (size_t)(fmt == RETRO_PIXEL_FORMAT_RGB565 ? 2 : 4), buf);

   /* O quadro 0 tem de ser o flash: tela inteira branca. */
   int branco = eh_branco(pixel(5,5,fmt)) && eh_branco(pixel(160,120,fmt)) &&
                eh_branco(pixel(310,230,fmt));
   checa("quadro 0 e o flash (tela branca)", branco, NULL);

   /* ...e o audio desse quadro tem de conter o clique (amplitude alta). */
   int16_t pico = 0;
   for (size_t i = 0; i < 160; i++) if (abs(colhido[i]) > pico) pico = (int16_t)abs(colhido[i]);
   snprintf(buf, sizeof buf, "pico %d", pico);
   checa("clique sai no MESMO quadro do flash", pico > 20000, buf);

   /* -------------------------------------------------------- cantos e borda */
   retro_run();                                   /* contador 1: quadro normal */
   uint32_t se = pixel(10, 10, fmt), sd = pixel(320 - 10, 10, fmt);
   uint32_t ie = pixel(10, 230, fmt), id = pixel(310, 230, fmt);
   snprintf(buf, sizeof buf, "SE=%06X SD=%06X IE=%06X ID=%06X", se, sd, ie, id);
   checa("cantos nas cores certas (R/G/B/branco)",
         R(se) > 200 && G(se) < 60 && B(se) < 60 &&
         G(sd) > 200 && R(sd) < 60 && B(sd) < 60 &&
         B(ie) > 200 && R(ie) < 60 && G(ie) < 60 &&
         R(id) > 200 && G(id) > 200 && B(id) > 200, buf);

   uint32_t topo = pixel(160, 0, fmt), base = pixel(160, 239, fmt);
   checa("borda de 1px presente em cima e embaixo",
         R(topo) > 200 && G(topo) > 200 && B(topo) < 60 &&
         R(base) > 200 && G(base) > 200 && B(base) < 60, NULL);

   /* ------------------------------------------- a barra anda 1px por quadro
    *
    * A amostragem e na linha 50, e nao no meio da tela: a barra cobre y=40..199
    * e os digitos do contador ocupam y=100..129, entao no meio os dois se
    * misturam e a varredura acha o digito em vez da barra. Comecar em x=1
    * tambem importa -- x=0 e a borda, e pular para x=2 perderia a barra no
    * quadro 1, fazendo dois quadros seguidos parecerem iguais. */
   int achou_em = ler_barra(fmt), achou_depois;
   retro_run();
   achou_depois = ler_barra(fmt);
   snprintf(buf, sizeof buf, "x=%d -> x=%d", achou_em, achou_depois);
   checa("a barra avanca exatamente 1 pixel", achou_depois == achou_em + 1, buf);

   /* -------------------------------------------------- o tom e mesmo 440 Hz */
   n_colhido = 0;
   for (int i = 0; i < 50; i++) retro_run();      /* quadros sem clique */
   int cruzamentos = 0;
   for (size_t i = 2; i < n_colhido; i += 2)
      if ((colhido[i-2] < 0) != (colhido[i] < 0)) cruzamentos++;
   double quadros_de_audio = (double)n_colhido / 2.0;
   double hz = (cruzamentos / 2.0) * (48000.0 / quadros_de_audio);
   snprintf(buf, sizeof buf, "medido %.1f Hz", hz);
   checa("o tom mede 440 Hz", fabs(hz - 440.0) < 3.0, buf);

   /* A senoide nao pode reiniciar a cada quadro: um salto na fronteira dos
    * 800 quadros seria um estalo que voce culparia no seu buffer. */
   int salto = 0;
   for (size_t q = 1; q < 40; q++) {
      size_t i = q * 800 * 2;
      if (i + 2 < n_colhido && abs(colhido[i] - colhido[i-2]) > 4000) salto++;
   }
   snprintf(buf, sizeof buf, "%d descontinuidades", salto);
   checa("a fase do audio e continua entre quadros", salto == 0, buf);

   /* ----------------------------------------------------- estado e entrada */
   size_t tam = retro_serialize_size();
   void *snap = malloc(tam);
   retro_run();
   const int no_snapshot = ler_barra(fmt);       /* onde estavamos ao salvar */
   checa("retro_serialize grava", retro_serialize(snap, tam), NULL);

   for (int i = 0; i < 10; i++) retro_run();
   const int apos_correr = ler_barra(fmt);
   checa("retro_unserialize restaura", retro_unserialize(snap, tam), NULL);
   retro_run();
   const int voltou = ler_barra(fmt);
   /* O +1 nao e folga: `retro_run` DESENHA o quadro e so depois avanca o
    * contador. Entao um save state tirado depois do quadro N guarda "o
    * proximo a desenhar e N+1", e restaurar retoma em N+1, nunca em N.
    * Vale lembrar disto ao fazer o gerenciador de save states: salvar e
    * restaurar no mesmo instante adianta um quadro, e isso e o correto. */
   snprintf(buf, sizeof buf, "salvou apos %d, correu ate %d, retomou em %d",
            no_snapshot, apos_correr, voltou);
   checa("o estado restaurado retoma no quadro seguinte ao salvo",
         apos_correr == no_snapshot + 10 && voltou == no_snapshot + 1, buf);
   free(snap);

   congelar = 1;
   retro_run();
   const int antes_c = ler_barra(fmt);
   retro_run();
   const int depois_c = ler_barra(fmt);
   snprintf(buf, sizeof buf, "x=%d -> x=%d", antes_c, depois_c);
   checa("o botao A congela o contador (teste de entrada)", antes_c == depois_c, buf);

   retro_deinit();
   dlclose(lib);
   printf("\n%s: %d falha(s)\n", falhas ? "REPROVADO" : "APROVADO", falhas);
   return falhas ? 1 : 0;
}
