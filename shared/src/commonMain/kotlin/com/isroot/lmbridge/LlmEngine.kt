package com.isroot.lmbridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * LlmEngine is the base class for LLM inference orchestration.
 * It defines the high-level flow and delegates the actual SDK calls to platform implementations.
 */
abstract class LlmEngine(
    protected val modelPath: String? = null,
    protected val backend: LMBridge.Backend = LMBridge.Backend.CPU,
    protected val maxNumTokens: Int = 8192
) {
    // Platform-specific SDK engine handle
    protected var isInitialized = false

    open suspend fun initialize() {
        try {
            performInitialize()
            isInitialized = true
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize LLM engine: ${e.message}", e)
        }
    }

    open fun sendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        if (!isInitialized) throw IllegalStateException("Engine not initialized")
        
        try {
            performSendMessage(content, systemInstruction).collect { result ->
                emit(result)
            }
            emit(GenerationResult.Done)
        } catch (e: Exception) {
            emit(GenerationResult.Error(e.message ?: "Unknown error occurred during generation"))
        }
    }

    open suspend fun shutdown() {
        performShutdown()
        isInitialized = false
    }

    // --- Platform-specific SDK glue methods ---
    protected abstract suspend fun performInitialize()
    protected abstract fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult>
    protected abstract suspend fun performShutdown()
}

sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data object Done : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

    }

    open fun sendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = flow {
        if (!isInitialized) throw IllegalStateException("Engine not initialized")
        
        try {
            performSendMessage(content, systemInstruction).collect { result ->
                emit(result)
            }
            emit(GenerationResult.Done)
        } catch (e: Exception) {
            emit(GenerationResult.Error(e.message ?: "Unknown error occurred during generation"))
        }
    }

    open suspend fun shutdown() {
        performShutdown()
        isInitialized = false
    }

    // --- Platform-specific SDK glue methods ---
    protected abstract suspend fun performInitialize()
    protected abstract fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult>
    protected abstract suspend fun performShutdown()
}

sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data object Done : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}
