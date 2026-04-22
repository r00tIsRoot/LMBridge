package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DummyLlmEngine : LlmEngine {
    override suspend fun initialize() {}
    override suspend fun createConversation(): Conversation = Conversation("dummy", emptyList())
    override fun sendMessage(conversationId: String, content: String): Flow<GenerationResult> = flow {
        emit(GenerationResult.Error("Not implemented on this platform"))
    }
    override suspend fun getConversations(): List<Conversation> = emptyList()
    override suspend fun shutdown() {}
}
