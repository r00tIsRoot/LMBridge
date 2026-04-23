package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DummyLlmEngine : LlmEngine {
    constructor() : super()
    
    override suspend fun performInitialize() {
    }
    
    override fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        emit(GenerationResult.Error("Not implemented on this platform"))
    }
    
    override suspend fun performShutdown() {
    }
}
