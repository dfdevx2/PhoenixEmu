/**
 * Ponte com o rcheevos (RetroAchievements).
 *
 * Tudo aqui roda na thread do emulador (do_frame, memoria) ou na thread de rede
 * do Kotlin (respostas HTTP). O rc_client tem mutex interno para isso.
 * Textos viajam como byte[] UTF-8: o NewStringUTF do JNI aborta com emoji.
 */
#include "ra_bridge.h"

#include <android/log.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <unordered_map>

#include "libretro_core.h"
#include "rc_client.h"
#include "rc_consoles.h"
#include "rc_libretro.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PhoenixRA", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PhoenixRA", __VA_ARGS__)

namespace {

const char *CLASSE = "com/dfdx047/phoenixemu/emulator/RaNativo";

JavaVM *g_vm = nullptr;
jclass g_cls = nullptr;
jmethodID m_http = nullptr;
jmethodID m_conquista = nullptr;
jmethodID m_jogo = nullptr;
jmethodID m_erro = nullptr;

rc_client_t *g_client = nullptr;
rc_libretro_memory_regions_t g_regioes;
bool g_regioes_ok = false;
phoenix::LibretroCore *g_nucleo = nullptr;
std::string g_hash;
std::atomic<bool> g_ativo{false};

struct Pendente {
    rc_client_server_callback_t cb;
    void *dados;
};
std::mutex g_mtx;
std::unordered_map<jlong, Pendente> g_pendentes;
std::atomic<jlong> g_prox_id{1};

struct Anexo {
    JNIEnv *env = nullptr;
    bool anexou = false;
    ~Anexo() {
        if (anexou && g_vm != nullptr) g_vm->DetachCurrentThread();
    }
};
thread_local Anexo t_anexo;

JNIEnv *env_atual() {
    if (t_anexo.env != nullptr) return t_anexo.env;
    if (g_vm == nullptr) return nullptr;
    JNIEnv *env = nullptr;
    jint r = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (r == JNI_OK) {
        t_anexo.env = env;
        return env;
    }
    if (r == JNI_EDETACHED && g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
        t_anexo.env = env;
        t_anexo.anexou = true;
        return env;
    }
    return nullptr;
}

jbyteArray bytes(JNIEnv *env, const char *s) {
    if (s == nullptr) s = "";
    jsize n = static_cast<jsize>(strlen(s));
    jbyteArray a = env->NewByteArray(n);
    if (a != nullptr) env->SetByteArrayRegion(a, 0, n, reinterpret_cast<const jbyte *>(s));
    return a;
}

void limpar_excecao(JNIEnv *env) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

void notificar_conquista(const char *titulo, const char *desc, int pontos, const char *url) {
    JNIEnv *env = env_atual();
    if (env == nullptr || g_cls == nullptr || m_conquista == nullptr) return;
    jbyteArray t = bytes(env, titulo), d = bytes(env, desc), u = bytes(env, url);
    env->CallStaticVoidMethod(g_cls, m_conquista, t, d, static_cast<jint>(pontos), u);
    limpar_excecao(env);
    env->DeleteLocalRef(t);
    env->DeleteLocalRef(d);
    env->DeleteLocalRef(u);
}

void notificar_jogo(const char *titulo, int total, int ganhas, int ptsTotal, int ptsGanhos) {
    JNIEnv *env = env_atual();
    if (env == nullptr || g_cls == nullptr || m_jogo == nullptr) return;
    jbyteArray t = bytes(env, titulo);
    env->CallStaticVoidMethod(g_cls, m_jogo, t, total, ganhas, ptsTotal, ptsGanhos);
    limpar_excecao(env);
    env->DeleteLocalRef(t);
}

void notificar_erro(int codigo, const char *msg) {
    JNIEnv *env = env_atual();
    if (env == nullptr || g_cls == nullptr || m_erro == nullptr) return;
    jbyteArray t = bytes(env, msg);
    env->CallStaticVoidMethod(g_cls, m_erro, static_cast<jint>(codigo), t);
    limpar_excecao(env);
    env->DeleteLocalRef(t);
}

// ---- memoria do core ----
int g_console = 0;
std::string g_usuario, g_token;
uint32_t g_quadros = 0;
std::atomic<bool> g_adiar{false};
retro_memory_descriptor g_desc[64];
retro_memory_map g_mapa;
bool g_tem_mapa = false;
uint32_t g_tent = 0;
void RC_CCONV info_memoria(uint32_t id, rc_libretro_core_memory_info_t *info) {
    if (g_nucleo == nullptr) {
        info->data = nullptr;
        info->size = 0;
        return;
    }
    info->data = static_cast<uint8_t *>(g_nucleo->memoriaDados(id));
    info->size = g_nucleo->memoriaTamanho(id);
}

uint32_t RC_CCONV ler_memoria(uint32_t endereco, uint8_t *buffer, uint32_t n, rc_client_t *) {
    if (!g_regioes_ok) {
        // alguns nucleos so expoem a RAM depois do primeiro quadro: tenta de novo de vez em quando
        if (g_nucleo != nullptr && (g_tent++ % 300) == 0 && g_tent < 20000) {
            memset(&g_regioes, 0, sizeof(g_regioes));
            g_regioes_ok = rc_libretro_memory_init(&g_regioes, g_tem_mapa ? &g_mapa : nullptr, info_memoria, static_cast<uint32_t>(g_console)) != 0;
            LOGI("memoria (tentativa %u): ok=%d sram=%zu wram=%zu", g_tent, g_regioes_ok ? 1 : 0,
                 static_cast<size_t>(g_nucleo->memoriaTamanho(0)), static_cast<size_t>(g_nucleo->memoriaTamanho(2)));
            for (unsigned id = 0; id < 12; id++) LOGI("  sonda id=%u tam=%zu ptr=%p", id, static_cast<size_t>(g_nucleo->memoriaTamanho(id)), g_nucleo->memoriaDados(id));
            for (unsigned id = 256; id < 262; id++) LOGI("  sonda id=%u tam=%zu ptr=%p", id, static_cast<size_t>(g_nucleo->memoriaTamanho(id)), g_nucleo->memoriaDados(id));
        }
        if (!g_regioes_ok) return 0;
    }
    return rc_libretro_memory_read(&g_regioes, endereco, buffer, n);
}

// ---- rede (feita pelo Kotlin) ----
void RC_CCONV servidor(const rc_api_request_t *req, rc_client_server_callback_t cb, void *dados, rc_client_t *) {
    auto falhar = [&]() {
        rc_api_server_response_t r;
        memset(&r, 0, sizeof(r));
        r.body = "";
        r.body_length = 0;
        r.http_status_code = RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR;
        cb(&r, dados);
    };
    JNIEnv *env = env_atual();
    if (env == nullptr || g_cls == nullptr || m_http == nullptr) {
        falhar();
        return;
    }
    jlong id = g_prox_id.fetch_add(1);
    {
        std::lock_guard<std::mutex> l(g_mtx);
        g_pendentes[id] = {cb, dados};
    }
    jbyteArray u = bytes(env, req->url);
    jbyteArray p = req->post_data != nullptr ? bytes(env, req->post_data) : nullptr;
    jbyteArray c = req->content_type != nullptr ? bytes(env, req->content_type) : nullptr;
    env->CallStaticVoidMethod(g_cls, m_http, id, u, p, c);
    bool erro = env->ExceptionCheck();
    limpar_excecao(env);
    env->DeleteLocalRef(u);
    if (p != nullptr) env->DeleteLocalRef(p);
    if (c != nullptr) env->DeleteLocalRef(c);
    if (erro) {
        {
            std::lock_guard<std::mutex> l(g_mtx);
            g_pendentes.erase(id);
        }
        falhar();
    }
}

// ---- eventos ----
void RC_CCONV evento(const rc_client_event_t *e, rc_client_t *) {
    if (e->type == RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED && e->achievement != nullptr) {
        char url[256] = {0};
        rc_client_achievement_get_image_url(e->achievement, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED, url, sizeof(url));
        LOGI("conquista desbloqueada: %s (%u pts)", e->achievement->title, e->achievement->points);
        notificar_conquista(e->achievement->title, e->achievement->description,
                            static_cast<int>(e->achievement->points), url);
    }
}

void RC_CCONV ao_carregar_jogo(int result, const char *msg, rc_client_t *client, void *) {
    if (result != RC_OK) {
        LOGE("load_game falhou (%d): %s", result, msg != nullptr ? msg : "");
        notificar_erro(result == RC_NO_GAME_LOADED ? 3 : 2, msg);
        return;
    }
    const rc_client_game_t *jogo = rc_client_get_game_info(client);
    rc_client_user_game_summary_t s;
    memset(&s, 0, sizeof(s));
    rc_client_get_user_game_summary(client, &s);
    LOGI("jogo carregado: %s (%u/%u)", jogo != nullptr ? jogo->title : "?", s.num_unlocked_achievements,
         s.num_promoted_achievements);
    notificar_jogo(jogo != nullptr ? jogo->title : "", static_cast<int>(s.num_promoted_achievements),
                   static_cast<int>(s.num_unlocked_achievements), static_cast<int>(s.points_available),
                   static_cast<int>(s.points_unlocked));
}

void RC_CCONV ao_logar(int result, const char *msg, rc_client_t *client, void *) {
    if (result != RC_OK) {
        LOGE("login falhou (%d): %s", result, msg != nullptr ? msg : "");
        notificar_erro(1, msg);
        return;
    }
    LOGI("login ok, carregando jogo %s", g_hash.c_str());
    rc_client_begin_load_game(client, g_hash.c_str(), ao_carregar_jogo, nullptr);
}

void parar_interno() {
    g_adiar.store(false, std::memory_order_release);
    g_ativo.store(false, std::memory_order_release);
    if (g_client != nullptr) {
        rc_client_unload_game(g_client);
        rc_client_destroy(g_client);
        g_client = nullptr;
    }
    if (g_regioes_ok) {
        rc_libretro_memory_destroy(&g_regioes);
        g_regioes_ok = false;
    }
    std::lock_guard<std::mutex> l(g_mtx);
    g_pendentes.clear();
}

} // namespace

