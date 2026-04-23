package com.isroot.lmbridge

/**
 * Factory for creating platform-specific LlmEngine implementations.
 */
expect class LlmEngineFactory {
    fun createEngine(
        modelPath: String? = null,
        backend: LMBridge.Backend = LMBridge.Backend.NPU,
        maxNumTokens: Int = 8192
    ): LlmEngine
}
