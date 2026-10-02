<p align="center">
  <img src="docs/img/logo.png" alt="Phoenix Emu" width="320">
</p>

<h1 align="center">Phoenix Emu</h1>

<p align="center">
  <b>Um emulador de NES e SNES gratuito para Android, feito para portáteis e controles.</b><br>
  Biblioteca no estilo de console, interface de vidro com temas, save states, rewind, atalhos e muito mais.
</p>

<p align="center">
  <a href="README.md">English</a> · Português (Brasil)
</p>

> **Status: pré-lançamento, desenvolvimento privado.** O Phoenix Emu está sendo feito e testado principalmente no **AYN Odin 3** (horizontal, com controle). O modo vertical ainda não foi testado. O repositório será aberto quando o emulador estiver pronto para a primeira versão pública.

---

## Capturas de tela

<table>
  <tr>
    <td><img src="docs/img/carousel.png" alt="Biblioteca em carrossel"><br><sub>Biblioteca, modo carrossel (XMB)</sub></td>
    <td><img src="docs/img/grid.png" alt="Biblioteca em grade"><br><sub>Biblioteca, modo grade</sub></td>
  </tr>
  <tr>
    <td><img src="docs/img/settings.png" alt="Ajustes"><br><sub>Ajustes, organizados em abas com dicas de ajuda</sub></td>
    <td><img src="docs/img/controls.png" alt="Controles"><br><sub>Controles: mapeamento do gamepad, na tela e atalhos</sub></td>
  </tr>
</table>

---

## Recursos

