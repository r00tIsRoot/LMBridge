package com.isroot.lmbridge

actual class LlmEngineFactory {
    actual fun createEngine(
        modelPath: String?,
        backend: LMBridge.Backend,
        maxNumTokens: Int
    ): LlmEngine {
        return JvmLlmEngine(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxNumTokens
        )
    }
}
