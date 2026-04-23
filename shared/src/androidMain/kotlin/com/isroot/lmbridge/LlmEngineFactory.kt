package com.isroot.lmbridge

import android.content.Context

actual class LlmEngineFactory(private val context: Context) {
    actual fun createEngine(
        modelPath: String? = null,
        backend: LMBridge.Backend = LMBridge.Backend.NPU,
        maxNumTokens: Int = 8192
    ): LlmEngine {
        return AndroidLlmEngine(
            context = context,
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxNumTokens
        )
    }
}
