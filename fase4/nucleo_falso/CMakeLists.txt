# Nucleo falso da Fase 4.
#
# Constroi UM .so por ABI, que voce copia para o aparelho e carrega como se
# fosse um nucleo de verdade. Nao faz parte do app: e ferramenta de teste.
#
#   cmake -B build \
#     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
#   cmake --build build
#
cmake_minimum_required(VERSION 3.22)
project(nucleo_falso C)

set(CMAKE_C_STANDARD 99)

add_library(nucleo_falso SHARED nucleo_falso.c)
target_include_directories(nucleo_falso PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(nucleo_falso m)

# Alinhamento de 16 KB.
#
# O Android 15+ roda com paginas de 16 KB em aparelhos novos -- e o Odin 3 com
# Snapdragon 8 Elite e exatamente esse caso. Um .so alinhado em 4 KB simplesmente
# nao carrega la, e o erro do dlopen nao diz "alinhamento": diz que nao achou a
# biblioteca. Vale a pena conferir isto AGORA, no nucleo falso, e nao depois
# quando o suspeito for o nucleo de verdade.
if(ANDROID)
    target_link_options(nucleo_falso PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
endif()

# Para gerar tambem a variante RGB565:
#   add_library(nucleo_falso_565 SHARED nucleo_falso.c)
#   target_compile_definitions(nucleo_falso_565 PRIVATE FORMATO_RGB565)
