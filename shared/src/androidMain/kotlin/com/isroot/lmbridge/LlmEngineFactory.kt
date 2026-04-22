package com.isroot.lmbridge

actual class LlmEngineFactory {
    actual companion object {
        actual fun create(config: EngineConfig): LlmEngine {
            return AndroidLlmEngine(
                context = AndroidSdkConfig.requireContext(),
                config = config
            )
        }
    }
}
