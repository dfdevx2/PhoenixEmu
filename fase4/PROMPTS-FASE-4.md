# Fase 4 — sequência de prompts

Oito prompts, na ordem. Cada um cabe com folga nos 98k e é auto-contido: o
modelo local não precisa lembrar do anterior.

**Os jogos de verdade abrem no prompt 7.** Os seis primeiros constroem e
validam o frontend contra o núcleo falso, que não é desvio — é o que faz o
prompt 7 ser uma troca de `.so` em vez de uma caçada.

Regra de ouro entre um prompt e o outro: **não avance sem o verificador do
passo passar.** Um erro de vídeo carregado para o passo do áudio vira duas
causas possíveis para um sintoma só, e é aí que se perde um dia.

---

## Antes de começar

```bash
# 1. NDK pelo SDK Manager do Android Studio (Tools > SDK Manager > SDK Tools)
# 2. destravar o módulo nativo
cd ~/AndroidStudioProjects/PhoenixEmu
sed -i 's|^// include(":emulator")|include(":emulator")|' settings.gradle.kts
# 3. o cabeçalho oficial
cp ~/Downloads/fase4/libretro.h emulator/src/main/cpp/
```

Compile o núcleo falso para o aparelho e deixe-o acessível:

```bash
cd ~/Downloads/fase4/nucleo_falso
cmake -B build \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
cmake --build build
./CONFERIR-ALINHAMENTO.sh build/libnucleo_falso.so   # tem de dar OK
adb push build/libnucleo_falso.so /sdcard/Download/
```

---

## Prompt 1 — carregador do núcleo

> No módulo `:emulator` deste projeto Android, escreva `libretro_core.h` e
> `libretro_core.cpp` em `src/main/cpp/`.
>
> Objetivo: carregar um núcleo libretro `.so` em tempo de execução e expor suas
> funções. O cabeçalho `libretro.h` oficial já está na mesma pasta.
>
> Requisitos:
> - uma struct `NucleoLibretro` com um ponteiro de função para cada uma das 25
>   funções obrigatórias da API (`retro_init`, `retro_deinit`,
>   `retro_api_version`, `retro_get_system_info`, `retro_get_system_av_info`,
>   `retro_set_environment`, `retro_set_video_refresh`, `retro_set_audio_sample`,
>   `retro_set_audio_sample_batch`, `retro_set_input_poll`,
>   `retro_set_input_state`, `retro_set_controller_port_device`, `retro_reset`,
>   `retro_run`, `retro_load_game`, `retro_load_game_special`,
>   `retro_unload_game`, `retro_get_region`, `retro_serialize_size`,
>   `retro_serialize`, `retro_unserialize`, `retro_cheat_reset`,
>   `retro_cheat_set`, `retro_get_memory_data`, `retro_get_memory_size`);
> - `bool carregar(const char* caminho)` que usa `dlopen` com
>   `RTLD_NOW | RTLD_LOCAL` e resolve cada símbolo com `dlsym`;
> - **se qualquer símbolo faltar, falhe imediatamente e registre o NOME do
>   símbolo ausente** com `__android_log_print`. Um ponteiro nulo chamado
>   depois é um crash sem pista; o nome dito na hora é um diagnóstico;
> - `RTLD_LOCAL` e não `RTLD_GLOBAL`: dois núcleos diferentes exportam os
>   mesmos 25 nomes, e com `GLOBAL` o segundo sequestraria o primeiro;
> - `void descarregar()` que chama `retro_deinit` antes do `dlclose`, e é
>   seguro chamar duas vezes;
> - verifique `retro_api_version() == 1` logo após carregar, e recuse se não for.
>
> Não escreva ainda nada de vídeo, áudio ou JNI. Só o carregador.

**Verificação:** um teste que carrega `/sdcard/Download/libnucleo_falso.so` e
loga `library_name`. Tem de sair `Phoenix Nucleo Falso`. Se o `dlopen` falhar
no Android 15+, rode o `CONFERIR-ALINHAMENTO.sh` antes de suspeitar do código.

---

## Prompt 2 — environment callback

