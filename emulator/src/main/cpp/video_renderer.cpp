/**
 * Video: GLES 3.0 sobre o ANativeWindow, com fallback para copia pela CPU.
 *
 * O contexto EGL nasce na thread do emulador (no primeiro quadro) e e liberado
 * quando essa thread termina (thread_local). Se qualquer passo do EGL/GL
 * falhar, cai no caminho antigo da CPU, que continua aqui.
 *
 * Modos de filtro: 0 vizinho, 1 linear, 2 bilinear nitido, 3 scanlines,
 * 4 CRT leve.
 */
#include "video_renderer.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <initializer_list>

#define VTAG "PhoenixVideo"
#define VLOGI(...) __android_log_print(ANDROID_LOG_INFO, VTAG, __VA_ARGS__)
#define VLOGE(...) __android_log_print(ANDROID_LOG_ERROR, VTAG, __VA_ARGS__)

extern int g_formato_pixel;

namespace {

ANativeWindow *g_window = nullptr;
std::atomic<int> g_filtro{2};
std::atomic<bool> g_gl_desligado{false};

// ---- estado do caminho CPU (fallback)
unsigned g_width = 0;
unsigned g_height = 0;
int g_format = -1;

// ---- estado do caminho GLES (vive na thread do emulador)
struct EstadoGL {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext contexto = EGL_NO_CONTEXT;
    ANativeWindow *janela = nullptr;
    GLuint programa = 0;
    GLuint textura = 0;
    GLuint vao = 0;
    GLint uTex = -1, uTexSize = -1, uOutSize = -1, uModo = -1, uBgr = -1;
    unsigned texL = 0, texA = 0;
    int texFormato = -1;
    int filtroAplicado = -1;
    bool pronto = false;
};

thread_local EstadoGL g_gl;

const char *kVertex = R"GLSL(#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = vec2(p.x, 1.0 - p.y);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)GLSL";

const char *kFragment = R"GLSL(#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform vec2 uTexSize;
uniform vec2 uOutSize;
uniform int uModo;
uniform int uBgr;
in vec2 vUv;
out vec4 oColor;

vec2 nitido(vec2 uv) {
    vec2 escala = max(uOutSize / uTexSize, vec2(1.0));
    vec2 texel = uv * uTexSize;
    vec2 base = floor(texel);
    vec2 dist = (texel - base) - 0.5;
    vec2 faixa = 0.5 - 0.5 / escala;
    vec2 f = (dist - clamp(dist, -faixa, faixa)) * escala + 0.5;
    return (base + f) / uTexSize;
}

void main() {
    vec2 uv = vUv;
    if (uModo >= 2) uv = nitido(uv);
    vec3 c = texture(uTex, uv).rgb;
    if (uBgr == 1) c = c.bgr;
    if (uModo >= 3) {
        float escalaY = uOutSize.y / uTexSize.y;
        float forca = clamp(escalaY - 1.5, 0.0, 1.0);
        float linha = 0.5 - 0.5 * cos(vUv.y * uTexSize.y * 6.2831853);
        float s = (uModo == 3) ? 0.45 : 0.55;
        c *= mix(1.0, (1.0 - s) + s * linha, forca);
        c *= 1.12;
        if (uModo >= 4) {
            vec2 q = vUv - 0.5;
            c *= 1.0 - 0.35 * dot(q, q);
        }
    }
    oColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
)GLSL";

GLuint compilar(GLenum tipo, const char *fonte) {
    GLuint sh = glCreateShader(tipo);
    glShaderSource(sh, 1, &fonte, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {0};
        glGetShaderInfoLog(sh, sizeof(log) - 1, nullptr, log);
        VLOGE("shader nao compilou: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

bool criarPrograma(EstadoGL &g) {
    GLuint vs = compilar(GL_VERTEX_SHADER, kVertex);
    GLuint fs = compilar(GL_FRAGMENT_SHADER, kFragment);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return false;
    }
    g.programa = glCreateProgram();
    glAttachShader(g.programa, vs);
    glAttachShader(g.programa, fs);
    glLinkProgram(g.programa);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(g.programa, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512] = {0};
        glGetProgramInfoLog(g.programa, sizeof(log) - 1, nullptr, log);
        VLOGE("programa nao linkou: %s", log);
        glDeleteProgram(g.programa);
        g.programa = 0;
        return false;
    }
    g.uTex = glGetUniformLocation(g.programa, "uTex");
    g.uTexSize = glGetUniformLocation(g.programa, "uTexSize");
    g.uOutSize = glGetUniformLocation(g.programa, "uOutSize");
    g.uModo = glGetUniformLocation(g.programa, "uModo");
    g.uBgr = glGetUniformLocation(g.programa, "uBgr");
    return true;
}

void liberarGL(EstadoGL &g) {
    if (g.display != EGL_NO_DISPLAY) {
        if (g.contexto != EGL_NO_CONTEXT && g.surface != EGL_NO_SURFACE) {
            if (eglMakeCurrent(g.display, g.surface, g.surface, g.contexto)) {
                if (g.programa) glDeleteProgram(g.programa);
                if (g.textura) glDeleteTextures(1, &g.textura);
                if (g.vao) glDeleteVertexArrays(1, &g.vao);
            }
        }
        eglMakeCurrent(g.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g.surface != EGL_NO_SURFACE) eglDestroySurface(g.display, g.surface);
        if (g.contexto != EGL_NO_CONTEXT) eglDestroyContext(g.display, g.contexto);
    }
    g = EstadoGL();
}

// Garante que o GL e liberado na thread do emulador quando ela termina.
void tocarGuarda() {
    struct Guarda {
        ~Guarda() { liberarGL(g_gl); }
    };
    thread_local Guarda guarda;
    (void) guarda;
}

bool iniciarGL(EstadoGL &g) {
    if (!g_window) return false;
    g.janela = g_window;

    g.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g.display == EGL_NO_DISPLAY || !eglInitialize(g.display, nullptr, nullptr)) {
        VLOGE("eglInitialize falhou");
        return false;
    }

    EGLConfig config = nullptr;
    EGLint numConfigs = 0;
    for (int alfa : {8, 0}) {
        const EGLint attribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, alfa,
            EGL_NONE};
        numConfigs = 0;
        if (eglChooseConfig(g.display, attribs, &config, 1, &numConfigs) && numConfigs > 0) break;
        config = nullptr;
    }
    if (!config) {
        VLOGE("nenhuma config EGL ES3");
        return false;
    }

