#pragma once

#include <android/native_window.h>
#include <cstddef>

extern "C" {

void phoenix_video_inicializar(ANativeWindow* window);

void phoenix_video_desenhar_quadro(const void *dados, unsigned largura,
                                   unsigned altura, size_t pitch);

void phoenix_video_finalizar();
void phoenix_video_definir_filtro(int modo);

}
