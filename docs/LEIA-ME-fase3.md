# Project Phoenix — Fase 3 (Vidro fosco) + esqueleto do módulo nativo

20 arquivos, em cima da Fase 1A. **Aplique a 1A primeiro** — este pacote
depende do `capaLocal`, do `ausente` e do `Trabalhos` que vieram de lá.

---

## 1. O problema que o `Modifier.blur` não resolve

Vale explicar, porque é a razão de existir de três arquivos novos.

**`Modifier.blur` do Compose desfoca o conteúdo do próprio composable, não o
que está atrás dele.** Aplicado numa pílula, ele borra o texto da pílula e
deixa o fundo nítido — exatamente o oposto de vidro fosco. O
`View.setRenderEffect` do Android sofre do mesmo problema, e o blur de janela
da API 31 só funciona para a janela inteira, o que serve a diálogos e não a
uma barra flutuante.

A técnica que funciona — e é a que bibliotecas como a Haze usam — é outra:

1. gravar o conteúdo do fundo numa `GraphicsLayer`;
2. dentro da pílula, desenhar essa mesma camada **deslocada**, de modo que a
   região correta do fundo caia sob ela;
3. envolver esse desenho numa segunda camada, que carrega o `BlurEffect`.

Escrevi isso à mão em vez de usar a Haze, contrariando o que eu mesmo tinha
sugerido na Fase 0. O motivo é prático: as APIs que uso aqui
(`GraphicsLayer`, `BlurEffect`) são estáveis e eu consigo raciocinar sobre
elas; a superfície pública da Haze mudou bastante entre versões e eu não
teria como conferir qual está valendo hoje. Se você preferir trocar depois, a
mudança é local — só `Fundo.kt` e `SuperficieDeVidro` sabem como o desfoque
acontece.

**Regra que o código respeita e que você precisa saber se for mexer:** quem
usa `fonteDeFundo` nunca pode conter uma superfície de vidro. A pílula
amostra o fundo; se estivesse dentro dele, amostraria a si mesma e viraria
realimentação. Por isso a tela tem duas camadas explícitas, comentadas no
`MainActivity`.

---

## 2. Adaptação aos 8 temas

O estilo do vidro é **derivado do `ColorScheme` ativo**, não escrito tema a
tema. Um tema novo que você criar depois ganha vidro coerente sem tocar em
`Vidro.kt`. A tinta sai da `surface`, a luz sai do `onSurface`, e a
luminância decide se o brilho é branco ou claro.

Dois temas precisaram de tratamento próprio, e os dois pelo mesmo tipo de
motivo — a regra genérica os estragava:

- **AMOLED.** A `surface` dele é cinza (`0xFF121212`). Usada como base,
  deixaria o vidro cinzento sobre o preto absoluto e mataria o propósito do
  tema. Lá a tinta é preta e quem desenha a pílula é a borda, em `primary`.
  A sombra também foi zerada: sombra preta sobre preto não existe.
- **Famicom.** Plástico creme. Vidro neutro puxa para cinza e suja o tema,
  então a tinta vem do próprio creme, clareada.

No **Snapdragon 665** provavelmente nada disso roda: `RenderEffect` só existe
a partir da API 31. Ele cai no caminho de scrim — e é por isso que o scrim foi
feito bonito e não apenas funcional: tinta mais opaca, gradiente de brilho no
topo, borda em gradiente. É um visual próprio, derivado do mesmo estilo, não
um degrade envergonhado. O toggle "Reduzir efeitos" força esse caminho em
qualquer aparelho.

---

## 3. A decisão de performance que eu tomei sozinho

**Só as pílulas flutuantes têm desfoque ao vivo. Os cartões não.**

Desfoque custa uma gravação + um blur **por superfície, por quadro**. Nas
quatro pílulas isso é constante, independente do tamanho da biblioteca. Numa
grade de cartões seria proporcional aos itens visíveis — uns 8 a 12 blurs por
quadro — e a rolagem morreria exatamente na biblioteca grande que este app
existe para aguentar. Seu critério de pronto para esta fase era 60 fps com
wallpaper animado; com blur por cartão ele não seria alcançável nem no 8
Elite.

Os cartões usam o scrim do mesmo estilo, então continuam parecendo o mesmo
material. Visualmente a diferença quase não aparece: o cartão tem a capa
ocupando quase toda a área, e o vidro só se vê na faixa do título e nas
bordas.

O código também não **aloca** uma `GraphicsLayer` quando não vai desfocar —
`SuperficieDeVidro` compõe um de dois caminhos. Sem isso, cada cartão
entrando e saindo da tela alocaria e liberaria um `RenderNode`.

---

## 4. O que mudou na interface

**O drawer saiu.** Quatro seções numa pílula flutuante embaixo: Biblioteca,
Conquistas, Ajustes, Sobre. Menos toques para chegar em qualquer lugar e,
num handheld, alcançável com o polegar e com o D-pad. O rótulo só aparece no
item ativo, o que mantém a pílula curta.

