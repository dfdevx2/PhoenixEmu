# Fase 4 — sete etapas, todas testadas no Odin 3

Cada etapa = **um prompt, um build, um teste no aparelho**. O Cline mesmo roda
o build e instala no Odin 3 (conectado no USB). Você só testa a lista da
etapa e decide se passa.

**Os jogos de verdade abrem na etapa 5.** As quatro primeiras constroem o
emulador em cima do núcleo falso — cada uma com algo que você vê ou ouve na
tela — para que, na 5, o único suspeito que sobre seja o núcleo real.

---

## O ciclo de cada etapa

**1. Ponto de retorno** (antes de colar o prompt):
```bash
git add -A
git commit -m "before stage N"
```

**2. Cole o prompt numa tarefa NOVA do Cline** (modo Act). Tarefa nova a cada
etapa: o contexto começa limpo e sobra janela para o código.

**3. O Cline edita, compila e instala.** Quando ele terminar, confira o
`git status` que ele mostrou: **só podem aparecer os arquivos da lista da
etapa.** Apareceu outro arquivo?
```bash
git restore caminho/do/arquivo
```

**4. Teste no Odin 3** com a lista da etapa. Deixe o log aberto num terminal:
```bash
adb logcat -c
adb logcat -v brief -s PhoenixCore PhoenixLibretro PhoenixAudio PhoenixEmu AndroidRuntime DEBUG
```

**5. Passou tudo:**
```bash
git add -A
git commit -m "stage N ok"
```
**Algo falhou:** peça ao Cline para corrigir colando o item que falhou e o
log. Se na segunda tentativa não resolver, volte ao ponto de retorno e me
chame com o item e o log:
```bash
git reset --hard
git clean -fd
```

---

## Antes da etapa 1 (uma vez só)

1. No Android Studio: **Tools → SDK Manager → SDK Tools**, marque **NDK (Side by
   side)** e **CMake 3.22.1**.
2. Aplique o pacote de ligação que eu mandei (ele liga o módulo `:emulator`,
   põe o núcleo falso para compilar sozinho e cria o `.clinerules`).
3. Compile e instale:
   ```bash
   ./gradlew :app:installDebug
   ```
4. No Odin 3: **Sobre → Testar núcleo falso**. Ainda não faz nada além de abrir
   uma tela preta — é só a prova de que o módulo nativo compila e o app abre a
   Activity. Aperte voltar.

Se isto não compilar, me chame antes de colar a etapa 1: é problema da minha
ligação, não do Cline.

---

## Etapa 1 — carregar o núcleo falso

