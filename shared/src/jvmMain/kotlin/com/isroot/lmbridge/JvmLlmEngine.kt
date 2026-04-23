package com.isroot.lmbridge

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class JvmLlmEngine(
    modelPath: String? = null,
    backend: LMBridge.Backend = LMBridge.Backend.CPU,
    maxNumTokens: Int = 8192
) : LlmEngine(modelPath, backend, maxNumTokens) {
    private var engine: Engine? = null

    override suspend fun performInitialize() {
        val path = modelPath ?: "gemma-4-E2B-it.litertlm"
        val config = EngineConfig(
            modelPath = path,
            backend = convertToSdkBackend(backend),
            maxNumTokens = maxNumTokens
        )
        
        val newEngine = Engine(config)
        newEngine.initialize()
        this.engine = newEngine
    }

    override fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")
        
        val conversationConfig = ConversationConfig(
            systemInstruction = systemInstruction
        )
        
        val conversation = currentEngine.createConversation(conversationConfig)
        
        conversation.sendMessageAsync(content).collect { token ->
            emit(GenerationResult.Token(token))
        }
    }

    override suspend fun performShutdown() {
        engine?.close()
        engine = null
    }

    private fun convertToSdkBackend(backend: LMBridge.Backend): Backend {
        return when (backend) {
            LMBridge.Backend.CPU -> Backend.CPU()
            LMBridge.Backend.GPU -> Backend.GPU()
            LMBridge.Backend.NPU -> Backend.NPU()
        }
    }
}

    }

    override fun sendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")
        
        val conversationConfig = ConversationConfig(
            systemInstruction = systemInstruction
        )
        
        val conversation = currentEngine.createConversation(conversationConfig)
        
        try {
            conversation.sendMessageAsync(content).collect { token ->
                emit(GenerationResult.Token(token))
            }
            emit(GenerationResult.Done)
        } catch (e: Exception) {
            emit(GenerationResult.Error(e.message ?: "Unknown error occurred during generation"))
        }
    }

    override suspend fun shutdown() {
        engine?.close()
        engine = null
    }

    private fun convertToSdkBackend(backend: LMBridge.Backend): Backend {
        return when (backend) {
            LMBridge.Backend.CPU -> Backend.CPU()
            LMBridge.Backend.GPU -> Backend.GPU()
            LMBridge.Backend.NPU -> Backend.NPU()
        }
    }
}
