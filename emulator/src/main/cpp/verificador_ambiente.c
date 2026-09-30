/*
 * ============================================================================
 * VERIFICADOR 1 -- nomes dos comandos do environment callback
 * ============================================================================
 *
 * GERADO a partir do libretro.h oficial (90 comandos). Nao edite a mao:
 * regenere se trocar de versao do cabecalho.
 *
 * Por que existe: o environment callback e um unico ponto por onde o nucleo
 * pede TUDO ao frontend, e o `default:` dele devolve false em silencio. Um
 * comando importante nao implementado nao quebra nada de imediato -- o nucleo
 * carrega, roda, e so um jogo especifico se comporta errado, semanas depois.
 *
 * Com isto no `default`, todo pedido nao atendido vira uma linha no logcat com
 * o nome do comando, e voce decide se aquilo importa ANTES de virar bug.
 *
 * Uso, dentro do seu retro_environment_t:
 *
 *     default:
 *         registrar_comando_nao_tratado(cmd);
 *         return false;
 */

#include <android/log.h>

#define FALSO_TAG "PhoenixLibretro"

/* O bit 0x10000 marca comandos experimentais e o 0x20000 os privados; os dois
 * precisam sair antes da comparacao, senao 36|EXPERIMENTAL nunca casa com 36. */
static unsigned comando_cru(unsigned cmd) { return cmd & 0xFFFFu; }