**ARQUIVOS PERMITIDOS:**
`emulator/src/main/cpp/libretro_core.h`, `libretro_core.cpp`, `jni_bridge.cpp`,
`ambiente.cpp` (novo), `CMakeLists.txt` (só acrescentar `ambiente.cpp` à lista
de fontes), `emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt`,
`EmulatorActivity.kt`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/CMakeLists.txt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt e o arquivo novo `emulator/src/main/cpp/ambiente.cpp`.
>
> Objetivo: carregar um núcleo libretro, iniciar sem jogo e mostrar as
> informações dele na tela. Ainda NÃO implemente vídeo, áudio, laço nem
> entrada — não toque em video_renderer.cpp nem audio_output.cpp.
>
> **libretro_core.h / .cpp** (classe `phoenix::LibretroCore`, mantenha o nome):
> - inclua `libretro.h` (já está na pasta) e troque os typedefs locais pelos
>   tipos reais do cabeçalho;
> - resolva as 25 funções da API com `dlsym`. Se faltar alguma, registre o
>   NOME dela no log (tag `PhoenixCore`) e falhe;
> - mantenha `dlopen(..., RTLD_NOW | RTLD_LOCAL)`;
> - recuse o núcleo se `retro_api_version() != 1`;
> - em `iniciar()`: chame `retro_set_environment` ANTES de `retro_init`, depois
>   instale os callbacks de vídeo, áudio, input_poll e input_state (por
>   enquanto podem ser funções vazias; input_state devolve 0);
> - um método `bool carregarJogo(const void* dados, size_t tamanho, const char* caminho)`
>   que monta `retro_game_info` e chama `retro_load_game`. Sem jogo, passe
>   `nullptr` para `retro_load_game` (o núcleo falso aceita);
> - um método que devolve um texto com: `library_name`, `library_version`,
>   largura x altura, fps, taxa de áudio e o formato de pixel aceito.
>
> **ambiente.cpp**: a função `retro_environment_t` do frontend. Faça
> `#include "verificador_ambiente.c"` (só neste arquivo). Compare sempre
> `cmd & 0xFFFF`. Trate exatamente:
>
> | nº | comando | o que fazer |
> |---|---|---|
> | 2 | GET_OVERSCAN | `*(bool*)data = false`, true |
> | 3 | GET_CAN_DUPE | `*(bool*)data = true`, true |
> | 9 | GET_SYSTEM_DIRECTORY | `*(const char**)data` = pasta de sistema, true |
> | 10 | SET_PIXEL_FORMAT | aceite 1 (XRGB8888) e 2 (RGB565) e guarde; recuse 0 |
> | 11 | SET_INPUT_DESCRIPTORS | true |
> | 15 | GET_VARIABLE | false |
> | 16 | SET_VARIABLES | true |
> | 17 | GET_VARIABLE_UPDATE | `*(bool*)data = false`, true |
> | 18 | SET_SUPPORT_NO_GAME | true |
> | 27 | GET_LOG_INTERFACE | **de verdade**: função que manda para o logcat com tag `PhoenixLibretro` |
> | 31 | GET_SAVE_DIRECTORY | `*(const char**)data` = pasta de saves, true |
> | 32 | SET_SYSTEM_AV_INFO | guarde a nova info, true |
> | 35 | SET_CONTROLLER_INFO | true |
> | 37 | SET_GEOMETRY | guarde a nova geometria, true |
> | 52 | GET_CORE_OPTIONS_VERSION | `*(unsigned*)data = 0`, true |
>
> No `default:` chame `registrar_comando_nao_tratado(cmd)` e devolva false.
> Logue também, com tag `PhoenixLibretro`, quando SET_PIXEL_FORMAT for aceito.
>
> **JNI + NucleoLibretro.kt**: acrescente `nativeDefinirPastas(sistema: String, saves: String)`
> e `nativeInfo(): String`. Mude `nativeCarregarJogo` para aceitar `ByteArray?`
> (nulo = sem jogo). Mantenha as outras assinaturas como estão.
>
> **EmulatorActivity.kt**:
> - leia `EXTRA_NUCLEO`; o caminho é `applicationInfo.nativeLibraryDir + "/" + nucleo`;
> - se o arquivo não existir, mostre na tela "Núcleo não encontrado: <caminho>"
>   em vez de chamar o nativo;
> - defina as pastas (crie `filesDir/system` e `filesDir/saves`), carregue o
>   núcleo, chame `carregarJogo(null)` e mostre o texto de `nativeInfo()` em
>   branco sobre fundo preto, com Compose;
> - se algo falhar, mostre na tela qual passo falhou;
> - no `onDestroy`, se `isFinishing`: descarregue e encerre o processo com
>   `android.os.Process.killProcess(android.os.Process.myPid())` — a
>   Activity roda no processo `:emu` e o núcleo precisa começar limpo na
>   próxima vez;
> - mantenha o companion object exatamente como está.

**No Odin 3, confira:**

| teste | esperado |
|---|---|
| Sobre → Testar núcleo falso | tela preta com texto branco |
| o texto | `Phoenix Nucleo Falso`, `1.0`, `320x240`, `60`, `48000`, `XRGB8888` |
| voltar | volta ao app, sem fechar sozinho |
| abrir de novo, 3 vezes | o mesmo texto todas as vezes |
| log | uma linha dizendo que `SET_PIXEL_FORMAT` foi aceito |
| log | **nenhuma** linha `environment nao tratado` |

Se aparecer "Núcleo não encontrado", o problema é empacotamento, não código:
me chame com o caminho que apareceu.

---

## Etapa 2 — vídeo