> Em `src/main/cpp/`, escreva `ambiente.cpp` com a implementação do
> `retro_environment_t` do frontend. Inclua `verificador_ambiente.c`
> (já pronto, na mesma pasta).
>
> Trate exatamente estes comandos e devolva `false` para todos os outros:
>
> | nº | comando | o que fazer |
> |----|---------|-------------|
> | 2  | GET_OVERSCAN | `*(bool*)data = false` |
> | 3  | GET_CAN_DUPE | `*(bool*)data = true` |
> | 9  | GET_SYSTEM_DIRECTORY | caminho da pasta `system` do app |
> | 10 | SET_PIXEL_FORMAT | aceite `XRGB8888` (1) e `RGB565` (2); recuse `0RGB1555` (0) |
> | 11 | SET_INPUT_DESCRIPTORS | aceite e ignore |
> | 15 | GET_VARIABLE | devolva `false` por enquanto |
> | 16 | SET_VARIABLES | guarde a lista, ela vira a tela de opções depois |
> | 17 | GET_VARIABLE_UPDATE | `*(bool*)data = false` |
> | 18 | SET_SUPPORT_NO_GAME | aceite |
> | 27 | GET_LOG_INTERFACE | **implemente de verdade**, mandando para o logcat |
> | 31 | GET_SAVE_DIRECTORY | pasta de saves do app |
> | 32 | SET_SYSTEM_AV_INFO | reconfigure vídeo e áudio |
> | 35 | SET_CONTROLLER_INFO | aceite e ignore |
> | 37 | SET_GEOMETRY | mude só a geometria, sem mexer no áudio |
> | 52 | GET_CORE_OPTIONS_VERSION | `*(unsigned*)data = 0` |
>
> No `default:`, chame `registrar_comando_nao_tratado(cmd)` antes de devolver
> `false`.
>
> Atenção ao mascaramento: comandos experimentais chegam com o bit `0x10000`
> ligado, então `36 | RETRO_ENVIRONMENT_EXPERIMENTAL` **não** é igual a `36`.
> Compare sempre `cmd & 0xFFFF`.
>
> O comando 27 é o de maior retorno: implementado, o próprio núcleo passa a
> contar o que está fazendo de errado, no seu logcat.

**Verificação:** carregue o núcleo falso e chame `retro_load_game`. Tem de
devolver `true` e o logcat tem de mostrar `SET_PIXEL_FORMAT` aceito. Nenhum
comando não tratado deve aparecer com o núcleo falso — se aparecer, o falso
está pedindo algo que a tabela não cobre.

---

## Prompt 3 — vídeo

> Escreva `video.cpp`/`.h` e a parte Kotlin que exibe os quadros.
>
> - `SurfaceView` com `ANativeWindow` (via `ANativeWindow_fromSurface`);
> - converta o buffer do núcleo para o formato da janela respeitando o
>   **pitch**: o núcleo entrega `pitch` em BYTES por linha, que **não** é
>   necessariamente `largura * bytes_por_pixel`. Copie linha a linha usando o
>   pitch de origem e o `stride` de destino, nunca um `memcpy` do bloco inteiro;
> - suporte `XRGB8888` e `RGB565`;
> - se `data == NULL` no callback de vídeo, isso significa "repita o quadro
>   anterior" (é o `GET_CAN_DUPE` que você aceitou). Não desenhe lixo nem pule;
> - mantenha a proporção do `aspect_ratio` do `av_info`, com barras pretas.

**Verificação, olhando a tela com o núcleo falso rodando:**

| o que você vê | o que está errado |
|---|---|
| cores trocadas (vermelho no canto azul) | formato de pixel / ordem dos canais |
| imagem torta na diagonal | pitch ignorado |
| borda amarela incompleta | recorte ou geometria |
| contador pulando números | quadros sendo perdidos |
| contador repetindo | quadros duplicados |

Os cantos são vermelho (superior esquerdo), verde (superior direito), azul
(inferior esquerdo) e branco (inferior direito). Decore essa ordem.

---

## Prompt 4 — áudio

> Escreva `audio.cpp`/`.h` usando Oboe. Inclua `verificador_audio.h`.
>
> - ring buffer lock-free de produtor único e consumidor único, dimensionado
>   para ~4 quadros de áudio (a 48000 Hz e 60 fps, 800 quadros por vez → 3200);
> - `AudioStreamBuilder` com `PerformanceMode::LowLatency`, `SharingMode::Exclusive`,
>   formato `I16`, 2 canais, taxa vinda do `av_info` do núcleo;
> - o callback do Oboe roda numa thread de tempo real: **nada de `malloc`,
>   `lock`, log ou JNI lá dentro**. Só ler do ring buffer;
> - chame `audio_saude_produziu(...)` ao escrever e `audio_saude_consumiu(...)`
>   ao ler, e `audio_saude_relatar()` uma vez por segundo, de outra thread.

**Verificação:** com o núcleo falso tocando, você deve ouvir um **Lá contínuo
e limpo**, com um clique por segundo. Depois de 60 segundos, o logcat tem de
dizer `underruns=0 overruns=0` e a deriva tem de ficar perto de zero — e,
principalmente, **não crescer** com o tempo.

