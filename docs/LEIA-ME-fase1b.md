# Project Phoenix — Fase 1B (Room + DataStore)

11 arquivos, em cima da Fase 3. Três são novos (`JogoEntity.kt`, `JogoDao.kt`,
`PhoenixDatabase.kt`).

---

## 1. Sobre a flag que destravou o KSP

O erro que você trouxe dizia tudo: o KSP registra os diretórios de código
gerado via `kotlin.sourceSets`, e o Kotlin embutido do AGP 9 proibiu esse DSL.
A flag `android.disallowKotlinSourceSets=false` é a saída documentada pelo
próprio AGP, e ela já está no `gradle.properties` deste pacote com o porquê
em comentário.

O que isso significa na prática, para você decidir com clareza: a flag existe
porque o KSP ainda não se adaptou ao Kotlin embutido do AGP 9. Vale tentar
subir a versão do KSP de tempos em tempos e conferir se o build passa **sem**
a flag — quando passar, remova. Se um AGP futuro tirar a flag antes de o KSP
acertar o passo, o Room para de compilar até isso se resolver. Acho o risco
pequeno perto do que o Room entrega, mas o plano B continua existindo e é
local: só três arquivos sabem que Room existe.

---

## 2. O que o Room mudou de verdade

Não é "trocar onde os dados ficam". São três coisas que ficaram baratas:

**Marcar um favorito.** Antes: copiar a lista inteira em memória, reserializar
o JSON, gravar o arquivo. Agora: `UPDATE jogos SET favorito = NOT favorito
WHERE uri = ?`. Uma linha. Com 5.000 jogos isso deixa de ser detalhe.

**Filtro, busca e ordenação.** Aconteciam em Kotlin, sobre a lista toda, a
cada mudança. Desceram para o SQL. A tela agora só consome
`biblioteca.jogosVisiveis`.

**A varredura incremental.** Carregava a biblioteca inteira só para comparar
tamanho e data. Agora lê três colunas (`SELECT uri, tamanho_bytes,
modificado_em`) numa projeção própria.

### Duas decisões dentro do SQL que valem explicação

**A coluna `nome_ordenacao`.** É o nome em minúsculas, guardado. Existe
porque `ORDER BY LOWER(nome)` obriga o SQLite a calcular `LOWER()` em cada
linha e a montar uma B-tree temporária — nenhum índice serve para uma
expressão. Com a coluna pronta, o índice resolve a ordenação sozinho.

**Três consultas de listagem, uma por ordenação, em vez de uma com
`CASE WHEN`.** O truque do `ORDER BY CASE WHEN :ordem = 0 THEN ...` é bem
mais compacto, e foi meu primeiro impulso. Mas ele impede o SQLite de usar
índice: vira ordenação temporária toda vez. Com consultas separadas, cada
`ORDER BY` cai direto no índice. O custo é repetir o `WHERE` três vezes, e
achei um preço justo.

Uma coisa que **não** ficou rápida, e é bom você saber: a busca por nome usa
`LIKE '%termo%'`, que é varredura completa por natureza — índice nenhum serve
para curinga à esquerda. Em alguns milhares de linhas isso ainda é
imperceptível. Se um dia incomodar, a saída é uma tabela FTS, não um índice.

### Sem `fallbackToDestructiveMigration`

É o atalho padrão que todo tutorial usa, e ele apaga o banco quando o schema
muda. Uma biblioteca com favoritos e tempo de jogo acumulado não pode ser
descartada porque eu acrescentei uma coluna. A partir da versão 2, cada
mudança vai ganhar sua `Migration` escrita à mão — e é para isso que o schema
é exportado para `app/schemas/`, que **deve ir para o Git**: é ele que torna
uma migração revisável em diff.

---

## 3. DataStore

`SharedPreferences.apply()` parece assíncrono, mas ainda enfileira um fsync
que o sistema cobra no `onPause`/`onStop` — e a primeira leitura de `getX()`
bloqueia até o XML inteiro carregar. Nenhum dos dois aparece em teste; os dois
aparecem em ANR de usuário.

A migração usa uma **lista explícita de chaves**. Isso não é zelo: sem a
lista, o `biblioteca_cache` (um JSON que pode ter megabytes) seria arrastado
para dentro do DataStore, que carrega tudo na memória. Esse cache tem outro
destino — virar linhas no Room.

