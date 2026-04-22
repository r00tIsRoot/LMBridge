package com.isroot.lmbridge

expect class LlmEngineFactory {
    companion object {
        fun create(config: EngineConfig): LlmEngine
    }
}