const char *nome_do_comando(unsigned cmd) {
   switch (comando_cru(cmd)) {
   case   1: return "SET_ROTATION";
   case   2: return "GET_OVERSCAN";
   case   3: return "GET_CAN_DUPE";
   case   6: return "SET_MESSAGE";
   case   7: return "SHUTDOWN";
   case   8: return "SET_PERFORMANCE_LEVEL";
   case   9: return "GET_SYSTEM_DIRECTORY";
   case  10: return "SET_PIXEL_FORMAT";
   case  11: return "SET_INPUT_DESCRIPTORS";
   case  12: return "SET_KEYBOARD_CALLBACK";
   case  13: return "SET_DISK_CONTROL_INTERFACE";
   case  14: return "SET_HW_RENDER";
   case  15: return "GET_VARIABLE";
   case  16: return "SET_VARIABLES";
   case  17: return "GET_VARIABLE_UPDATE";
   case  18: return "SET_SUPPORT_NO_GAME";
   case  19: return "GET_LIBRETRO_PATH";
   case  21: return "SET_FRAME_TIME_CALLBACK";
   case  22: return "SET_AUDIO_CALLBACK";
   case  23: return "GET_RUMBLE_INTERFACE";
   case  24: return "GET_INPUT_DEVICE_CAPABILITIES";
   case  25: return "GET_SENSOR_INTERFACE  [experimental]";
   case  26: return "GET_CAMERA_INTERFACE  [experimental]";
   case  27: return "GET_LOG_INTERFACE";
   case  28: return "GET_PERF_INTERFACE";
   case  29: return "GET_LOCATION_INTERFACE";
   case  30: return "GET_CORE_ASSETS_DIRECTORY";
   case  31: return "GET_SAVE_DIRECTORY";
   case  32: return "SET_SYSTEM_AV_INFO";
   case  33: return "SET_PROC_ADDRESS_CALLBACK";
   case  34: return "SET_SUBSYSTEM_INFO";
   case  35: return "SET_CONTROLLER_INFO";
   case  36: return "SET_MEMORY_MAPS  [experimental]";
   case  37: return "SET_GEOMETRY";
   case  38: return "GET_USERNAME";
   case  39: return "GET_LANGUAGE";
   case  40: return "GET_CURRENT_SOFTWARE_FRAMEBUFFER  [experimental]";
   case  41: return "GET_HW_RENDER_INTERFACE  [experimental]";
   case  42: return "SET_SUPPORT_ACHIEVEMENTS  [experimental]";
   case  43: return "SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE  [experimental]";
   case  44: return "SET_HW_SHARED_CONTEXT  [experimental]";
   case  45: return "GET_VFS_INTERFACE  [experimental]";
   case  46: return "GET_LED_INTERFACE  [experimental]";
   case  47: return "GET_AUDIO_VIDEO_ENABLE  [experimental]";
   case  48: return "GET_MIDI_INTERFACE  [experimental]";
   case  49: return "GET_FASTFORWARDING  [experimental]";
   case  50: return "GET_TARGET_REFRESH_RATE  [experimental]";
   case  51: return "GET_INPUT_BITMASKS  [experimental]";
   case  52: return "GET_CORE_OPTIONS_VERSION";
   case  53: return "SET_CORE_OPTIONS";
   case  54: return "SET_CORE_OPTIONS_INTL";
   case  55: return "SET_CORE_OPTIONS_DISPLAY";
   case  56: return "GET_PREFERRED_HW_RENDER";
   case  57: return "GET_DISK_CONTROL_INTERFACE_VERSION";
   case  58: return "SET_DISK_CONTROL_EXT_INTERFACE";
   case  59: return "GET_MESSAGE_INTERFACE_VERSION";
   case  60: return "SET_MESSAGE_EXT";
   case  61: return "GET_INPUT_MAX_USERS";
   case  62: return "SET_AUDIO_BUFFER_STATUS_CALLBACK";
   case  63: return "SET_MINIMUM_AUDIO_LATENCY";
   case  64: return "SET_FASTFORWARDING_OVERRIDE";
   case  65: return "SET_CONTENT_INFO_OVERRIDE";
   case  66: return "GET_GAME_INFO_EXT";
   case  67: return "SET_CORE_OPTIONS_V2";
   case  68: return "SET_CORE_OPTIONS_V2_INTL";
   case  69: return "SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK";
   case  70: return "SET_VARIABLE";
   case  71: return "GET_THROTTLE_STATE  [experimental]";
   case  72: return "GET_SAVESTATE_CONTEXT  [experimental]";
   case  73: return "GET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_SUPPORT  [experimental]";
   case  74: return "GET_JIT_CAPABLE";
   case  75: return "GET_MICROPHONE_INTERFACE  [experimental]";
   case  77: return "GET_DEVICE_POWER  [experimental]";
   case  78: return "SET_NETPACKET_INTERFACE";
   case  79: return "GET_PLAYLIST_DIRECTORY";
   case  80: return "GET_FILE_BROWSER_START_DIRECTORY";
   case  81: return "GET_TARGET_SAMPLE_RATE  [experimental]";
   case  82: return "GET_NETPLAY_CLIENT_INDEX  [experimental]";
   case  83: return "EXEC_MEM_ALLOC";
   case  84: return "EXEC_MEM_FREE";
   case  85: return "GET_AUDIO_SAMPLE_BATCH_FLOAT  [experimental]";
   case  86: return "GET_MEMORY_STATUS  [experimental]";
   case  87: return "SET_SERIALIZATION_QUIRKS";
   case  88: return "GET_SCREEN_10BPC_CAPABLE  [experimental]";
   case  89: return "GET_HDR_PAPER_WHITE_NITS  [experimental]";
   case  90: return "GET_HDR_EXPAND_GAMUT  [experimental]";
   case  91: return "GET_HDR_OUTPUT_MODE  [experimental]";
   case  92: return "GET_HDR_MAX_NITS  [experimental]";
   case  93: return "GET_VFS_AUTHORIZED_LOCATIONS  [experimental]";
   case  94: return "GET_AUDIO_SAMPLE_BATCH_MULTI  [experimental]";
   default: return "DESCONHECIDO";
   }
}

/*
 * Registra uma vez por comando, e nao uma vez por chamada.
 *
 * Alguns nucleos consultam o mesmo comando a cada quadro. Sem isto, um unico
 * comando nao tratado enche o logcat com 60 linhas por segundo e esconde todo
 * o resto -- inclusive os outros comandos nao tratados.
 */
void registrar_comando_nao_tratado(unsigned cmd) {
   static unsigned char ja_visto[65536];
   unsigned cru = comando_cru(cmd);
   if (cru < 65536) {
      if (ja_visto[cru]) return;
      ja_visto[cru] = 1;
   }
   __android_log_print(ANDROID_LOG_WARN, FALSO_TAG,
      "environment nao tratado: %u (%s)%s", cru, nome_do_comando(cmd),
      (cmd & 0x10000u) ? " [pedido como experimental]" : "");
}