### Emulação
- **NES** e **SNES** por núcleos [libretro](https://www.libretro.com/) (Mesen para NES, bsnes para SNES), rodando em um processo separado para que a interface nunca dispute tempo com o laço de emulação.
- Áudio de baixa latência com [Oboe](https://github.com/google/oboe); o laço de emulação é guiado pelo relógio do áudio.
- **Save states** com 4 slots por jogo e miniatura em cada slot.
- **Auto-save ao sair / auto-load ao entrar** (cada um pode ser desligado).
- **Avanço rápido** e **rewind** (buffer circular de estados recentes), por padrão em R2 e L2.
- **Motor de atalhos**: toda função extra (salvar/carregar estado, slot anterior/próximo, avançar, voltar, menu, reiniciar) pode ser ligada a qualquer botão, com um botão de "atalho" opcional para combos no estilo RetroArch (segure o botão de atalho e aperte outro).
- Opções de proporção de tela e de filtro de vídeo.

### Biblioteca
- Varre as pastas que você escolher e monta a biblioteca sozinha.
- Duas visões: **carrossel** (estilo menu de console, capas grandes, barra superior fixa e navegação flutuante) e **grade** clássica. Você escolhe qual usar.
- Busca, filtro por sistema (NES / SNES), recentes, favoritos e ordenação.
- Capas e informações buscadas online, selos de região (USA / EUR / JPN) e identificação da ROM por CRC32.
- Totalmente navegável com controle: direcional/analógico para mover, **A** para jogar, **Y** para opções, **L1 / R1** para trocar de seção.

### Interface
- **Interface de vidro** com desfoque ao vivo (Android 12+) e alternativa mais leve para aparelhos antigos ou fracos; ou acabamento **sólido**, se preferir.
- Temas: Material You (acompanha o sistema), **NES (EUA)**, **Famicom (Japão)**, **SNES (EUA)**, **Super Famicom (Japão)** e a opção de preto absoluto **AMOLED**.
- Papel de parede próprio com desfoque e opacidade ajustáveis, modo "reduzir efeitos" para aparelhos mais fracos.
- Português e inglês, com **balão de ajuda "?" em cada opção**.
- Ajustes organizados em abas (Aparência, Áudio, Vídeo, Jogo, Navegação, Manutenção).
- *Tudo é opção, nada é regra*: cada comportamento novo tem a sua própria chave de ligar/desligar.

### Controles
- Remapeamento do gamepad físico.
- Controle na tela com editor: arraste cada botão, ajuste opacidade e tamanho.
- Aba de atalhos com botão de atalho configurável.

---

## Roteiro

Trabalho planejado, mais ou menos nesta ordem:

| Área | O que vem por aí |
|---|---|
| **Menu do jogo** | Menu lateral com o tema do app dentro do jogo (deslizar ou Voltar): gerenciador de save states, ajustes, filtros, remapeamento de controles, informações do jogo. |
| **Avanço rápido / rewind** | Velocidades configuráveis (de 2x até 16x), modos segurar ou liga/desliga, indicador na tela com o tempo de rewind restante, contador opcional de FPS e tempo por quadro. |
| **Opções dos núcleos** | Todas as opções que cada núcleo declara, montadas automaticamente, em camadas: padrão do núcleo, global e por jogo. Tipos de controle por porta, 2 jogadores, multitap, mouse, Super Scope, Zapper, cheats, overscan, volume, turbo. |
| **Vídeo em OpenGL ES** | Renderizador GLES com escala inteira, proporções, corte de overscan e filtros GLSL (sharp-bilinear, scanlines, CRT, grade LCD, estilo xBR/HQ2x, NTSC) com parâmetros ajustáveis, globais e por jogo. Run-ahead para latência quase zero. |
| **Controles na tela e skins** | Layouts fiéis ao NES/SNES, skins da comunidade (`.zip`, importadas pelo seletor de arquivos do sistema), editor completo com botões novos: combo, turbo, salvar/carregar estado, avançar, voltar, pausar, menu, reiniciar. Vibração, ocultar quando houver gamepad conectado. |
| **RetroAchievements** | Login, hash da ROM, lista de conquistas, progresso, avisos e sons durante o jogo, modo hardcore. Tela dedicada com o último jogo, recentes e a lista completa por console. |
| **Qualidade e lançamento** | Correção da afinidade de thread do emulador, meta de cerca de 2 W em um Snapdragon 8 Elite, tela de licenças GPL com oferta do código-fonte dos núcleos, backup/exportação de saves, ícone adaptativo, política de privacidade, Play Store. Avaliação do Mesen2 como núcleo alternativo de NES. |

---

## Compilando

Requisitos: Android Studio (versão estável atual), Android SDK e NDK, e um aparelho ou emulador.

```bash
git clone <este repositório>
cd PhoenixEmu
./gradlew installDebug
```

Opcional, para as capas: crie uma chave gratuita da [RAWG](https://rawg.io/apidocs) e coloque em `~/.gradle/gradle.properties` (nunca no repositório):

```properties
RAWG_API_KEY=sua_chave_aqui
```

O app **não** inclui nenhum jogo. Adicione a sua pasta de ROMs pela tela da biblioteca.

### Estrutura do projeto

| Módulo | Função |
|---|---|
| `:app` | Interface em Compose, biblioteca, ajustes, temas, preferências. |
| `:emulator` | O motor: hospedeiro libretro, ponte JNI, áudio (Oboe), activity do jogo. Roda em processo próprio (`:emu`). |

---

## Atalhos padrão do gamepad

| Botão | Ação |
|---|---|
| R2 | Avanço rápido |
| L2 | Rewind |
| L1 / R1 | Seção anterior / próxima (biblioteca) |
| A / Y | Jogar / opções (biblioteca) |

Os outros atalhos começam sem nada atribuído e são definidos em **Controles ▸ Atalhos**.

---

## Aviso legal

O Phoenix Emu é um emulador. Ele **não** inclui ROMs, BIOS ou qualquer jogo protegido por direitos autorais. Use apenas jogos que você possui e que você mesmo extraiu. "Nintendo", "NES", "Famicom", "SNES" e "Super Famicom" são marcas dos seus respectivos donos; este projeto não é afiliado nem aprovado por eles.

## Apoie o projeto

O Phoenix Emu é gratuito. O link de doação do Ko-fi será colocado aqui e no app na primeira versão pública.

## Créditos

- API e comunidade [libretro](https://www.libretro.com/)
- Núcleos [Mesen](https://github.com/SourMesen/Mesen) (NES) e [bsnes](https://github.com/bsnes-emu/bsnes) (SNES)
- [Oboe](https://github.com/google/oboe), áudio de baixa latência
- [RAWG](https://rawg.io/), dados e capas dos jogos
- [RetroAchievements](https://retroachievements.org/) e `rcheevos` (planejado)

## Licença

O Phoenix Emu é licenciado sob a **GNU General Public License v3.0**. Veja [LICENSE](LICENSE). Os núcleos libretro incluídos também são GPL.
