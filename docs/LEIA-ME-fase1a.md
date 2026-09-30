# Project Phoenix — Fase 1A (Varredura, hashes e trabalhos em segundo plano)

15 arquivos, todos em cima da Fase 0. Nenhum toca no tema, no áudio ou nas
preferências: se a Fase 0 está compilando, este pacote não desfaz nada dela.

---

## 1. Uma correção no roteiro, antes de tudo

No plano original eu escrevi que **o scanner também calcularia o hash da
ROM**. Está errado, e é melhor corrigir agora do que descobrir no teste.

Hashear exige ler **cada byte de cada ROM** através do SAF, que é a camada de
I/O mais lenta disponível no Android. Uma biblioteca de 5.000 jogos de SNES a
2 MB de média são 10 GB lidos. Isso não é "um pouco mais devagar": é a
diferença entre a varredura levar 3 segundos e levar vinte minutos — e
destruiria justamente o critério de pronto que eu mesmo defini para esta
fase.

Então o hash saiu da varredura e virou uma ação separada:

| Operação | Quando roda | Custo |
|---|---|---|
| Varredura | ao abrir o app, automática | uma query por pasta |
| Capas | em segundo plano, automática | rede, retomável |
| **Hashes** | **só quando você mandar**, nas Configurações | leitura integral das ROMs |

O hash existe para o RetroAchievements, que é Fase 5. Ele pode chegar tarde,
pode ser cancelado no meio e pode nunca ser calculado sem que nada quebre.

---

## 2. O que mudou

### Varredura incremental (`RomScanner`)

Cada arquivo carrega uma **assinatura**: tamanho + data de modificação. Se a
assinatura bate com a que está na biblioteca, o arquivo é ignorado sem ser
aberto. Reabrir o app com 5.000 ROMs passou a custar uma consulta por pasta.

A parte que exigiu cuidado foi a **ausência**. Se o cartão SD não montou, a
query falha — e marcar tudo como ausente apagaria a biblioteca visualmente.
Por isso `listar()` agora devolve `ok: Boolean`, e um jogo só pode ser
declarado ausente se a árvore dele **respondeu**. Jogo ausente não é apagado:
fica esmaecido no cartão, com um selo, e volta ao normal sozinho quando a
pasta reaparece. Favorito e tempo de jogo sobrevivem.

Arquivo que **mudou** é reidentificado, mas o merge preserva o que é seu
(favorito, tempo de jogo, última vez jogado) e descarta o que ficou inválido
(o hash, porque o conteúdo é outro).

### `RomHasher`

CRC32 do arquivo inteiro (é o que as DATs No-Intro publicam) e MD5 no formato
RetroAchievements, **na mesma passada**, sem carregar a ROM na memória.

O RA não hasheia o arquivo cru: no NES ele ignora o cabeçalho iNES de 16
bytes; no SNES, o cabeçalho de copiador de 512 bytes, quando presente. Errar
isso não dá erro visível — dá "jogo não reconhecido" na Fase 5 e horas
procurando o motivo.

Detectar o cabeçalho do SNES exige o tamanho total, que nem sempre se conhece
antes de ler (entradas de zip costumam reportar -1). Em vez de exigir o
tamanho, o código mantém **dois MD5 em paralelo** — um do arquivo inteiro e
um ignorando os primeiros 512 bytes — e escolhe no final, quando o tamanho
real já é conhecido. MD5 é barato; reler a ROM não é.

Portei esse algoritmo para um script e conferi contra um MD5 de referência
nos quatro casos (NES com e sem cabeçalho, SNES com e sem copiador), com
buffers de 7, 16, 64, 512, 513, 4096 e 1 MB — de propósito incluindo tamanhos
que caem no meio do cabeçalho, que é onde a aritmética de offset costuma
quebrar. Os quatro passam em todos os buffers.

**Ainda assim, valide antes da Fase 5.** Pegue um jogo que você já tem no
RetroAchievements, calcule o hash aqui e compare com o que o site mostra. Eu
implementei a regra como a conheço; conferir contra a fonte é barato e evita
uma caçada longa lá na frente.

### Capas e hashes no WorkManager

Antes as capas eram baixadas dentro do escopo do `BibliotecaStore`. Funcionava,
mas fechar o app no meio de 3.000 capas significava recomeçar.

Agora são dois Workers. Eles sobrevivem ao app ser fechado, respeitam rede
(capas) e bateria (hashes), e retomam sozinhos — o critério de "o que falta"
é o próprio estado da biblioteca, então não há posição guardada para perder.

E a capa agora é **gravada em disco**, no diretório do app. A lista passou a
ler arquivo local: nem a rede nem o cache do Coil (que é limitado e
descartável) entram no caminho do scroll. Escrita atômica, para que uma queda
de conexão não deixe um PNG truncado no lugar da capa.

