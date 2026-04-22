package com.isroot.lmbridge

import android.content.Context
import com.isroot.lmbridge.inference.ModelInferenceManager
import kotlinx.coroutines.flow.Flow

class AndroidLlmEngine(
    private val context: Context,
    private val config: EngineConfig
) : LlmEngine {
    private val inferenceManager = ModelInferenceManager(
        context = context,
        modelPath = config.modelPath,
        backend = LMBridge.Backend.NPU, // Defaulting to NPU as per original implementation
        maxNumTokens = 8192
    )

    override suspend fun initialize() {
        inferenceManager.initialize()
    }

    override suspend fun createConversation(): Conversation {
        // The current LiteRT-LM implementation in ModelInferenceManager 
        // doesn't seem to have a distinct 'Conversation' object, 
        // it just handles prompts. We'll simulate conversation IDs.
        return Conversation(id = "conv_${System.currentTimeMillis()}", messages = emptyList())
    }

    override fun sendMessage(conversationId: String, content: String): Flow<GenerationResult> {
        return inferenceManager.generate(content)
    }

    override suspend fun getConversations(): List<Conversation> {
        return emptyList() // Not supported by current LiteRT-LM implementation
    }

    override suspend fun shutdown() {
        inferenceManager.close()
    }
}