void phoenix_ra_definir_mapa(const struct retro_memory_map *mapa) {
    g_tem_mapa = false;
    if (mapa == nullptr || mapa->descriptors == nullptr || mapa->num_descriptors == 0) return;
    unsigned n = mapa->num_descriptors > 64 ? 64 : mapa->num_descriptors;
    for (unsigned i = 0; i < n; i++) g_desc[i] = mapa->descriptors[i];
    g_mapa.descriptors = g_desc;
    g_mapa.num_descriptors = n;
    g_tem_mapa = true;
    LOGI("mapa de memoria do nucleo: %u descritores", n);
}

void phoenix_ra_depois_do_quadro() {
    if (!g_ativo.load(std::memory_order_relaxed) || g_client == nullptr) return;
    if (g_adiar.load(std::memory_order_acquire)) {
        if (++g_quadros < 90) return;
        g_adiar.store(false, std::memory_order_release);
        if (g_regioes_ok) rc_libretro_memory_destroy(&g_regioes);
        memset(&g_regioes, 0, sizeof(g_regioes));
        g_regioes_ok = rc_libretro_memory_init(&g_regioes, g_tem_mapa ? &g_mapa : nullptr, info_memoria, static_cast<uint32_t>(g_console)) != 0;
        LOGI("memoria (apos %u quadros): console=%d ok=%d wram=%zu sram=%zu", g_quadros, g_console, g_regioes_ok ? 1 : 0,
             static_cast<size_t>(g_nucleo != nullptr ? g_nucleo->memoriaTamanho(2) : 0),
             static_cast<size_t>(g_nucleo != nullptr ? g_nucleo->memoriaTamanho(0) : 0));
        rc_client_begin_login_with_token(g_client, g_usuario.c_str(), g_token.c_str(), ao_logar, nullptr);
        return;
    }
    rc_client_do_frame(g_client);
}

