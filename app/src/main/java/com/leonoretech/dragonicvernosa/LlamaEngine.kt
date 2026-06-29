package com.leonoretech.dragonicvernosa

/**
 * Jembatan ke native code (llama.cpp via JNI).
 * Semua fungsi `external` di-implement di cpp/llama_jni.cpp
 */
object LlamaEngine {

    init {
        System.loadLibrary("dragonicvernosa")
    }

    interface TokenListener {
        fun onToken(token: String)
        fun onDone(fullText: String)
        fun onError(message: String)
    }

    /** Load model GGUF dari path lokal. Return context pointer (0 = gagal). */
    external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Long

    /** Generate jawaban, streaming token via listener. Blocking — panggil dari background thread. */
    external fun nativeGenerate(
        ctxPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        listener: TokenListener
    )

    /** Stop generation yang sedang jalan (dipanggil dari thread lain). */
    external fun nativeStop(ctxPtr: Long)

    /** Bebasin resource model dari memori. */
    external fun nativeFree(ctxPtr: Long)
}
