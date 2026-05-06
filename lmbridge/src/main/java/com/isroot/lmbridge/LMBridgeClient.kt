package com.isroot.lmbridge

import android.content.Context
import android.graphics.Bitmap
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

    fun generate(prompt: String): Flow<GenerationResult> {
        return inferenceManager.generate(prompt)
    }

    fun generateWithImages(prompt: String, images: List<Bitmap>): Flow<GenerationResult> {
        return inferenceManager.generateWithImages(prompt, images)
    }

    fun generateWithFiles(
        prompt: String,
        filePaths: List<String>,
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithFiles(prompt, filePaths)
    }

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithTools(prompt, tools)
    }

    fun generateWithInput(prompt: String, input: MultimodalInput): Flow<GenerationResult> = flow {
        val texts = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Text>()
        val promptText = if (texts.isEmpty()) prompt else texts.joinToString(" ") { it.text }

        val conversation = inferenceManager.createSession()
        try {
            val chunks = if (inferenceManager.estimateTokenCount(promptText) > maxNumTokens) {
                inferenceManager.splitByTokenLimit(promptText, maxNumTokens)
            } else {
                listOf(promptText)
            }

            if (chunks.size > 1) {
                emit(GenerationResult.Token("Processing ${chunks.size} chunks...\n"))
            }

            chunks.forEachIndexed { index, chunk ->
                if (chunks.size > 1) {
                    emit(GenerationResult.Token("\n--- Chunk ${index + 1}/${chunks.size} ---\n"))
                }

                val chunkInput = input.copy(
                    parts = input.parts.filter { it !is com.isroot.lmbridge.models.MultimodalContent.Text } + 
                            com.isroot.lmbridge.models.MultimodalContent.Text(chunk)
                )
                
                val resultFlow = inferenceManager.executeGenerate(conversation, chunkInput)
                emitAll(resultFlow.filter { it !is GenerationResult.Done })
            }
            emit(GenerationResult.Done)
        } finally {
            conversation.close()
        }
    }

    fun generateWithAudio(
        prompt: String,
        audioBytes: ByteArray,
        systemInstruction: String = "You are a helpful AI assistant."
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithAudio(prompt, listOf(audioBytes), systemInstruction)
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
