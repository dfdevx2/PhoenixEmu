#!/usr/bin/env bash
# Auditoria do PhoenixEmu (Fases 0, 1A, 3 a 3.3). Rode na raiz do projeto.
# Nao altera nada, exceto se voce passar --corrigir.

# Roda no diretorio ATUAL: chame-o da raiz do projeto.
if [ ! -f settings.gradle.kts ]; then
  echo "Rode este script na raiz do projeto (onde esta o settings.gradle.kts)."; exit 1
fi
FALHAS=0
ok()   { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
falta(){ printf '  \033[31mFALTA\033[0m %s\n' "$1"; FALHAS=$((FALHAS+1)); }
pend() { printf '  \033[33mPEND\033[0m  %s\n' "$1"; FALHAS=$((FALHAS+1)); }
tit()  { printf '\n\033[1m%s\033[0m\n' "$1"; }

J=app/src/main/java/com/dfdx047/phoenixemu
E=emulator/src/main

tit "1. ARQUIVOS ESPERADOS"
for f in \
  settings.gradle.kts gradle.properties gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/AndroidManifest.xml app/src/main/keepRules/rules.keep \
  app/src/main/res/values/strings.xml app/src/main/res/values/themes.xml \
  app/src/main/res/values/colors_phoenix.xml app/src/main/res/values-night/themes.xml \
  app/src/main/res/values-night/colors_phoenix.xml app/src/main/res/values-pt/strings.xml \
  $J/AudioEngine.kt $J/MainActivity.kt $J/Modelos.kt $J/PhoenixApplication.kt \
  $J/ScrapeEngine.kt $J/TelasSecundarias.kt \
  $J/data/BibliotecaStore.kt $J/data/CapaWorker.kt $J/data/HashWorker.kt \
  $J/data/Preferencias.kt $J/data/RomHasher.kt $J/data/RomScanner.kt $J/data/Trabalhos.kt \
  $J/data/JogoEntity.kt $J/data/JogoDao.kt $J/data/PhoenixDatabase.kt \
  $J/ui/design/Componentes.kt $J/ui/design/Fundo.kt $J/ui/design/Vidro.kt \
  $J/ui/design/Rolagem.kt $J/ui/design/Controle.kt $J/ui/design/Navegacao.kt \
  $J/ui/telas/TelaControles.kt $J/ui/telas/BoasVindas.kt \
  $J/data/Controles.kt $J/data/Idioma.kt \
  $J/ui/theme/Theme.kt \
  emulator/build.gradle.kts $E/AndroidManifest.xml \
  $E/cpp/CMakeLists.txt $E/cpp/audio_output.cpp $E/cpp/jni_bridge.cpp \
  $E/cpp/libretro_core.cpp $E/cpp/libretro_core.h $E/cpp/video_renderer.cpp \
  $E/java/com/dfdx047/phoenixemu/emulator/EmulatorActivity.kt \
  $E/java/com/dfdx047/phoenixemu/emulator/NucleoLibretro.kt
do [ -f "$f" ] && ok "$f" || falta "$f"; done

# Type.kt e Color.kt sao arquivos ORIGINAIS do projeto, nunca substituidos
# pelas fases -- por isso ficam numa checagem separada. O Theme.kt usa
# `Typography`, que mora no Type.kt: se ele desaparecer, o build quebra com
# "Unresolved reference 'Typography'" e o erro nao aponta para ca.
for f in $J/ui/theme/Type.kt; do
  [ -f "$f" ] && ok "$f (original, define Typography)" \
    || falta "$f AUSENTE - o Theme.kt nao compila sem ele"
done

tit "2. NAO PODE EXISTIR"
if [ -d app/src/main/res/values-pt-rBR ]; then
  if [ "$1" = "--corrigir" ]; then
    rm -rf app/src/main/res/values-pt-rBR && ok "values-pt-rBR/ apagada agora"
  else
    pend "values-pt-rBR/ ainda existe (rode com --corrigir, ou apague a mao)"
  fi
else ok "values-pt-rBR/ ausente"; fi

tit "3. CODIGO ANTIGO (regressoes) - ignora comentarios"
for s in COLOQUE_SUA_API_KEY_AQUI 'window.statusBarColor' BibliotecaManager DocumentFile \
         'store.gravar' 'getSharedPreferences("EmulatorSettings"' \
         'Icons.Default.ArrowBack' 'Icons.Default.Sort' 'Icons.Default.Logout' \
         'TabRow(' 'ElevatedCard(' procurarNovos BUSCANDO_CAPAS rememberPagerState \
         'TemaApp.CLARO' 'TemaApp.ESCURO' 'TemaApp.AMOLED' \
         'nativeKeyEvent' 'focusGroup'
do
  hits=$(grep -rn -F "$s" "$J" 2>/dev/null | grep -v ':[[:space:]]*//' | grep -v ':[[:space:]]*\*')
  if [ -z "$hits" ]; then ok "ausente: $s"
  else falta "AINDA PRESENTE: $s"; echo "$hits" | sed 's/^/        /'; fi
done

# O nome antigo pode sobrar em strings e regras, nao so no codigo Kotlin --
# por isso esta busca e no projeto inteiro, e nao dentro de $J.
# --exclude-dir=build e obrigatorio: o app/build guarda os recursos MESCLADOS
# da compilacao anterior, com os textos como eram ANTES da correcao. Sem isto o
# script acusa um nome antigo que ja nao existe em nenhum fonte.
antigo=$(grep -rn "Project Phoenix" --include="*.kt" --include="*.kts" \
  --include="*.xml" --include="*.keep" --include="*.properties" \
  --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.git \
  --exclude-dir=.idea --exclude-dir=.kotlin . 2>/dev/null)
if [ -z "$antigo" ]; then ok "ausente no projeto inteiro: \"Project Phoenix\""
else falta "nome antigo \"Project Phoenix\" ainda aparece:"; echo "$antigo" | sed 's/^/        /'; fi

tit "4. MARCADORES OBRIGATORIOS"
chk(){ grep -rqF "$2" "$1" 2>/dev/null && ok "$1: $2" || falta "$1: $2"; }
grep -A3 'optimization {' app/build.gradle.kts 2>/dev/null | grep -q 'enable = true' \
  && ok "app/build.gradle.kts: R8 ligado" || falta "app/build.gradle.kts: R8 ligado"
chk app/build.gradle.kts 'buildConfig = true'
chk app/build.gradle.kts 'RAWG_API_KEY'
chk gradle/libs.versions.toml 'lifecycle-process'
chk gradle/libs.versions.toml 'work-runtime-ktx'
chk gradle/libs.versions.toml 'core-splashscreen'
chk gradle/libs.versions.toml 'android-library'
chk app/src/main/AndroidManifest.xml '.PhoenixApplication'
chk $J/MainActivity.kt 'enableEdgeToEdge()'
chk $J/MainActivity.kt 'PilulaDeNavegacao'
chk $J/MainActivity.kt 'SeletorSegmentado'
chk $J/ui/theme/Theme.kt 'LocalVidro provides'
chk app/src/main/keepRules/rules.keep 'androidx.work.ListenableWorker'
chk app/build.gradle.kts 'libs.plugins.ksp'
chk app/build.gradle.kts 'room.schemaLocation'
chk gradle/libs.versions.toml 'room-compiler'
chk gradle/libs.versions.toml 'datastore-preferences'
chk gradle.properties 'android.disallowKotlinSourceSets=false'
chk $J/data/BibliotecaStore.kt 'jogosVisiveis'
# --- Fase 3.3: navegacao por controle, aba de controles e boas-vindas
chk $J/MainActivity.kt 'Secao.CONTROLES'
chk $J/MainActivity.kt 'NavegacaoDeLista.interpretar'
chk $J/MainActivity.kt 'attachBaseContext'
chk $J/MainActivity.kt 'collectIsDraggedAsState'
chk $J/MainActivity.kt 'primeiraExecucaoConcluida'
chk $J/ui/design/Navegacao.kt 'Acao.LIMITE'
chk $J/ui/design/Componentes.kt 'LocalContentColor provides'
chk $J/data/Preferencias.kt 'BGM_PADRAO'
chk $J/data/Controles.kt 'KEYCODE_BUTTON_B'
chk app/src/main/keepRules/rules.keep 'ConfigDoOverlay'
chk app/src/main/res/values/strings.xml 'boasvindas_comecar'
chk app/src/main/res/values-pt/strings.xml 'boasvindas_comecar'
# O onKeyEvent tem de estar ACIMA do focusable na arvore. Se alguem inverter
# isso, o controle para de funcionar na grade e o sintoma nao aponta para ca.
if grep -q 'focusable()$' "$J/MainActivity.kt" && \
   [ "$(grep -n 'onKeyEvent' "$J/MainActivity.kt" | head -1 | cut -d: -f1)" -lt \
     "$(grep -n 'focusRequester(focoDaLista)' "$J/MainActivity.kt" | head -1 | cut -d: -f1)" ]; then
  ok "MainActivity.kt: onKeyEvent antes do alvo de foco"
else
  falta "MainActivity.kt: onKeyEvent DEPOIS do focusable -- o D-pad nao chega na grade"
fi
# Paridade de strings entre os dois idiomas.
en=$(grep -c '<string name=' app/src/main/res/values/strings.xml 2>/dev/null)
pt=$(grep -c '<string name=' app/src/main/res/values-pt/strings.xml 2>/dev/null)
[ "$en" = "$pt" ] && ok "strings: $en em ingles e $pt em portugues" \
  || falta "strings: $en em ingles mas $pt em portugues"

tit "5. ACOES HUMANAS"
grep -qF 'ko-fi.com/dfdx047' "$J/TelasSecundarias.kt" 2>/dev/null \
  && pend "URL_KOFI ainda e o placeholder (troque pelo seu handle)" \
  || ok "URL_KOFI personalizada"
[ -f "$E/cpp/libretro.h" ] && ok "libretro.h presente" \
  || pend "libretro.h ausente (so e necessario na Fase 4)"
if grep -qE '^\s*include\("' settings.gradle.kts 2>/dev/null && \
   grep -qE '^\s*include\(":emulator"\)' settings.gradle.kts; then
  falta "include(\":emulator\") ATIVO - quebra o build sem NDK"
else ok "include(\":emulator\") comentado"; fi
grep -qs 'RAWG_API_KEY' "$HOME/.gradle/gradle.properties" \
  && ok "RAWG_API_KEY em ~/.gradle/gradle.properties" \
  || pend "RAWG_API_KEY ausente em ~/.gradle/gradle.properties"

printf '\n\033[1mRESUMO:\033[0m %s item(ns) exigindo atencao.\n' "$FALHAS"
