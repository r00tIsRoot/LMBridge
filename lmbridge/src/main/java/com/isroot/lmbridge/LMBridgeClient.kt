package com.isroot.lmbridge

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.ToolProvider
import com.isroot.lmbridge.download.ModelDownloadManager
import com.isroot.lmbridge.inference.GenerationResult
import com.isroot.lmbridge.inference.ModelInferenceManager
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.flow.Flow

class LMBridgeClient private constructor(
    private val context: Context,
    private val modelPath: String? = null,
    private val backend: LMBridge.Backend = LMBridge.Backend.NPU,
    private val maxNumTokens: Int = 8192,
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

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
    ): Flow<GenerationResult> {
        return inferenceManager.generateWithTools(prompt, tools)
    }

    fun generateWithInput(input: MultimodalInput): Flow<GenerationResult> {
        val texts = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Text>()
        val images = input.parts.filterIsInstance<com.isroot.lmbridge.models.MultimodalContent.Image>()

        return when {
            images.isNotEmpty() -> {
                val prompt = texts.joinToString(" ") { it.text }
                inferenceManager.generateWithImages(prompt, images.map { it.bitmap })
            }
            texts.size > 1 -> {
                inferenceManager.generateWithTexts(texts.map { it.text })
            }
            else -> {
                val prompt = texts.joinToString(" ") { it.text }
                inferenceManager.generate(prompt)
            }
        }
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
        private var backend: LMBridge.Backend = LMBridge.Backend.NPU
        private var maxNumTokens: Int = 8192

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
