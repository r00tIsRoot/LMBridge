package com.isroot.lmbridge

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class JsLlmEngine(
    modelPath: String? = null,
    backend: LMBridge.Backend = LMBridge.Backend.CPU,
    maxNumTokens: Int = 8192
) : LlmEngine(modelPath, backend, maxNumTokens) {
    private var engine: LiteRtEngine? = null
    private var model: LiteRtModel? = null
    private var currentConversation: LiteRtConversation? = null

    override suspend fun performInitialize() {
        engine = LiteRtJs.loadLiteRt().await()
        model = engine?.loadAndCompile(modelPath ?: "gemma-4-E2B-it.litertlm")?.await()
    }

    override fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        val jsModel = model ?: throw IllegalStateException("Model not initialized")
        
        val config = createConversationConfig(systemInstruction)
        val jsConv = jsModel.createConversation(config).await()
        currentConversation = jsConv
        
        try {
            val response = jsConv.sendMessage(content).await()
            emit(GenerationResult.Token(response.text))
        } catch (e: Throwable) {
            emit(GenerationResult.Error(e.message ?: "Unknown JS Error"))
        }
    }

    override suspend fun performShutdown() {
        currentConversation = null
        model = null
        engine = null
    }
}