    ANativeWindow_setBuffersGeometry(g.janela, 0, 0, 0);

    g.surface = eglCreateWindowSurface(g.display, config, g.janela, nullptr);
    if (g.surface == EGL_NO_SURFACE) {
        VLOGE("eglCreateWindowSurface falhou: 0x%x", eglGetError());
        return false;
    }

    const EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    g.contexto = eglCreateContext(g.display, config, EGL_NO_CONTEXT, ctxAttribs);
    if (g.contexto == EGL_NO_CONTEXT) {
        VLOGE("eglCreateContext falhou: 0x%x", eglGetError());
        return false;
    }

    if (!eglMakeCurrent(g.display, g.surface, g.surface, g.contexto)) {
        VLOGE("eglMakeCurrent falhou: 0x%x", eglGetError());
        return false;
    }
    eglSwapInterval(g.display, 0);

    if (!criarPrograma(g)) return false;

    glGenTextures(1, &g.textura);
    glBindTexture(GL_TEXTURE_2D, g.textura);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenVertexArrays(1, &g.vao);

    g.pronto = true;
    VLOGI("GLES pronto: renderer=%s", reinterpret_cast<const char *>(glGetString(GL_RENDERER)));
    return true;
}

// Retorna false SO se a inicializacao do GL falhar (aí cai na CPU).
bool desenharGL(const void *dados, unsigned largura, unsigned altura, size_t pitch) {
    EstadoGL &g = g_gl;
    tocarGuarda();

    if (g.pronto && g.janela != g_window) liberarGL(g);
    if (!g.pronto) {
        if (!iniciarGL(g)) {
            liberarGL(g);
            return false;
        }
    }

    EGLint w = 0, h = 0;
    eglQuerySurface(g.display, g.surface, EGL_WIDTH, &w);
    eglQuerySurface(g.display, g.surface, EGL_HEIGHT, &h);
    if (w <= 0 || h <= 0) return true;

    const bool xrgb = (g_formato_pixel == 1);
    const int formato = xrgb ? 1 : 0;
    const unsigned bpp = xrgb ? 4 : 2;

    glBindTexture(GL_TEXTURE_2D, g.textura);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, static_cast<GLint>(pitch / bpp));
    if (g.texL != largura || g.texA != altura || g.texFormato != formato) {
        if (xrgb) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, largura, altura, 0, GL_RGBA, GL_UNSIGNED_BYTE, dados);
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB565, largura, altura, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dados);
        }
        g.texL = largura;
        g.texA = altura;
        g.texFormato = formato;
        g.filtroAplicado = -1;
    } else {
        if (xrgb) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, largura, altura, GL_RGBA, GL_UNSIGNED_BYTE, dados);
        } else {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, largura, altura, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dados);
        }
    }
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);

    const int modo = g_filtro.load(std::memory_order_relaxed);
    if (g.filtroAplicado != modo) {
        const GLint f = (modo == 0) ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
        g.filtroAplicado = modo;
    }

    glViewport(0, 0, w, h);
    glUseProgram(g.programa);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(g.uTex, 0);
    glUniform2f(g.uTexSize, static_cast<float>(largura), static_cast<float>(altura));
    glUniform2f(g.uOutSize, static_cast<float>(w), static_cast<float>(h));
    glUniform1i(g.uModo, modo);
    glUniform1i(g.uBgr, xrgb ? 1 : 0);
    glBindVertexArray(g.vao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    eglSwapBuffers(g.display, g.surface);
    return true;
}

