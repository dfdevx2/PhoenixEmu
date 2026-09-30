# Andaime da Fase 4

O que tem aqui e **ferramenta de teste**, nao codigo do app. Nada disto entra
no APK.

```
nucleo_falso/
  nucleo_falso.c            o nucleo falso
  libretro.h                cabecalho oficial (8730 linhas, baixado da libretro)
  CMakeLists.txt            build para Android, ja com alinhamento de 16 KB
  teste_do_nucleo.c         confere o proprio nucleo falso
  CONFERIR-ALINHAMENTO.sh   roda em qualquer .so, inclusive nos nucleos reais
verificador_ambiente.c      nomes dos 90 comandos do environment (gerado)
verificador_audio.h         contador de underrun/overrun e deriva
PROMPTS-FASE-4.md           os oito prompts
```

## Por que um nucleo falso

Ate agora o compilador foi o juiz: o modelo local erra, o build reclama, voce
cola o erro, ele corrige. Na camada nativa esse juiz some — os erros tipicos
nao quebram build nenhum. Aparecem jogando, dias depois, sem pista.

O nucleo falso devolve o juiz. Ele emite coisas que voce conhece de cor, e ai
cada bug classico vira um sintoma que se identifica em cinco segundos:

| sintoma | causa |
|---|---|
| cores trocadas | formato de pixel |
| imagem torta na diagonal | pitch ignorado |
| borda amarela incompleta | geometria / recorte |
| contador pula numeros | quadros perdidos |
| contador repete | quadros duplicados |
| tom desafinado | taxa de amostragem |
| estalos | buffer de audio |
| flash e clique separados | video e audio fora de sincronia |

Ele tambem nao precisa de ROM, nem de nucleo real, nem resolve licenca nenhuma.

## O nucleo falso foi verificado

Ele e o juiz de tudo, entao mentir seria pior que nao existir. O
`teste_do_nucleo.c` carrega por `dlopen` — como o app vai carregar — e mede
cada promessa: os 25 simbolos, a geometria, 800 amostras por quadro, o pitch,
os cantos, a borda, a barra andando 1px, o tom em 440 Hz medido por cruzamentos
de zero, a continuidade de fase, o save state e a entrada.

```bash
cd nucleo_falso
gcc -shared -fPIC -O2 -o nucleo_falso.so nucleo_falso.c -lm
gcc -O2 -o teste_do_nucleo teste_do_nucleo.c -ldl -lm
./teste_do_nucleo                    # XRGB8888 (o que o Mesen usa)

gcc -shared -fPIC -O2 -DFORMATO_RGB565 -o nucleo_falso_565.so nucleo_falso.c -lm
./teste_do_nucleo ./nucleo_falso_565.so
```

Os dois dao 18/18 aqui.

## O alinhamento de 16 KB

O Odin 3 e Snapdragon 8 Elite, entao Android 15+ com paginas de 16 KB. Um `.so`
alinhado a 4 KB nao carrega — e o `dlopen` falha com uma mensagem generica que
nao diz "alinhamento". Voce procuraria o bug no seu codigo por horas.

O `CONFERIR-ALINHAMENTO.sh` roda em qualquer `.so`. Use nos nucleos que baixar
prontos tambem: nem todos vem alinhados.

```bash
./CONFERIR-ALINHAMENTO.sh algum.so
```

## Onde os jogos abrem

No **prompt 7**. Os seis primeiros constroem e validam o frontend contra o
nucleo falso. Nao e desvio: e o que transforma o prompt 7 numa troca de `.so`
em vez de uma cacada, porque nessa altura video, audio, pacing e entrada ja
foram provados separadamente.

A regra que economiza mais tempo: **nao avance sem o verificador do passo
passar.** Erro de video carregado para o passo do audio da duas causas
possiveis para um sintoma so.
