/**
 * Vídeo: EGL + GLES3 sobre ANativeWindow.
 *
 * ESTADO: esqueleto (Fase 4a).
 *
 * Decisoes ja tomadas, registradas aqui para nao se perderem:
 *
 *  - SurfaceView, nunca TextureView. TextureView passa por uma composicao
 *    extra e acrescenta latencia -- inaceitavel num emulador.
 *  - O quadro do nucleo sobe por glTexSubImage2D numa textura persistente e
 *    e desenhado com shader. Nada de recriar textura por quadro.
 *  - As opcoes que ja existem na tela de Configuracoes (4:3 / 16:9 /
 *    esticar; nearest / bilinear / CRT scanlines) viram parametros deste
 *    pipeline: as duas primeiras sao a matriz de projecao, a terceira e a
 *    escolha de shader.
 *  - ANativeWindow_setFrameRate(60) em telas de alta taxa: sem isso, 60 Hz
 *    de conteudo numa tela de 120 Hz produz cadencia irregular.
 */
#include <android/log.h>
#include <cstddef>

extern "C" {

void phoenix_video_inicializar() {
    // TODO: eglCreateContext, shaders, textura de trabalho.
}

void phoenix_video_desenhar_quadro(const void * /*dados*/, unsigned /*largura*/,
                                   unsigned /*altura*/, size_t /*pitch*/) {
    // TODO: glTexSubImage2D + draw + eglSwapBuffers.
}

void phoenix_video_finalizar() {
    // TODO: liberar contexto e recursos.
}

} // extern "C"
