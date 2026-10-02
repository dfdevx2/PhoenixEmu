/**
 * Vídeo: ANativeWindow com cópia pela CPU.
 *
 * ESTADO: Etapa 2.
 *
 * NOTA: NÃO usamos EGL/OpenGL agora. Será adicionado quando os shaders entrarem.
 */
#include "video_renderer.h"
#include <android/log.h>
#include <android/native_window.h>
#include <cstdint>
#include <cstring>

extern int g_formato_pixel;

namespace {
    ANativeWindow* g_window = nullptr;
    unsigned g_width = 0;
    unsigned g_height = 0;
    int g_format = -1;
}

extern "C" {

void phoenix_video_inicializar(ANativeWindow* window) {
    g_window = window;
    g_width = 0;
    g_height = 0;
    g_format = -1;
}

void phoenix_video_desenhar_quadro(const void *dados, unsigned largura,
                                   unsigned altura, size_t pitch) {
    if (!g_window || !dados) return;

    int32_t win_format = (g_formato_pixel == 1) ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGB_565;

    if (largura != g_width || altura != g_height || win_format != g_format) {
        ANativeWindow_setBuffersGeometry(g_window, largura, altura, win_format);
        g_width = largura;
        g_height = altura;
        g_format = win_format;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(g_window, &buffer, nullptr) < 0) {
        return;
    }

    auto* src_bytes = static_cast<const uint8_t*>(dados);
    auto* dst_bytes = static_cast<uint8_t*>(buffer.bits);

    if (g_formato_pixel == 1) {
        for (unsigned y = 0; y < altura; ++y) {
            auto* src = reinterpret_cast<const uint32_t*>(src_bytes + y * pitch);
            auto* dst = reinterpret_cast<uint32_t*>(dst_bytes + y * buffer.stride * 4);
            for (unsigned x = 0; x < largura; ++x) {
                uint32_t pixel = src[x];
                dst[x] = 0xFF000000 | ((pixel >> 16) & 0xFF) | (pixel & 0xFF00) | ((pixel & 0xFF) << 16);
            }
        }
    } else {
        // Assume RGB565 ou copia direta 16-bit
        int bpp = (win_format == WINDOW_FORMAT_RGB_565) ? 2 : 4;
        for (unsigned y = 0; y < altura; ++y) {
            memcpy(dst_bytes + y * buffer.stride * bpp, src_bytes + y * pitch, largura * bpp);
        }
    }

    ANativeWindow_unlockAndPost(g_window);
}

void phoenix_video_finalizar() {
    g_window = nullptr;
}

} // extern "C"
