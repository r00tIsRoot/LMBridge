package com.isroot.lmbridge.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.isroot.lmbridge.models.MultimodalContent
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message

class ModelInferenceManager(
    private val context: Context,
    private val modelPath: String? = null
) {
    // 실제 LiteRT-LM 엔진 인스턴스
    private var llmEngine: Engine? = null
    
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            extractAssetIfNeeded(context, "gemma-4-E2B-it.litertlm")
        } else {
            modelPath
        }

        val engineConfig = EngineConfig(
            modelPath = finalModelPath,
            backend = Backend.NPU(),
        )
        llmEngine = Engine(engineConfig).apply {
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

    suspend fun generate(input: MultimodalInput): String = withContext(Dispatchers.IO) {
        // Build Contents from multimodal input parts
        val contentsBuilder = Contents.Builder()
        
        for (part in input.parts) {
            when (part) {
                is MultimodalContent.TextContent -> {
                    contentsBuilder.add(Content.Text(part.text))
                }
                is MultimodalContent.ImageContent -> {
                    // Convert Bitmap to PNG bytes for LiteRT-LM
                    val imageBytes = part.image.toByteArray()
                    contentsBuilder.add(Content.Blob("image/png", imageBytes))
                }
                is MultimodalContent.AudioContent -> {
                    contentsBuilder.add(Content.Blob("audio/wav", part.audioBytes))
                }
                is MultimodalContent.VideoContent -> {
                    // Video: use Content.ImageFile for file path
                    val videoFile = File(part.videoUri)
                    if (videoFile.exists()) {
                        contentsBuilder.add(Content.ImageFile(part.videoUri))
                    }
                }
            }
        }
        
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of("You are a helpful assistant."),
            initialMessages = emptyList(),
        )
        
        val responseBuilder = StringBuilder()
        llmEngine?.createConversation(conversationConfig)?.use { conversation ->
            // Send multimodal content and collect streaming response
            conversation.sendMessageAsync(contentsBuilder.build()).collect { token ->
                responseBuilder.append(token)
            }
        }
        
        return@withContext responseBuilder.toString().ifEmpty { "Error: Failed to generate response" }
    }
    
    private fun Bitmap.toByteArray(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
    
    fun close() {
        llmEngine?.close()
    }
}