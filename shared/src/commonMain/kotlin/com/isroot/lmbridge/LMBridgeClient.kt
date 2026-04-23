package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow

class LMBridgeClient(
    private val engine: LlmEngine,
    private val config: LlmOrchestrator.OrchestratorConfig = LlmOrchestrator.OrchestratorConfig()
) {
    private val orchestrator = LlmOrchestrator(engine, config)

    suspend fun initialize() {
        engine.initialize()
    }

    fun generate(
        prompt: String,
        systemInstruction: String = config.defaultSystemInstruction
    ): Flow<GenerationResult> {
        return orchestrator.generate(prompt, systemInstruction)
    }

    suspend fun release() {
        engine.shutdown()
    }
}