### O diálogo "Novos Jogos Encontrados" saiu

Você descreveu o scanner como **"Scanner Fantasma (Leitura Oculta)"**. O
diálogo contrariava isso: parava o usuário para pedir autorização de algo que
agora é barato. Virou um **snackbar** — "12 jogos novos" — que não interrompe
nada. Os jogos simplesmente aparecem e as capas vão preenchendo.

Também entrou um botão de sincronizar na barra de topo, para quando você
acabou de copiar ROMs e não quer esperar a próxima abertura.

---

## 3. Como aplicar

Copie os 15 arquivos por cima. Nada para apagar.

**Novos:** `data/RomHasher.kt`, `data/Trabalhos.kt`, `data/CapaWorker.kt`,
`data/HashWorker.kt`.

Depois de copiar, **sync + build** (o `libs.versions.toml` mudou):

```
./gradlew :app:assembleDebug
```

```
git add -A
git commit -m "Phase 1A: incremental scan, ROM hashing, WorkManager jobs"
```

---

## 4. Como testar

1. **Reabertura barata.** Abra o app com a biblioteca já montada. Não deve
   haver diálogo nem espera. No Logcat, filtre `RomScanner`: a segunda
   abertura não identifica nada.
2. **Arquivo novo.** Copie uma ROM para uma pasta já registrada e reabra.
   Snackbar "1 jogo novo", e ele aparece.
3. **Arquivo alterado.** Marque um jogo como favorito, depois substitua o
   arquivo por outro com o mesmo nome. Após sincronizar, o jogo é
   reidentificado **e continua favorito**.
4. **Ausência.** Desmonte o cartão (ou renomeie a pasta) e sincronize. Os
   jogos ficam esmaecidos com o selo, não somem. Remonte e sincronize: voltam
   ao normal.
5. **Pasta ilegível não destrói nada.** Revogue a permissão de uma pasta em
   Configurações do Android e sincronize: deve aparecer "Uma pasta não pôde
   ser lida", e **nenhum** jogo deve ser marcado ausente.
6. **Capas sobrevivem ao fechamento.** Adicione muitas ROMs, deixe as capas
   baixando e feche o app pelo gerenciador. Reabra: o download continua de
   onde parou, e as que já baixaram carregam em modo avião (é a prova de que
   estão em disco).
7. **Hashes.** Configurações → Manutenção → Calcular. Acompanhe o progresso,
   cancele no meio, mande calcular de novo: ele retoma sem refazer o que já
   fez.
8. **Validação do hash** (o teste que importa): compare o hash de um jogo
   conhecido com o que o RetroAchievements mostra.

---

## 5. Decisão pendente: Room ou SQLite na mão

A Fase 1B é DataStore + banco de dados, e ela trava numa incógnita que só um
teste seu resolve: **o Room precisa de KSP, e o KSP é um plugin do compilador
Kotlin.** O seu projeto usa o Kotlin embutido do AGP 9 — não há
`org.jetbrains.kotlin.android` no `plugins {}`. Eu não tenho como confirmar
se o KSP se aplica nesse arranjo.

Teste de dois minutos. No `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}
```

E rode:

```
./gradlew :app:help
```

Os dois desfechos ruins querem dizer coisas diferentes:

- **"version 2.2.10-2.0.2 not found"** ou similar → é só a versão. Procure a
  release do KSP que casa com o Kotlin 2.2.10 e troque o número. Não é
  bloqueio.
- **Erro falando que o Kotlin Gradle Plugin não está aplicado** → aí sim o
  KSP não convive com o Kotlin embutido do AGP 9, e o Room está fora.

Me diga qual dos três aconteceu (sucesso, versão errada, ou incompatível) e
eu monto a 1B de acordo:

| Resultado | Caminho |
|---|---|
| KSP aplica | **Room**: entidades, DAOs, índices em sistema/favorito/últimoJogo, migração do JSON |
| KSP não aplica | **SQLite direto** via `androidx.sqlite`, com SQL escrito à mão. Mais código meu, zero plugin, mesmas consultas |

Não vale tentar o Room às cegas: se o plugin não aplicar, todas as entidades
e DAOs viram trabalho jogado fora.

---

## 6. O que fica para a 1B

- DataStore com `SharedPreferencesMigration`, preservando as chaves atuais.
- O banco (Room ou SQLite, conforme o teste acima), com a migração do
  `biblioteca.json` — as assinaturas públicas do `BibliotecaStore` foram
  desenhadas para não mudar quando o miolo trocar.
- Filtro e ordenação descendo para o SQL, com índices, em vez de acontecerem
  em memória.