**As abas viraram um seletor segmentado** (Todos / NES / SNES) numa pílula.
Isso resolve de vez o bug nº 10: não existe mais pager externo para disputar
o gesto com o carrossel XMB. O XMB, aliás, virou `LazyRow` com snap.

**Busca.** Pílula flutuante no topo, filtrando por nome. Os botões de
sincronizar e de alternar grade/XMB moraram dentro dela.

**FAB estendido** que recolhe ao rolar.

**Navegação por controle desde o nascimento.** Todo elemento interativo tem
anel de foco visível e ordem previsível. Num Odin isso não é acessibilidade,
é o modo normal de uso — e adaptar depois custa muito mais do que nascer com.

**Configurações por jogo** deixaram de ser tela e viraram folha inferior. Com
quatro seções fixas na barra, uma quinta tela sem entrada própria ficaria
órfã: o usuário chegava nela e não sabia como voltar.

---

## 5. Módulo `:emulator` — preparado, desligado

Está tudo escrito: `CMakeLists.txt`, a ponte JNI, o carregador libretro por
`dlopen`, os stubs de vídeo e áudio, a `EmulatorActivity` e o
`NucleoLibretro` com as `external fun`.

**O `include(":emulator")` está comentado no `settings.gradle.kts`, de
propósito.** O módulo usa CMake; sem o NDK instalado, ligá-lo quebraria o
build inteiro — inclusive o app, que hoje compila. Para ativar, quando chegar
a Fase 4:

1. NDK "side by side" + CMake 3.22.1 no SDK Manager;
2. descomente a linha no `settings.gradle.kts`;
3. baixe o `libretro.h` oficial para `emulator/src/main/cpp/`;
4. `implementation(project(":emulator"))` no `app/build.gradle.kts`.

As decisões que são caras de mudar depois já estão gravadas no código, com o
porquê em comentário: processo separado `:emu` (os núcleos têm estado global
e `dlclose` não o limpa de forma confiável), `RTLD_LOCAL` no `dlopen` (os
núcleos têm símbolos de mesmo nome e colidiriam em silêncio), SurfaceView e
não TextureView (latência), bitmask atômico para input, sincronização pelo
áudio, e o alinhamento de **16 KB** no linker, que a Play Store exige para
target Android 15+.

---

## 6. Como aplicar e testar

Copie os 20 arquivos. O `settings.gradle.kts` é substituído (só ganha o bloco
comentado do `:emulator`). Sync + build.

```
git add -A
git commit -m "Phase 3: frosted glass design system, pill navigation, emulator skeleton"
```

Teste:

1. **Os 8 temas.** Percorra todos com um wallpaper claro e outro escuro. O
   texto das pílulas tem que continuar legível em todos. AMOLED e Famicom são
   os que valem olhar com atenção — são os dois casos especiais.
2. **Desfoque real.** No Odin, com wallpaper: role a lista e veja o conteúdo
   passando borrado sob a pílula de navegação.
3. **Caminho de scrim.** Ligue "Reduzir efeitos" nos Ajustes. O visual muda,
   mas nada fica feio nem ilegível. É o que o 665 vai mostrar sempre.
4. **60 fps.** Em **release**, com wallpaper animado, role uma biblioteca
   grande de ponta a ponta.
5. **Controle.** Conecte um gamepad e navegue a interface inteira sem tocar na
   tela. Todo elemento focado precisa ser visível.
6. **O app ainda compila sem NDK.** É o teste de que o módulo nativo está
   desligado como deveria.

---

## 7. O que ficou de fora, e por quê

- **Transição de elemento compartilhado** da capa para os detalhes do jogo.
  Faltou uma tela de detalhes para ser o destino — hoje o toque longo abre uma
  folha. Faz mais sentido junto com a tela de detalhes, na Fase 4, quando
  houver "Jogar".
- **Diálogos em vidro.** Diálogo e folha inferior vivem em outra janela, onde
  a camada de fundo não existe. Dariam uma segunda captura só para eles;
  deixei no Material do tema, que já acompanha as cores.
- **Wallpaper em vídeo (Media3) e o "usar papel de parede do sistema".**
  GIF e WebP animado já funcionam desde a Fase 0. Os outros dois são
  independentes do vidro e cabem num drop pequeno quando você quiser.

E a **Fase 1B continua parada** esperando o teste do KSP que eu pedi: adicionar
`id("com.google.devtools.ksp") version "2.2.10-2.0.2"` e rodar
`./gradlew :app:help`. Sucesso, erro de versão, ou erro dizendo que o Kotlin
Gradle Plugin não está aplicado — os três levam a caminhos diferentes para o
banco de dados, e nenhum deles vale tentar às cegas.
