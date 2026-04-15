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
) {
    private var engine: Engine? = null
    private var currentConversation: Conversation? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        } else {
            modelPath
        }

        val engineConfig = EngineConfig(
            modelPath = finalModelPath,
            backend = Backend.NPU(),
        )
        engine = Engine(engineConfig).apply {
            initialize()
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

        val conversation = engine.createConversation(config)
        currentConversation = conversation

        conversation.sendMessageAsync(
            Contents.of(prompt),
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

        val conversation = engine.createConversation(config)
        currentConversation = conversation

        val contents = mutableListOf<Content>()
        images.forEach { bitmap ->
            contents.add(Content.ImageBytes(bitmap.toPngBytes()))
        }
        contents.add(Content.Text(prompt))

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

        val conversation = engine.createConversation(config)
        currentConversation = conversation

        conversation.sendMessageAsync(
            Contents.of(prompt),
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

    fun stopGeneration() {
        currentConversation?.cancelProcess()
    }

    fun close() {
        currentConversation?.close()
        engine?.close()
    }

    companion object {
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