**ARQUIVOS PERMITIDOS:** `video_renderer.cpp`, `video_renderer.h` (novo),
`jni_bridge.cpp`, `libretro_core.h`, `libretro_core.cpp`, `ambiente.cpp`,
`NucleoLibretro.kt`, `EmulatorActivity.kt`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/cpp/video_renderer.cpp @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/cpp/ambiente.cpp @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt e o novo `video_renderer.h`.
>
> Objetivo: mostrar os quadros do núcleo na tela. Não mexa em áudio.
>
> **Abordagem: ANativeWindow com cópia pela CPU. NÃO use EGL/OpenGL agora**
> (fica para quando os shaders entrarem). Atualize o comentário do topo de
> `video_renderer.cpp` dizendo isso.
>
> - `nativeIniciarLaco(surface)`: `ANativeWindow_fromSurface` e cria a thread
>   do emulador (`std::thread`). Laço PROVISÓRIO: `retro_run()` e depois
>   `std::this_thread::sleep_until` no próximo instante de `1/fps` (use
>   `steady_clock`, acumulando o alvo — não durma um intervalo fixo). A
>   etapa 3 vai trocar este ritmo;
> - callback de vídeo:
>   - `data == nullptr` significa "repita o quadro anterior": não poste nada;
>   - `ANativeWindow_setBuffersGeometry(janela, largura, altura, formato)`,
>     só quando largura/altura/formato mudarem;
>   - XRGB8888 → `WINDOW_FORMAT_RGBX_8888`, **trocando R e B em cada pixel**:
>     `dst = 0xFF000000 | ((src >> 16) & 0xFF) | (src & 0xFF00) | ((src & 0xFF) << 16)`.
>     Sem essa troca o vermelho aparece azul;
>   - RGB565 → `WINDOW_FORMAT_RGB_565`, cópia direta;
>   - **copie linha a linha**: a origem avança `pitch` BYTES por linha (não
>     largura × bytes); o destino avança `buffer.stride` PIXELS por linha.
>     Nunca um `memcpy` do bloco inteiro;
>   - `ANativeWindow_lock` / `ANativeWindow_unlockAndPost`;
> - `nativePararLaco()`: sinaliza (atomic), faz `join` na thread e **só depois**
>   libera a janela. A thread nunca pode tocar na janela depois de liberada;
> - `surfaceDestroyed` → parar; `surfaceCreated` → iniciar de novo;
> - Kotlin: `SurfaceView` centralizada num `Box` preto, com
>   `Modifier.aspectRatio(aspect_ratio do núcleo)` — barras pretas, sem esticar.
>   Exponha o aspect ratio por um `external fun`;
> - o texto de informação da etapa 1 vira uma linha pequena no canto superior.

**No Odin 3, confira** — decore a ordem dos cantos:
vermelho em cima à esquerda, verde em cima à direita, azul embaixo à
esquerda, branco embaixo à direita.

| o que você vê | se estiver errado, a causa é |
|---|---|
| cantos nas cores e lugares acima | cor trocada = R e B não trocados |
| borda amarela completa nos quatro lados | torta = pitch ignorado; cortada = geometria |
| barra branca andando lisa da esquerda para a direita | tranco = quadros perdidos |
| número grande subindo sem pular | pulando = quadros perdidos; repetindo = duplicados |
| cronometre 10 s: o número sobe ~600 | muito menos = laço lento |
| tela inteira pisca branco 1× por segundo | — |
| imagem 4:3 com barras pretas dos lados | esticada = aspectRatio |
| Home → voltar ao app, 5 vezes | a imagem volta, sem fechar sozinho |

---

## Etapa 3 — áudio e ritmo pelo áudio

