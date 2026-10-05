#pragma once

#include <jni.h>

#include <string>

namespace phoenix { class LibretroCore; }

/** Chamado pela thread do emulador depois de cada retro_run. No-op se o RA nao foi iniciado. */
void phoenix_ra_depois_do_quadro();
/** Chamado enquanto o jogo esta pausado. */
void phoenix_ra_idle();
/** Chamado quando o jogo e reiniciado. */
void phoenix_ra_resetou();

bool phoenix_ra_iniciar(JNIEnv *env, phoenix::LibretroCore *nucleo, const std::string &usuario,
                        const std::string &token, const std::string &hash, int consoleId, bool hardcore);