A API pública de `Preferencias` não mudou, então nem a UI nem o `AudioEngine`
precisaram de ajuste. A única exceção é `pastas()`, que virou suspensa.

**Um detalhe que a splash resolve:** ler do DataStore é assíncrono, então por
alguns quadros o app apareceria no tema padrão antes de trocar para o salvo.
A `MainActivity` agora segura a splash até `prefs.carregado` **e**
`biblioteca.carregado` ficarem prontos.

---

## 4. A migração da sua biblioteca

Roda uma vez, na primeira abertura, e só se a tabela estiver vazia. Duas
origens possíveis, nessa ordem: o `biblioteca.json` da Fase 1A e, para quem
pulou dela, o `biblioteca_cache` das SharedPreferences da Fase 0.

O `biblioteca.json` é **renomeado** para `biblioteca.json.migrado`, não
apagado. Se algo sair torto, o dado original ainda está lá para ser
inspecionado. Depois que você confirmar que está tudo certo, pode apagar à
mão.

---

## 5. Mudanças de API (se você tiver escrito código próprio em cima)

| Antes | Agora |
|---|---|
| `biblioteca.jogos` | `biblioteca.jogosVisiveis` (já filtrada pelo SQL) |
| filtro/ordenação em Kotlin na tela | `definirSistema()` / `definirTermo()` |
| `prefs.pastas()` | virou `suspend` |
| `store.jogosSemCapa()` / `jogosSemHash()` | viraram `suspend` |
| `store.gravar()` | **deixou de existir** — o Room grava na hora |

---

## 6. Aplicar e testar

```
git add -A
git commit -m "Phase 1B: Room database and DataStore preferences"
```

Sync + build. O primeiro build demora mais: o KSP vai gerar o código do Room.

1. **A biblioteca sobreviveu.** Abra o app. Os jogos, favoritos e tempo de
   jogo têm que estar todos lá. No Logcat, filtre `BibliotecaStore`: aparece
   "Migrados N jogos para o Room" uma única vez.
2. **As preferências sobreviveram.** Tema, volumes e wallpaper continuam como
   você deixou.
3. **Sem flash de tema.** Com AMOLED ativo, abra o app várias vezes. Ele
   nunca deve piscar claro antes de escurecer.
4. **Favorito é instantâneo.** Marque vários em sequência numa biblioteca
   grande. Nenhum engasgo.
5. **Busca.** Digite e apague. Digitar espera 180 ms; apagar até esvaziar
   volta a lista na hora (o debounce é condicional de propósito).
6. **`app/schemas/` apareceu** com um JSON do schema versão 1. Commite.
7. **Confira o rodapé:** `biblioteca.json.migrado` deve existir em
   `/data/data/com.dfdx047.phoenixemu/files/`.

---

## 7. Ressalvas

- **Versões.** Usei Room 2.7.1 e DataStore 1.1.7, as mais recentes que
  consigo verificar. Se o Android Studio sugerir número maior, aceite —
  principalmente no Room, onde uma versão mais nova pode inclusive dispensar
  a flag do KSP.
- **O Gson ficou.** Ele agora serve só à migração e ao conversor do Retrofit.
  Quando a migração já tiver rodado em todos os lugares que importam, dá para
  trocar o Retrofit por chamadas diretas de OkHttp e remover o Gson de vez.

---

## 8. O que sobrou do roteiro

Da Fase 2 original, áudio, temas e navegação já foram resolvidos nas fases 0 e
3. Restam duas coisas, e vou ser franco sobre o valor de cada uma:

- **ViewModel na tela da biblioteca** — vale. Hoje `secaoIndice`, `busca` e
  `sistemaIndice` vivem na composição; num ViewModel eles sobreviveriam à
  morte do processo e a tela ficaria testável.
- **Hilt** — honestamente, opcional para um dev solo. Os singletons que
  temos funcionam e são substituíveis em teste. Hilt paga a mais quando há
  várias implementações trocáveis, e não é o caso ainda. Eu deixaria para
  quando doer.

Depois disso, Fase 4: o NDK. Aí sim o módulo `:emulator` sai do gelo.
