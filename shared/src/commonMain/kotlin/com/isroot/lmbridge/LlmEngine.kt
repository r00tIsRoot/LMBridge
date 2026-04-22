package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    suspend fun initialize()
    
    suspend fun createConversation(): Conversation
    
    fun sendMessage(conversationId: String, content: String): Flow<GenerationResult>
    
    suspend fun getConversations(): List<Conversation>
    
    suspend fun shutdown()
}

sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data object Done : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
