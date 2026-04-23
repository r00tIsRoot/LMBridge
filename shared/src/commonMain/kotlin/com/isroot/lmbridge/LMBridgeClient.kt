package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow

class LMBridgeClient(
    engine: LlmEngine,
    private val maxNumTokens: Int = 8192
) {
    private val orchestrator = LlmOrchestrator(engine, maxNumTokens)


    suspend fun initialize() {
        engine.initialize()
    }

    fun generate(
        prompt: String,
        systemInstruction: String = "You are a helpful AI assistant."
    ): Flow<GenerationResult> {
        return orchestrator.generate(prompt, systemInstruction)
    }

    suspend fun release() {
        engine.shutdown()
    }
}
