package com.isroot.lmbridge

actual class LlmEngineFactory {
    actual fun createEngine(
        modelPath: String? = null,
        backend: LMBridge.Backend = LMBridge.Backend.NPU,
        maxNumTokens: Int = 8192
    ): LlmEngine {
        return IosLlmEngine(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxNumTokens
        )
    }
}