**ARQUIVOS PERMITIDOS:** `audio_output.cpp`, `audio_output.h` (novo),
`CMakeLists.txt` (só as duas linhas do Oboe), `jni_bridge.cpp`,
`libretro_core.h`, `libretro_core.cpp`, `NucleoLibretro.kt`, `EmulatorActivity.kt`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/cpp/audio_output.cpp @/emulator/src/main/cpp/CMakeLists.txt @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt e o novo `audio_output.h`.
>
> Objetivo: som, e o áudio passa a mandar no ritmo do laço.
>
> - **CMakeLists.txt**: acrescente só `find_package(oboe REQUIRED CONFIG)` e
>   `oboe::oboe` em `target_link_libraries(phoenix_emu ...)`. A dependência
>   Gradle e o `prefab` já estão prontos — não mexa em nenhum .gradle.kts;
> - ring buffer lock-free, um produtor e um consumidor (`std::atomic` com
>   acquire/release), de `int16_t` estéreo intercalado, capacidade de 4
>   quadros de áudio (`taxa / fps * 4` quadros estéreo);
> - Oboe: saída, `PerformanceMode::LowLatency`, `SharingMode::Exclusive`,
>   `AudioFormat::I16`, 2 canais, taxa = `sample_rate` do núcleo, e
>   `setSampleRateConversionQuality(SampleRateConversionQuality::Medium)` —
>   assim o Oboe converte se o aparelho não rodar na taxa do núcleo;
> - o callback do Oboe só lê do ring; o que faltar vira silêncio. **Nada de
>   malloc, mutex, log ou JNI ali dentro**;
> - callback de áudio do núcleo (`audio_sample_batch` e `audio_sample`)
>   escreve no ring;
> - `#include "verificador_audio.h"`: chame `audio_saude_iniciar()` ao abrir o
>   stream, `audio_saude_produziu(...)` ao escrever, `audio_saude_consumiu(...)`
>   no callback do Oboe e `audio_saude_relatar()` uma vez por segundo **na
>   thread do emulador**, nunca no callback;
> - **ritmo**: tire o `sleep_until` da etapa 2. Agora o laço só chama
>   `retro_run()` quando o ring tiver espaço livre para pelo menos um quadro de
>   áudio (`taxa / fps` quadros); se não tiver, dorme 1 ms e confere de novo.
>   Nada de laço girando sem dormir;
> - `onPause` → para o laço e o stream; `onResume` → volta os dois.

**No Odin 3, confira:**

| teste | esperado |
|---|---|
| ao abrir | um Lá contínuo e limpo (um afinador no celular marca A4 / 440) |
| a cada segundo | um clique **junto** com o flash branco, não antes nem depois |
| estalos | nenhum |
| log depois de 60 s | `underruns=0 overruns=0` |
| log depois de 5 min | a `deriva` fica parada num valor, **não cresce** |
| número da tela | continua ~600 em 10 s |
| Home e voltar | silêncio fora do app; som volta limpo, sem rajada de estalos |

Se estalar, olhe o log antes de pedir correção: ocupação baixa = produção
lenta; ocupação alta = o inverso; deriva crescendo = taxa errada. Mande essa
linha junto.

---

## Etapa 4 — controle físico e menu de pausa

**ARQUIVOS PERMITIDOS:** `EmulatorActivity.kt`, `NucleoLibretro.kt`,
`jni_bridge.cpp`, `libretro_core.cpp`, `libretro_core.h`, `ambiente.cpp`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/ambiente.cpp
>
> Objetivo: o controle chega ao núcleo, e existe um menu de pausa.
>
> - leia `EXTRA_MAPEAMENTO`: `IntArray` de 12 keycodes na ordem de
>   `ORDEM_DO_MAPEAMENTO` (CIMA, BAIXO, ESQUERDA, DIREITA, A, B, X, Y, L, R,
>   SELECT, START). Traduza cada nome para o bit correspondente em
>   `NucleoLibretro.Botao` com uma tabela explícita, uma linha por botão;
> - `dispatchKeyEvent`: DOWN liga o bit, UP desliga; ignore repetições
>   (`repeatCount > 0`); mande a máscara com `definirBotoes(0, mascara)`;
> - `dispatchGenericMotionEvent` de joystick: `AXIS_HAT_X` / `AXIS_HAT_Y` e o
>   analógico esquerdo (`AXIS_X` / `AXIS_Y`, zona morta 0.5) também acionam as
>   direções. Muitos controles mandam o direcional como HAT, não como tecla;
> - nativo: `input_state` lê a máscara atômica. Para `RETRO_DEVICE_JOYPAD`:
>   id 256 (`RETRO_DEVICE_ID_JOYPAD_MASK`) devolve a máscara inteira; os
>   outros devolvem `(mascara >> id) & 1`. **Nada de JNI dentro do callback**;
> - em ambiente.cpp, acrescente só: `51` (GET_INPUT_BITMASKS) → true;
> - `KEYCODE_BACK` ou `KEYCODE_BUTTON_MODE` abrem um menu de pausa em Compose,
>   por cima do jogo: **Continuar**, **Reiniciar**, **Sair**. Com o menu
>   aberto, o jogo pausa (laço e áudio parados) e as teclas NÃO vão para o
>   núcleo. Voltar de novo = Continuar. Reiniciar chama `retro_reset` na
>   thread do emulador. O menu tem de ser navegável pelo D-pad.

**No Odin 3, confira:**

