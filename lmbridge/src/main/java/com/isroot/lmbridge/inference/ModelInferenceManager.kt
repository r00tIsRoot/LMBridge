package com.isroot.lmbridge.inference

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ToolProvider
import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.convertToLiteRtBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelInferenceManager(
    private val context: Context,
    private val modelPath: String? = null,
    private val backend: LMBridge.Backend = LMBridge.Backend.NPU,
    private val maxNumTokens: Int = 8192,
) {
    private var engine: Engine? = null
    private var currentConversation: Conversation? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Logger.d(TAG, "Initializing ModelInferenceManager...")
        Logger.d(TAG, "modelPath parameter value: $modelPath")
        Logger.d(TAG, "modelPath.isNullOrEmpty(): ${modelPath.isNullOrEmpty()}")
        Logger.d(TAG, "backend: $backend")
        Logger.d(TAG, "maxNumTokens: $maxNumTokens")

        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            Logger.d(TAG, "Model path not provided, extracting asset: $DEFAULT_MODEL_FILE")
            extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        } else {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                Logger.d(TAG, "Using provided model path: $modelPath")
                modelPath
            } else {
                Logger.w(TAG, "Provided model path does not exist: $modelPath, falling back to asset extraction")
                extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
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

    private fun extractAssetIfNeeded(context: Context, assetFileName: String): String {
        val outFile = File(context.filesDir, assetFileName)
        if (!outFile.exists()) {
            context.assets.open(assetFileName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return outFile.absolutePath
    }

    fun generate(
        prompt: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@ModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
        )

        Logger.d(TAG, "Creating conversation for text generation")
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        Logger.d(TAG, "Sending message: $prompt")

        conversation.sendMessageAsync(
            Contents.of(prompt),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    Logger.v(TAG, "onMessage: $message")
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    Logger.d(TAG, "Generation completed")
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Generation error", throwable)
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            Logger.d(TAG, "Cancelling generation")
            conversation.cancelProcess()
        }
    }

    fun generateWithTexts(
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@ModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
        )

        Logger.d(TAG, "Creating conversation for text generation with ${texts.size} text contents")
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        val contents = texts.map { Content.Text(it) }
        Logger.d(TAG, "Sending ${contents.size} text contents")

        conversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    Logger.v(TAG, "onMessage: $message")
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    Logger.d(TAG, "Generation completed")
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Generation error", throwable)
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            Logger.d(TAG, "Cancelling generation")
            conversation.cancelProcess()
        }
    }

    fun generateWithImages(
        prompt: String,
        images: List<Bitmap>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@ModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
        )

        Logger.d(TAG, "Creating conversation for multimodal generation (${images.size} images)")
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        val contents = mutableListOf<Content>()
        images.forEach { bitmap ->
            contents.add(Content.ImageBytes(bitmap.toPngBytes()))
        }
        contents.add(Content.Text(prompt))

        Logger.d(TAG, "Sending multimodal message")
        conversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    Logger.v(TAG, "onMessage: $message")
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    Logger.d(TAG, "Multimodal generation completed")
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Multimodal generation error", throwable)
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            Logger.d(TAG, "Cancelling multimodal generation")
            conversation.cancelProcess()
        }
    }

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@ModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            tools = tools,
        )

        Logger.d(TAG, "Creating conversation for tool calling (${tools.size} tools)")
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        Logger.d(TAG, "Sending message with tools")
        conversation.sendMessageAsync(
            Contents.of(prompt),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    Logger.v(TAG, "onMessage: $message")
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    Logger.d(TAG, "Tool calling completed")
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Tool calling error", throwable)
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            Logger.d(TAG, "Cancelling tool calling")
            conversation.cancelProcess()
        }
    }

    fun stopGeneration() {
        Logger.d(TAG, "Stopping generation")
        currentConversation?.cancelProcess()
    }

    fun close() {
        Logger.d(TAG, "Closing ModelInferenceManager")
        currentConversation?.close()
        engine?.close()
    }

    companion object {
        private const val TAG = "ModelInferenceManager"
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
        private const val DEFAULT_SYSTEM_INSTRUCTION = "You are a helpful AI assistant."
    }
}

sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data object Done : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

private fun Bitmap.toPngBytes(): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
