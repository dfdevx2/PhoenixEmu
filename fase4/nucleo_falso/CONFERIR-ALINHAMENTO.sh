#!/usr/bin/env bash
# Confere se um .so esta alinhado para paginas de 16 KB.
#
# O sintoma de nao estar e cruel: no Android 15+ o dlopen falha com uma mensagem
# generica de "nao consegui carregar", e voce vai procurar o bug no seu codigo.
# Rode isto em TODO .so antes de por no aparelho -- inclusive nos nucleos que
# voce baixar prontos do buildbot da libretro.
[ -z "$1" ] && { echo "uso: $0 <arquivo.so>"; exit 1; }
LEITOR=$(command -v llvm-readelf || command -v readelf) || { echo "instale binutils ou llvm"; exit 1; }
echo "arquivo: $1"
MAU=0
while read -r alinhamento; do
  # LOAD segments precisam de alinhamento >= 0x4000 (16384)
  valor=$((alinhamento))
  if [ "$valor" -lt 16384 ]; then MAU=1; fi
  printf "  segmento LOAD alinhado em %s (%d bytes)\n" "$alinhamento" "$valor"
done < <("$LEITOR" -l "$1" 2>/dev/null | awk '/LOAD/ {print $NF}')
if [ "$MAU" = 1 ]; then
  echo "  REPROVADO: ha segmento abaixo de 16384 -- nao vai carregar no Android 15+"
  exit 1
fi
echo "  OK: alinhado para 16 KB"