Se estalar, o relatório já diz a causa: ocupação baixa é buffer pequeno ou
núcleo lento; ocupação alta é o inverso; deriva que cresce é taxa de
amostragem errada.

---

## Prompt 5 — pacing

> Escreva o laço de execução em `emulador.cpp`.
>
> **O áudio manda no relógio, não o vsync.** O núcleo produz exatamente
> `taxa_audio / fps` amostras por quadro; chame `retro_run()` quando o ring
> buffer tiver espaço para mais um quadro, e não a cada vsync.
>
> O motivo: a tela do aparelho roda a 60.0, 90 ou 120 Hz, e o SNES roda a
> 60.098 Hz. Amarrar no vsync faz o áudio derivar alguns milissegundos por
> minuto — soa perfeito nos primeiros segundos e vira estalo periódico depois.
> Amarrar no áudio faz o vídeo repetir ou pular um quadro de vez em quando, que
> ninguém percebe.
>
> - laço numa thread própria, nunca na UI;
> - pause de verdade no `onPause` (pare o laço e o stream Oboe);
> - fast forward = deixar rodar sem esperar o buffer, com o áudio descartado ou
>   silenciado.

**Verificação:** deixe 5 minutos rodando. A deriva no relatório tem de ficar
estável. Se ela cresce linearmente, o relógio ainda está no vsync.

---

## Prompt 6 — entrada

> Ligue o mapeamento de controle que já existe (`data/Controles.kt`,
> `BotaoVirtual`, e o mapa em `Preferencias`) ao `retro_set_input_state`.
>
> - a Activity coleta o estado dos botões e passa ao nativo por JNI;
> - o nativo mantém um array simples de 12 posições e o callback só lê dele;
> - **não chame JNI de dentro do callback de input**: ele roda dentro do
>   `retro_run()`, que está na thread do emulador;
> - a ordem dos `RETRO_DEVICE_ID_JOYPAD_*` não é a mesma da enum `BotaoVirtual`
>   — escreva a tradução explícita, uma linha por botão.

**Verificação:** com o núcleo falso, **segurar o botão A congela o contador**.
Soltou, volta a andar. É o caminho inteiro — Kotlin, JNI, núcleo — num teste só.

---

## Prompt 7 — o núcleo de verdade (aqui os jogos abrem)

> Troque o núcleo falso pelo Mesen (NES) e Mesen-S (SNES).
>
> - baixe os `.so` arm64 do buildbot da libretro;
> - **rode o `CONFERIR-ALINHAMENTO.sh` em cada um antes de tentar**. Núcleos
>   pré-compilados nem sempre vêm alinhados a 16 KB, e no Android 15+ o
>   `dlopen` falha com uma mensagem que não menciona alinhamento;
> - carregue a ROM pela SAF. Atenção: `need_fullpath` do `retro_get_system_info`
>   decide tudo. Se for `false`, leia o arquivo inteiro para memória e passe o
>   ponteiro em `retro_game_info.data`. Se for `true`, o núcleo quer um caminho
>   de sistema de arquivos — e um `content://` da SAF **não serve**; copie para
>   o diretório do app antes;
> - escolha o núcleo pela extensão, reaproveitando o `Sistema` de `Modelos.kt`.

Aqui o jogo abre. Se vídeo, áudio, pacing e entrada passaram nos verificadores,
o que sobra de risco é o núcleo em si — e não o seu frontend.

---

## Prompt 8 — save states, fast forward, rewind

> Com `retro_serialize_size`, `retro_serialize` e `retro_unserialize`:
>
> - save states com slots e miniatura do quadro atual;
> - rewind = buffer circular de estados, um a cada N quadros. Meça o
>   `retro_serialize_size` primeiro: se for grande, guarde menos amostras. Não
>   fixe N no código antes de ver o número;
> - fast forward conforme o prompt 5.
>
> Um save state guarda o estado **depois** do último quadro desenhado, então
> restaurar retoma no quadro seguinte ao salvo. Isso é o correto, não um bug —
> o teste do núcleo falso mede exatamente isso.

---

## Quando me chamar

Não para erro de compilação — esses o laço com o modelo local resolve melhor.
Vale me chamar quando:

- um verificador falhar e a causa não for óbvia depois de uma tentativa;
- o áudio derivar mesmo com o pacing pelo áudio;
- o `dlopen` do núcleo real falhar com o alinhamento já conferido;
- for decidir como expor shaders e proporções (li as opções do núcleo em tempo
  de execução pelo `SET_VARIABLES`, e é uma decisão de arquitetura, não de código).

Mande só o trecho e o log, nunca o arquivo inteiro.