| teste | esperado |
|---|---|
| segurar o botão que faz o papel de A | o número **congela**; soltou, volta a andar |
| em Controles, remapear A para L1; abrir o teste de novo | agora é o L1 que congela |
| voltar o mapeamento ao padrão | volta a ser o A |
| botão Voltar | menu aparece, número e som param |
| navegar o menu só pelo D-pad | dá para escolher as três opções |
| Continuar | retoma do número onde parou |
| Reiniciar | o número volta para 0 |
| Sair | volta para a biblioteca |

O direcional e o analógico não aparecem no núcleo falso — eles são testados
na etapa 5, com jogo de verdade.

---

## Etapa 5 — os jogos abrem

**Antes de colar o prompt, você baixa os núcleos (manual):**

1. Do buildbot da libretro, pasta de Android **arm64-v8a**
   (`buildbot.libretro.com/nightly/android/latest/arm64-v8a/`), baixe
   `mesen_libretro_android.so.zip` e `bsnes_libretro_android.so.zip`.
   Confira o nome exato lá — às vezes muda.
2. Extraia e **renomeie**: `libmesen.so` e `libbsnes.so`. O Android só instala
   `.so` cujo nome começa com `lib`; com o nome original, o arquivo é
   ignorado sem aviso.
3. Confira o alinhamento de cada um:
   ```bash
   fase4/nucleo_falso/CONFERIR-ALINHAMENTO.sh libmesen.so
   fase4/nucleo_falso/CONFERIR-ALINHAMENTO.sh libbsnes.so
   ```
   REPROVADO = não carrega no Odin 3. Me chame com a saída.
4. Coloque os dois em `emulator/src/main/jniLibs/arm64-v8a/` (crie a pasta).

**ARQUIVOS PERMITIDOS:** `EmulatorActivity.kt`, `NucleoLibretro.kt`,
`jni_bridge.cpp`, `libretro_core.cpp`, `libretro_core.h`, `ambiente.cpp` e
**uma** mudança exata em `app/.../MainActivity.kt`, descrita no prompt.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/ambiente.cpp e, SÓ para a mudança descrita no fim, @/app/src/main/java/com/dfdx047/phoenixemu/MainActivity.kt
>
> Objetivo: abrir ROMs de verdade com o Mesen e o bsnes.
>
> - `EXTRA_ROM` traz uma URI `content://`. Leia os bytes com
>   `contentResolver.openInputStream`. Se a extensão for `.zip`, use
>   `java.util.zip.ZipInputStream` e pegue a primeira entrada `.nes`, `.sfc`
>   ou `.smc`;
> - consulte `need_fullpath` em `retro_get_system_info`:
>   - `false`: passe os bytes em `retro_game_info.data/size`;
>   - `true`: grave os bytes em `cacheDir` com a extensão original e passe o
>     **caminho** em `retro_game_info.path`. Uma URI `content://` nunca serve
>     como caminho;
> - **SRAM (save do próprio jogo)**: logo depois do `retro_load_game`, se
>   existir `saves/<EXTRA_NOME_SAVE>.srm`, copie para
>   `retro_get_memory_data(0)` (0 = `RETRO_MEMORY_SAVE_RAM`), no máximo
>   `retro_get_memory_size(0)` bytes. Grave o `.srm` de volta ao pausar, ao
>   sair e a cada 30 s. Sem isto, o save feito dentro do jogo some ao fechar;
> - se `retro_load_game` falhar, mostre o motivo na tela em vez de fechar.
>
> **Única mudança no app**, em MainActivity.kt. Troque exatamente este trecho:
> ```kotlin
>         jogoParaJogar = null
>         hostDeSnackbar.showSnackbar(aindaSemNucleo)
> ```
> por:
> ```kotlin
>         jogoParaJogar = null
>         Emulador.abrirJogo(context, prefs, jogo)
> ```
> e acrescente `import com.dfdx047.phoenixemu.emulacao.Emulador` junto aos
> outros imports `com.dfdx047`. Não mude mais nada neste arquivo.

**No Odin 3, confira:**

| teste | esperado |
|---|---|
| tocar num jogo de NES | abre, com imagem e som |
| um jogo de SNES | idem |
| direcional, analógico e todos os botões | respondem no jogo |
| Star Fox, Yoshi's Island ou Kirby Super Star | sem lentidão; log com `underruns=0` |
| jogo com save de bateria: salve dentro do jogo, Sair, abra de novo | o save está lá |
| um jogo `.zip` | abre |
| log `environment nao tratado` | **me mande só essa lista** — é aqui que o núcleo real pede coisas que o falso não pedia |

