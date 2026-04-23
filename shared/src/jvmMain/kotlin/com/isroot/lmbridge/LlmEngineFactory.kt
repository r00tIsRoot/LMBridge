package com.isroot.lmbridge

actual class LlmEngineFactory {
    actual fun createEngine(
        modelPath: String? = null,
        backend: LMBridge.Backend = LMBridge.Backend.CPU,
        maxNumTokens: Int = 8192
    ): LlmEngine {
        return JvmLlmEngine(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxNumTokens
        )
    }
}
