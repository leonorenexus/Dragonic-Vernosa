#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>

#include "llama.h"

#define TAG "DragonicVernosa"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct EngineContext {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    std::atomic<bool> stop_flag{false};
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_leonoretech_dragonicvernosa_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring jModelPath, jint nThreads, jint nCtx) {

    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only — aman & stabil di semua HP

    llama_model *model = llama_model_load_from_file(modelPath, mparams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (!model) {
        LOGE("Gagal load model GGUF");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Gagal init context");
        llama_model_free(model);
        return 0;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    auto *engine = new EngineContext();
    engine->model = model;
    engine->ctx = ctx;
    engine->sampler = sampler;

    LOGI("Model loaded OK, ctx=%d threads=%d", nCtx, nThreads);
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_leonoretech_dragonicvernosa_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/, jlong ctxPtr, jstring jPrompt,
        jint maxTokens, jfloat temperature, jobject listener) {

    auto *engine = reinterpret_cast<EngineContext *>(ctxPtr);
    if (!engine) return;
    engine->stop_flag = false;

    jclass listenerClass = env->GetObjectClass(listener);
    jmethodID onToken = env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone = env->GetMethodID(listenerClass, "onDone", "(Ljava/lang/String;)V");
    jmethodID onError = env->GetMethodID(listenerClass, "onError", "(Ljava/lang/String;)V");

    const char *promptChars = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(jPrompt, promptChars);

    const llama_vocab *vocab = llama_model_get_vocab(engine->model);

    // Tokenize prompt
    int nPromptTokens = -llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(nPromptTokens);
    if (llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), tokens.data(), nPromptTokens, true, true) < 0) {
        env->CallVoidMethod(listener, onError, env->NewStringUTF("Tokenize gagal"));
        return;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());

    std::string fullText;
    for (int i = 0; i < maxTokens; i++) {
        if (engine->stop_flag.load()) break;

        if (llama_decode(engine->ctx, batch) != 0) {
            env->CallVoidMethod(listener, onError, env->NewStringUTF("Decode gagal"));
            return;
        }

        llama_token newToken = llama_sampler_sample(engine->sampler, engine->ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            fullText += piece;
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(listener, onToken, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        batch = llama_batch_get_one(&newToken, 1);
    }

    jstring jFull = env->NewStringUTF(fullText.c_str());
    env->CallVoidMethod(listener, onDone, jFull);
    env->DeleteLocalRef(jFull);
}

extern "C" JNIEXPORT void JNICALL
Java_com_leonoretech_dragonicvernosa_LlamaEngine_nativeStop(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    auto *engine = reinterpret_cast<EngineContext *>(ctxPtr);
    if (engine) engine->stop_flag = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_leonoretech_dragonicvernosa_LlamaEngine_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    auto *engine = reinterpret_cast<EngineContext *>(ctxPtr);
    if (!engine) return;
    if (engine->sampler) llama_sampler_free(engine->sampler);
    if (engine->ctx) llama_free(engine->ctx);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
}