---

## Etapa 6 — save states, avanço rápido e rewind

**ARQUIVOS PERMITIDOS:** `EmulatorActivity.kt`, `NucleoLibretro.kt`,
`jni_bridge.cpp`, `libretro_core.cpp`, `libretro_core.h`, `audio_output.cpp`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt @/emulator/src/main/cpp/jni_bridge.cpp @/emulator/src/main/cpp/libretro_core.cpp @/emulator/src/main/cpp/libretro_core.h @/emulator/src/main/cpp/audio_output.cpp
>
> - **Toda chamada a `retro_serialize` / `retro_unserialize` roda na thread do
>   emulador**, entre dois `retro_run`. A UI só enfileira o pedido (fila com
>   `std::atomic` ou mutex fora do callback de áudio). Chamar da UI enquanto o
>   quadro roda corrompe o estado;
> - save states: 4 slots em `saves/<EXTRA_NOME_SAVE>.state1..4`, com uma
>   miniatura PNG do último quadro e a data. No menu de pausa: **Salvar
>   estado** e **Carregar estado**, mostrando os 4 slots com miniatura;
> - avanço rápido: segurar `KEYCODE_BUTTON_R2`. O laço roda sem esperar o ring
>   e o áudio é descartado enquanto durar. Mostre "⏩" no canto;
> - rewind: segurar `KEYCODE_BUTTON_L2`. Guarde um estado a cada 2 quadros num
>   buffer circular. **Antes de escolher o tamanho, logue
>   `retro_serialize_size()`** e limite o buffer a 64 MB — o número de estados
>   sai dessa conta, não de um valor fixo. Enquanto L2 estiver segurado,
>   restaure um estado por quadro, do mais novo para o mais velho;
> - um save state guarda o estado DEPOIS do último quadro desenhado: restaurar
>   retoma no quadro seguinte ao salvo. Isso é o correto.

**No Odin 3, confira** (primeiro com o núcleo falso, depois num jogo):

| teste | esperado |
|---|---|
| núcleo falso: salvar no slot 1, esperar, carregar | o número volta para o salvo **+1** |
| fechar o app, abrir, carregar o slot 1 | funciona igual |
| miniaturas | aparecem nos slots usados |
| segurar R2 | número dispara; som some; ⏩ aparece |
| segurar L2 | número anda para trás, liso |
| log | o `retro_serialize_size` do núcleo — me mande o de cada núcleo |
| num jogo: salvar, morrer, carregar | volta para antes de morrer |

---

## Etapa 7 — controle na tela

**ARQUIVOS PERMITIDOS:** `EmulatorActivity.kt`, `NucleoLibretro.kt`.

> Siga o `.clinerules`. Arquivos permitidos nesta etapa: @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt @/emulator/src/main/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt
>
> - leia `EXTRA_OVERLAY` com `org.json.JSONObject` (formato descrito no
>   comentário da constante). `x` e `y` são frações da tela inteira; o
>   tamanho do botão é `34.dp * escala`; a transparência é `opacidade`;
> - desenhe os botões em Compose por cima da `SurfaceView`, só se `visivel`;
> - **multitoque de verdade**: acompanhe todos os ponteiros
>   (`awaitPointerEventScope`), e cada ponteiro dentro de um botão liga o bit
>   dele. Segurar direção e apertar A ao mesmo tempo tem de funcionar;
> - a máscara final é **OU** entre controle físico e toque;
> - com o menu de pausa aberto, o overlay some.

**No Odin 3, confira:**

| teste | esperado |
|---|---|
| botões | nos mesmos lugares do editor da aba Controles |
| opacidade e tamanho | iguais aos do editor |
| segurar direção + apertar A | os dois ao mesmo tempo |
| desligar "Mostrar controles na tela" | somem |
| usar o controle físico junto | os dois funcionam |

---

## Quando me chamar

- a ligação ("Antes da etapa 1") não compilar;
- um item de teste falhar e a segunda tentativa do Cline não resolver;
- a lista de `environment nao tratado` da etapa 5;
- os valores de `retro_serialize_size` da etapa 6;
- quando for decidir shaders e proporções.

Mande o item que falhou e o trecho do log. Não precisa mandar o arquivo.
