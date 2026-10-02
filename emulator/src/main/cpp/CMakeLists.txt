cmake_minimum_required(VERSION 3.22.1)
project("phoenix_emu")

# ---------------------------------------------------------------------
# Paginas de 16 KB.
#
# A Play Store exige bibliotecas nativas alinhadas em 16 KB para apps com
# target Android 15+. O padrao do NDK ainda e 4 KB em versoes mais antigas,
# entao forcamos o alinhamento no linker. Isto vale para a NOSSA .so; os
# nucleos libretro precisam do mesmo cuidado, e e por isso que compila-los a
# partir do codigo-fonte e mais seguro do que baixar os binarios do buildbot.
# ---------------------------------------------------------------------
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

add_library(phoenix_emu SHARED
    jni_bridge.cpp
    libretro_core.cpp
    ambiente.cpp
    video_renderer.cpp
    audio_output.cpp
)

target_include_directories(phoenix_emu PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(phoenix_emu
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl          # dlopen/dlsym para carregar os nucleos libretro
    oboe::oboe
)

# ---------------------------------------------------------------------
# Nucleo falso -- SO no build de depuracao.
#
# E a ferramenta de teste da Fase 4 (veja nucleo_falso/nucleo_falso.c). Vai
# para o APK debug como libnucleo_falso.so e some sozinho do release, entao
# nao ha risco de ele chegar a Play Store.
# ---------------------------------------------------------------------
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_library(nucleo_falso SHARED nucleo_falso/nucleo_falso.c)
    target_include_directories(nucleo_falso PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(nucleo_falso m)
endif()

# ---------------------------------------------------------------------
# Fase 4b: Oboe entra aqui.
find_package(oboe REQUIRED CONFIG)
# com `implementation("com.google.oboe:oboe:1.9.0")` e
# `arguments += "-DANDROID_STL=c++_shared"` no build.gradle.kts (ja esta la).
# ---------------------------------------------------------------------
