package com.isroot.lmbridge.inference

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
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
    private val backend: LMBridge.Backend = LMBridge.Backend.CPU,
    private val maxNumTokens: Int = 1024,
) {
    private var engine: Engine? = null

    companion object {
        private const val TAG = "ModelInferenceManager"
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
        private const val DEFAULT_SYSTEM_INSTRUCTION = "You are a helpful AI assistant."
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Logger.d(TAG, "Initializing ModelInferenceManager...")

        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        } else {
            val modelFile = File(modelPath)
            if (modelFile.exists()) modelPath else extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        }

        // Enable MTP via speculative decoding
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableSpeculativeDecoding = true

        val engineConfig = EngineConfig(
            modelPath = finalModelPath,
            backend = convertToLiteRtBackend(backend),
            maxNumTokens = maxNumTokens,
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

    fun createConversation(
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
        tools: List<ToolProvider> = emptyList()
    ): Conversation {
        Logger.d(TAG, "Creating new conversation session")
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            tools = tools
        )
        return engine?.createConversation(config)
            ?: throw IllegalStateException("Engine not initialized")
    }

    fun generate(
        prompt: String,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> {
        val conv = conversation ?: createConversation(systemInstruction)
        return processChunkedGenerate(conv, prompt, systemInstruction)
    }

    fun generateWithTexts(
        texts: List<String>,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> {
        val conv = conversation ?: createConversation(systemInstruction)
        return generateSingle(conv, texts, systemInstruction)
    }

    private fun generateSingle(
        conversation: Conversation,
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
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
            }
        )
        awaitClose { conversation.cancelProcess() }
    }

    fun generateWithImages(
        prompt: String,
        images: List<Bitmap>,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val conv = conversation ?: createConversation(systemInstruction)
        val contents = mutableListOf<Content>()
        images.forEach { bitmap -> contents.add(Content.ImageBytes(bitmap.toPngBytes())) }
        contents.add(Content.Text(prompt))

        conv.sendMessageAsync(
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
            }
        )
        awaitClose { conv.cancelProcess() }
    }

    fun generateWithAudio(
        prompt: String,
        audioBytesList: List<ByteArray>,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val conv = conversation ?: createConversation(systemInstruction)
        val contents = mutableListOf<Content>()
        audioBytesList.forEach { bytes -> contents.add(Content.AudioBytes(bytes)) }
        contents.add(Content.Text(prompt))

        conv.sendMessageAsync(
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
            }
        )
        awaitClose { conv.cancelProcess() }
    }

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val conv = conversation ?: createConversation(systemInstruction, tools)

        conv.sendMessageAsync(
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
            }
        )
        awaitClose { conv.cancelProcess() }
    }

    fun stopGeneration(conversation: Conversation? = null) {
        conversation?.cancelProcess()
    }

    fun close() {
        engine?.close()
    }

    private fun estimateTokenCount(text: String): Int {
        var koreanChars = 0
        var englishChars = 0
        var otherChars = 0

        for (char in text) {
            when {
                char.code in 0xAC00..0xD7A3 -> koreanChars++
                char in 'a'..'z' || char in 'A'..'Z' -> englishChars++
                else -> otherChars++
            }
        }

        return (koreanChars / 2.0).toInt() + (englishChars / 4.0).toInt() + otherChars
    }

    internal fun splitByTokenLimit(text: String, maxTokens: Int): List<String> {
        val estimatedTokens = estimateTokenCount(text)
        if (estimatedTokens <= maxTokens) return listOf(text)

        val chunks = mutableListOf<String>()
        val lines = text.split("\n")
        var currentChunk = StringBuilder()
        var currentTokens = 0

        for (line in lines) {
            val lineTokens = estimateTokenCount(line)
            
            // 한 줄 자체가 제한을 초과하는 경우 처리
            if (lineTokens > maxTokens) {
                // 현재까지 모인 청크가 있다면 먼저 저장
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                    currentTokens = 0
                }
                
                // 긴 줄을 강제로 분할 (글자 수 기반으로 대략적 분할)
                var remainingLine = line
                while (remainingLine.isNotEmpty()) {
                    val splitIdx = (maxTokens * 2).coerceAtMost(remainingLine.length) // 대략적인 글자수 기반 분할
                    chunks.add(remainingLine.substring(0, splitIdx))
                    remainingLine = remainingLine.substring(splitIdx)
                }
                continue
            }

            if (currentTokens + lineTokens > maxTokens && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
                currentTokens = 0
            }
            currentChunk.appendLine(line)
            currentTokens += lineTokens
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trim())
        return chunks
    }

    private fun processChunkedGenerate(
        conversation: Conversation,
        prompt: String,
        systemInstruction: String,
    ): Flow<GenerationResult> = callbackFlow {
        val chunks = splitByTokenLimit(prompt, maxNumTokens).apply {
            Logger.d(TAG, "splitedChunk")
        }

        if (chunks.size == 1) {
            generateSingle(conversation, listOf(prompt), systemInstruction).collect { result ->
                trySend(result)
            }
        } else {
            trySend(GenerationResult.Token("Processing ${chunks.size} chunks...\n"))
            for ((index, chunk) in chunks.withIndex()) {
                trySend(GenerationResult.Token("\n--- Chunk ${index + 1}/${chunks.size} ---\n"))
                generateSingle(conversation, listOf(chunk), systemInstruction).collect { result ->
                    if (result !is GenerationResult.Done) trySend(result)
                }
            }
            trySend(GenerationResult.Done)
            close()
        }
    }

    fun generateWithFiles(
        prompt: String,
        filePaths: List<String>,
        conversation: Conversation? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> {
        val fileContents = filePaths.mapNotNull { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    Logger.d(TAG, "Reading file: $path (${file.length()} bytes)")
                    file.readText()
                } else {
                    Logger.w(TAG, "File not found: $path")
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to read file: $path", e)
                null
            }
        }

        val contextPrompt = if (fileContents.isNotEmpty()) {
            """
            |Based on the following document content, answer the question.
            |
            |Document content:
            |${fileContents.joinToString("\n\n---\n\n")}
            |
            |Question: $prompt
            """.trimMargin()
        } else {
            prompt
        }

        Logger.d(TAG, "generateWithFiles: ${filePaths.size} files, prompt length: ${contextPrompt.length}")
        val conv = conversation ?: createConversation(systemInstruction)
        return processChunkedGenerate(conv, contextPrompt, systemInstruction)
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
