package com.isroot.lmbridge

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ToolProvider
import com.isroot.lmbridge.download.ModelDownloadManager
import com.isroot.lmbridge.inference.GenerationResult
import com.isroot.lmbridge.inference.ModelInferenceManager
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class LMBridgeClient private constructor(
    private val context: Context,
    private val modelPath: String? = null,
    private val backend: LMBridge.Backend = LMBridge.Backend.CPU,
    private val maxNumTokens: Int = 1024,
) {
    private val inferenceManager = ModelInferenceManager(context, modelPath, backend, maxNumTokens)
    private val downloadManager = ModelDownloadManager(context)

    suspend fun initialize() {
        inferenceManager.initialize()
    }

    fun newConversation(
        systemInstruction: String = "You are a helpful AI assistant.",
        tools: List<ToolProvider> = emptyList()
    ): Conversation {
        return inferenceManager.createConversation(systemInstruction, tools)
    }

    fun generateWithConversation(
        conversation: Conversation,
        prompt: String,
        systemInstruction: String = "You are a helpful AI assistant.",
    ): Flow<GenerationResult> {
        return inferenceManager.generate(prompt, conversation, systemInstruction)
    }

    fun generate(prompt: String): Flow<GenerationResult> {
        return inferenceManager.generate(prompt = prompt)
    }

    fun generateWithImages(prompt: String, images: List<Bitmap>): Flow<GenerationResult> {
        return inferenceManager.generateWithImages(prompt = prompt, images = images)
    }

    fun generateWithFiles(
        prompt: String,
        filePaths: List<String>,
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithFiles(prompt = prompt, filePaths = filePaths)
    }

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithTools(prompt = prompt, tools = tools)
    }

    fun generateWithInput(input: MultimodalInput): Flow<GenerationResult> {
        val texts = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Text>()
        val images = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Image>()
        val audios = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Audio>()

        return when {
            audios.isNotEmpty() -> {
                val prompt = texts.joinToString(" ") { it.text }
                inferenceManager.generateWithAudio(prompt = prompt, audioBytesList = audios.map { it.bytes })
            }
            images.isNotEmpty() -> {
                val prompt = texts.joinToString(" ") { it.text }
                inferenceManager.generateWithImages(prompt = prompt, images = images.map { it.bitmap })
            }
            texts.size > 1 -> {
                inferenceManager.generateWithTexts(texts = texts.map { it.text })
            }
            else -> {
                val prompt = texts.joinToString(" ") { it.text }
                inferenceManager.generate(prompt = prompt)
            }
        }
    }

    fun generateWithAudio(
        prompt: String,
        audioBytes: ByteArray,
        systemInstruction: String = "You are a helpful AI assistant."
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithAudio(prompt = prompt, audioBytesList = listOf(audioBytes), systemInstruction = systemInstruction)
    }

    fun stopGeneration() {
        inferenceManager.stopGeneration()
    }

    fun release() {
        inferenceManager.close()
    }

    fun getDownloadManager(): ModelDownloadManager = downloadManager

    class Builder(private val context: Context) {
        private var modelPath: String? = null
        private var backend: LMBridge.Backend = LMBridge.Backend.CPU
        private var maxNumTokens: Int = 1024

        fun setModelPath(path: String): Builder {
            modelPath = path
            return this
        }

        fun setBackend(backend: LMBridge.Backend): Builder {
            this.backend = backend
            return this
        }

        fun setMaxNumTokens(maxNumTokens: Int): Builder {
            this.maxNumTokens = maxNumTokens
            return this
        }

        fun build(): LMBridgeClient {
            return LMBridgeClient(context, modelPath, backend, maxNumTokens)
        }
    }
}
