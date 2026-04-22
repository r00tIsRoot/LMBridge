package com.isroot.lmbridge.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ToolProvider
import com.isroot.lmbridge.GenerationResult
import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.convertToLiteRtBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

class JvmModelInferenceManager(
    private val modelPath: String? = null,
    private val backend: LMBridge.Backend = LMBridge.Backend.CPU,
    private val maxNumTokens: Int = 8192,
) {
    private var engine: Engine? = null
    private var currentConversation: Conversation? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Logger.d(TAG, "Initializing JvmModelInferenceManager...")
        
        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            // For JVM, we assume the model is already present at a default location 
            // or provided via config. We'll use a default path for now.
            DEFAULT_MODEL_FILE
        } else {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                modelPath
            } else {
                Logger.w(TAG, "Provided model path does not exist: $modelPath, falling back to default")
                DEFAULT_MODEL_FILE
            }
        }

        Logger.d(TAG, "Creating engine with model: $finalModelPath, backend: $backend, maxNumTokens: $maxNumTokens")
        val engineConfig = EngineConfig(
            modelPath = finalModelPath,
            backend = convertToLiteRtBackend(backend),
            maxNumTokens = maxNumTokens,
        )
        engine = Engine(engineConfig).apply {
            Logger.d(TAG, "Calling engine.initialize()")
            initialize()
            Logger.d(TAG, "Engine initialized successfully")
        }
    }

    fun generate(
        prompt: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = processChunkedGenerate(prompt, systemInstruction)

    private fun generateSingle(
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@JvmModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
        )

        Logger.d(TAG, "Creating conversation for text generation")
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        val contents = texts.map { Content.Text(it) }
        
        conversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            conversation.cancelProcess()
        }
    }

    private fun processChunkedGenerate(
        prompt: String,
        systemInstruction: String,
    ): Flow<GenerationResult> = callbackFlow {
        // Simplified: for JVM PoC, we'll use single generate first.
        // In full impl, we'll port the splitByTokenLimit logic.
        generateSingle(listOf(prompt), systemInstruction).collect { result ->
            trySend(result)
        }
    }

    fun stopGeneration() {
        currentConversation?.cancelProcess()
    }

    fun close() {
        currentConversation?.close()
        engine?.close()
    }

    companion object {
        private const val TAG = "JvmModelInferenceManager"
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
        private const val DEFAULT_SYSTEM_INSTRUCTION = "You are a helpful AI assistant."
    }
}
