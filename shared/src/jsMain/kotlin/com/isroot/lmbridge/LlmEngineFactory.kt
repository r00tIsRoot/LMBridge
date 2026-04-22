package com.isroot.lmbridge

actual class LlmEngineFactory {
    actual companion object {
        actual fun create(config: EngineConfig): LlmEngine {
            return JsLlmEngine(config)
        }
    }
}
