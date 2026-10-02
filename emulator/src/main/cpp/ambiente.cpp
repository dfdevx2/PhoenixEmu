#include "libretro.h"
#include <android/log.h>
#include <string>
#include <cstdarg>
#include <stdio.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", __VA_ARGS__)

#include "verificador_ambiente.c"

std::string g_system_dir;
std::string g_saves_dir;
int g_formato_pixel = -1;

static void retro_log_printf(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[4096];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    int priority = ANDROID_LOG_INFO;
    switch(level) {
        case RETRO_LOG_DEBUG: priority = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO: priority = ANDROID_LOG_INFO; break;
        case RETRO_LOG_WARN: priority = ANDROID_LOG_WARN; break;
        case RETRO_LOG_ERROR: priority = ANDROID_LOG_ERROR; break;
    }
    __android_log_print(priority, "PhoenixLibretro", "%s", buf);
}

bool cb_environment(unsigned cmd, void *data) {
    unsigned clean_cmd = cmd & 0xFFFF;
    switch (clean_cmd) {
        case 2: // RETRO_ENVIRONMENT_GET_OVERSCAN
            *(bool*)data = false;
            return true;
        case 3: // RETRO_ENVIRONMENT_GET_CAN_DUPE
            *(bool*)data = true;
            return true;
        case 9: // RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY
            *(const char**)data = g_system_dir.c_str();
            return true;
        case 10: { // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT
            int format = *(const int*)data;
            if (format == 1 || format == 2) {
                g_formato_pixel = format;
                LOGI("SET_PIXEL_FORMAT aceito: %d", format);
                return true;
            }
            return false;
        }
        case 11: // RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS
            return true;
        case 15: // RETRO_ENVIRONMENT_GET_VARIABLE
            return false;
        case 16: // RETRO_ENVIRONMENT_SET_VARIABLES
            return true;
        case 17: // RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE
            *(bool*)data = false;
            return true;
        case 18: // RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME
            return true;
        case 27: { // RETRO_ENVIRONMENT_GET_LOG_INTERFACE
            struct retro_log_callback *cb = (struct retro_log_callback*)data;
            cb->log = retro_log_printf;
            return true;
        }
        case 31: // RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY
            *(const char**)data = g_saves_dir.c_str();
            return true;
        case 32: // RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO
            return true;
        case 35: // RETRO_ENVIRONMENT_SET_CONTROLLER_INFO
            return true;
        case 37: // RETRO_ENVIRONMENT_SET_GEOMETRY
            return true;
        case 52: // RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION
            *(unsigned*)data = 0;
            return true;
        default:
            registrar_comando_nao_tratado(clean_cmd);
            return false;
    }
}