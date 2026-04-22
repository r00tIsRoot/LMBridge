package com.isroot.lmbridge

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.js.Date

class JsLlmEngine(
    private val config: EngineConfig
) : LlmEngine {
    private var engine: LiteRtEngine? = null
    private var model: LiteRtModel? = null
    private var currentConversation: LiteRtConversation? = null

    override suspend fun initialize() {
        engine = LiteRtJs.loadLiteRt().await()
        model = engine?.loadAndCompile(config.modelPath)?.await()
    }

    override suspend fun createConversation(): Conversation {
        val config = createConversationConfig("You are a helpful AI assistant.")
        val jsConv = model?.createConversation(config)?.await()
        currentConversation = jsConv
        
        return Conversation(
            id = "js_conv_${Date.now().toLong()}",
            messages = emptyList()
        )
    }

    override fun sendMessage(conversationId: String, content: String): Flow<GenerationResult> = flow {
        val jsConv = currentConversation ?: throw IllegalStateException("Conversation not initialized")
        
        try {
            val response = jsConv.sendMessage(content).await()
            emit(GenerationResult.Token(response.text))
            emit(GenerationResult.Done)
        } catch (e: Throwable) {
            emit(GenerationResult.Error(e.message ?: "Unknown JS Error"))
        }
    }

    override suspend fun getConversations(): List<Conversation> {
        return emptyList()
    }

    override suspend fun shutdown() {
        currentConversation = null
        model = null
        engine = null
    }
}
