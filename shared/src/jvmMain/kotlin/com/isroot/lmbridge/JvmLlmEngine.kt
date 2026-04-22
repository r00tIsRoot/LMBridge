package com.isroot.lmbridge

import com.isroot.lmbridge.inference.JvmModelInferenceManager
import kotlinx.coroutines.flow.Flow

class JvmLlmEngine(
    private val config: EngineConfig
) : LlmEngine {
    private val inferenceManager = JvmModelInferenceManager(
        modelPath = config.modelPath,
        backend = LMBridge.Backend.CPU, // JVM defaults to CPU usually
        maxNumTokens = 8192
    )

    override suspend fun initialize() {
        inferenceManager.initialize()
    }

    override suspend fun createConversation(): Conversation {
        return Conversation(id = "jvm_conv_${System.currentTimeMillis()}", messages = emptyList())
    }

    override fun sendMessage(conversationId: String, content: String): Flow<GenerationResult> {
        return inferenceManager.generate(content)
    }

    override suspend fun getConversations(): List<Conversation> {
        return emptyList()
    }

    override suspend fun shutdown() {
        inferenceManager.close()
    }
}