void phoenix_ra_idle() {
    if (g_ativo.load(std::memory_order_relaxed) && g_client != nullptr) rc_client_idle(g_client);
}

void phoenix_ra_resetou() {
    if (g_ativo.load(std::memory_order_relaxed) && g_client != nullptr) rc_client_reset(g_client);
}

bool phoenix_ra_iniciar(JNIEnv *env, phoenix::LibretroCore *nucleo, const std::string &usuario,
                        const std::string &token, const std::string &hash, int consoleId, bool hardcore) {
    parar_interno();
    if (g_vm == nullptr) env->GetJavaVM(&g_vm);

    if (g_cls == nullptr) {
        jclass local = env->FindClass(CLASSE);
        if (local == nullptr) {
            env->ExceptionClear();
            LOGE("classe RaNativo nao encontrada");
            return false;
        }
        g_cls = static_cast<jclass>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
        m_http = env->GetStaticMethodID(g_cls, "pedidoHttp", "(J[B[B[B)V");
        m_conquista = env->GetStaticMethodID(g_cls, "aoConquista", "([B[BI[B)V");
        m_jogo = env->GetStaticMethodID(g_cls, "aoJogo", "([BIIII)V");
        m_erro = env->GetStaticMethodID(g_cls, "aoErro", "(I[B)V");
        if (m_http == nullptr || m_conquista == nullptr || m_jogo == nullptr || m_erro == nullptr) {
            env->ExceptionClear();
            LOGE("metodos de RaNativo nao encontrados");
            g_cls = nullptr;
            return false;
        }
    }

    g_nucleo = nucleo;
    g_hash = hash;
    g_client = rc_client_create(ler_memoria, servidor);
    if (g_client == nullptr) return false;
    rc_client_set_event_handler(g_client, evento);
    rc_client_set_hardcore_enabled(g_client, hardcore ? 1 : 0);

    g_console = consoleId;
    g_tent = 0;
    memset(&g_regioes, 0, sizeof(g_regioes));
    g_regioes_ok = rc_libretro_memory_init(&g_regioes, g_tem_mapa ? &g_mapa : nullptr, info_memoria, static_cast<uint32_t>(consoleId)) != 0;
    LOGI("memoria: console=%d ok=%d", consoleId, g_regioes_ok ? 1 : 0);

    g_ativo.store(true, std::memory_order_release);
    // login e mapeamento da memoria so depois de alguns quadros (alguns nucleos so tem a RAM pronta depois)
    g_usuario = usuario;
    g_token = token;
    g_quadros = 0;
    g_adiar.store(true, std::memory_order_release);
    return true;
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_RaNativo_nativeRaParar(JNIEnv *, jobject) {
    parar_interno();
}

JNIEXPORT void JNICALL
Java_com_dfdx047_phoenixemu_emulator_RaNativo_nativeRaResposta(JNIEnv *env, jobject, jlong id, jint status,
                                                               jbyteArray corpo) {
    Pendente p;
    {
        std::lock_guard<std::mutex> l(g_mtx);
        auto it = g_pendentes.find(id);
        if (it == g_pendentes.end()) return;
        p = it->second;
        g_pendentes.erase(it);
    }
    std::string body;
    if (corpo != nullptr) {
        jsize n = env->GetArrayLength(corpo);
        body.resize(static_cast<size_t>(n));
        if (n > 0) env->GetByteArrayRegion(corpo, 0, n, reinterpret_cast<jbyte *>(&body[0]));
    }
    rc_api_server_response_t r;
    memset(&r, 0, sizeof(r));
    r.body = body.c_str();
    r.body_length = body.size();
    r.http_status_code = status > 0 ? status : RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR;
    p.cb(&r, p.dados);
}

} // extern "C"