void desenharCPU(const void *dados, unsigned largura, unsigned altura, size_t pitch) {
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

    auto *src_bytes = static_cast<const uint8_t *>(dados);
    auto *dst_bytes = static_cast<uint8_t *>(buffer.bits);

    if (g_formato_pixel == 1) {
        for (unsigned y = 0; y < altura; ++y) {
            auto *src = reinterpret_cast<const uint32_t *>(src_bytes + y * pitch);
            auto *dst = reinterpret_cast<uint32_t *>(dst_bytes + y * buffer.stride * 4);
            for (unsigned x = 0; x < largura; ++x) {
                uint32_t pixel = src[x];
                dst[x] = 0xFF000000 | ((pixel >> 16) & 0xFF) | (pixel & 0xFF00) | ((pixel & 0xFF) << 16);
            }
        }
    } else {
        int bpp = (win_format == WINDOW_FORMAT_RGB_565) ? 2 : 4;
        for (unsigned y = 0; y < altura; ++y) {
            memcpy(dst_bytes + y * buffer.stride * bpp, src_bytes + y * pitch, largura * bpp);
        }
    }

    ANativeWindow_unlockAndPost(g_window);
}

} // namespace

extern "C" {

void phoenix_video_inicializar(ANativeWindow *window) {
    g_window = window;
    g_width = 0;
    g_height = 0;
    g_format = -1;
    g_gl_desligado.store(false, std::memory_order_relaxed);
}

void phoenix_video_definir_filtro(int modo) {
    if (modo < 0) modo = 0;
    if (modo > 4) modo = 4;
    g_filtro.store(modo, std::memory_order_relaxed);
}

void phoenix_video_desenhar_quadro(const void *dados, unsigned largura,
                                   unsigned altura, size_t pitch) {
    if (!g_window || !dados) return;

    if (!g_gl_desligado.load(std::memory_order_relaxed)) {
        if (desenharGL(dados, largura, altura, pitch)) return;
        VLOGE("GLES indisponivel, usando copia pela CPU");
        g_gl_desligado.store(true, std::memory_order_relaxed);
        g_format = -1;
    }
    desenharCPU(dados, largura, altura, pitch);
}

void phoenix_video_finalizar() {
    g_window = nullptr;
}

} // extern "C"
