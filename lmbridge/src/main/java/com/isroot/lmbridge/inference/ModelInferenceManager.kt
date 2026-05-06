package com.isroot.lmbridge.inference

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelInferenceManager(
    private val context: Context,
    private val modelPath: String? = null,
    private val backend: LMBridge.Backend = LMBridge.Backend.CPU,
    private val maxNumTokens: Int = 8192,
) {
    private var engine: Engine? = null
    private var currentConversation: Conversation? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Logger.d(TAG, "Initializing ModelInferenceManager...")
        
        val finalModelPath = if (modelPath.isNullOrEmpty()) {
            extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        } else {
            val modelFile = File(modelPath)
            if (modelFile.exists()) modelPath else extractAssetIfNeeded(context, DEFAULT_MODEL_FILE)
        }

        val engineConfig = EngineConfig(
            modelPath = finalModelPath,
            backend = convertToLiteRtBackend(backend),
            maxNumTokens = maxNumTokens,
        )
        engine = Engine(engineConfig).apply {
            initialize()
        }
        Logger.d(TAG, "Engine initialized successfully")
    }

    fun generate(
        prompt: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        try {
            emitAll(executeGenerateSingle(conversation, listOf(prompt), systemInstruction))
        } finally {
            conversation.close()
            currentConversation = null
        }
    }

    fun generateWithTexts(
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        try {
            val totalTokens = texts.sumOf { estimateTokenCount(it) }
            if (totalTokens <= maxNumTokens) {
                emitAll(executeGenerateSingle(conversation, texts, systemInstruction))
            } else {
                val chunks = chunkTexts(texts, maxNumTokens)
                emit(GenerationResult.Token("Processing ${chunks.size} text chunks...\n"))
                chunks.forEachIndexed { index, chunkTexts ->
                    emit(GenerationResult.Token("\n--- Text Chunk ${index + 1}/${chunks.size} ---\n"))
                    emitAll(executeGenerateSingle(conversation, chunkTexts, systemInstruction).filter { it !is GenerationResult.Done })
                }
                emit(GenerationResult.Done)
            }
        } finally {
            conversation.close()
            currentConversation = null
        }
    }


    fun generateWithImages(
        prompt: String,
        images: List<Bitmap>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        try {
            emitAll(executeGenerateWithImages(conversation, prompt, images))
        } finally {
            conversation.close()
            currentConversation = null
        }
    }

    fun generateWithAudio(
        prompt: String,
        audioBytesList: List<ByteArray>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        try {
            emitAll(executeGenerateWithAudio(conversation, prompt, audioBytesList))
        } finally {
            conversation.close()
            currentConversation = null
        }
    }

    fun generateWithTools(
        prompt: String,
        tools: List<ToolProvider>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            tools = tools,
        )
        val conversation = engine.createConversation(config)
        currentConversation = conversation
        try {
            emitAll(executeGenerateWithTools(conversation, prompt))
        } finally {
            conversation.close()
            currentConversation = null
        }
    }

    fun generateWithFiles(
        prompt: String,
        filePaths: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val fileContents = filePaths.mapNotNull { path ->
            try {
                val file = File(path)
                if (file.exists()) file.readText() else null
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

        val engine = this@ModelInferenceManager.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        try {
            val texts = listOf(contextPrompt)
            val totalTokens = texts.sumOf { estimateTokenCount(it) }
            if (totalTokens <= maxNumTokens) {
                emitAll(executeGenerateSingle(conversation, texts, systemInstruction))
            } else {
                val chunks = chunkTexts(texts, maxNumTokens)
                emit(GenerationResult.Token("Processing ${chunks.size} text chunks...\n"))
                chunks.forEachIndexed { index, chunk ->
                    emit(GenerationResult.Token("\n--- Text Chunk ${index + 1}/${chunks.size} ---\n"))
                    emitAll(executeGenerateSingle(conversation, chunk, systemInstruction).filter { it !is GenerationResult.Done })
                }
                emit(GenerationResult.Done)
            }
        } finally {
            conversation.close()
            currentConversation = null
        }
    }

    fun stopGeneration() {
        currentConversation?.cancelProcess()
    }

    fun close() {
        currentConversation?.close()
        engine?.close()
    }

    internal fun createSession(systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION): Conversation {
        val engine = this.engine ?: throw IllegalStateException("Engine not initialized")
        val config = ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        return engine.createConversation(config)
    }

    internal fun estimateTokenCount(text: String): Int {
        val koreanChars = text.count { it.code in 0xAC00..0xD7A3 }
        val otherChars = text.length - koreanChars
        return koreanChars / 2 + otherChars / 4
    }

    internal fun splitByTokenLimit(text: String, maxTokens: Int): List<String> {
        val estimatedTokens = estimateTokenCount(text)
        if (estimatedTokens <= maxTokens) return listOf(text)

        val chunks = mutableListOf<String>()
        val lines = text.split("\n")
        val currentChunk = StringBuilder()
        var currentTokens = 0

        for (line in lines) {
            val lineTokens = estimateTokenCount(line)
            if (currentTokens + lineTokens > maxTokens && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
                currentTokens = 0
            }
            currentChunk.appendLine(line)
            currentTokens += lineTokens
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString().trim())
        return chunks
    }

    private fun chunkTexts(texts: List<String>, maxTokens: Int): List<List<String>> {

        val chunks = mutableListOf<List<String>>()
        var currentChunk = mutableListOf<String>()
        var currentTokens = 0
        for (text in texts) {
            val tokens = estimateTokenCount(text)
            if (currentTokens + tokens > maxTokens && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentTokens = 0
            }
            currentChunk.add(text)
            currentTokens += tokens
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)
        return chunks
    }

    internal fun executeGenerateSingle(
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
            },
        )
        awaitClose { conversation.cancelProcess() }
    }

    internal fun executeGenerateWithImages(
        conversation: Conversation,
        prompt: String,
        images: List<Bitmap>,
    ): Flow<GenerationResult> = callbackFlow {
        val contents = mutableListOf<Content>()
        images.forEach { bitmap -> contents.add(Content.ImageBytes(bitmap.toPngBytes())) }
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
        awaitClose { conversation.cancelProcess() }
    }

    internal fun executeGenerateWithAudio(
        conversation: Conversation,
        prompt: String,
        audioBytesList: List<ByteArray>,
    ): Flow<GenerationResult> = callbackFlow {
        val contents = mutableListOf<Content>()
        audioBytesList.forEach { bytes -> contents.add(Content.AudioBytes(bytes)) }
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
        awaitClose { conversation.cancelProcess() }
    }

    internal fun executeGenerateWithTools(
        conversation: Conversation,
        prompt: String,
    ): Flow<GenerationResult> = callbackFlow {
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
        awaitClose { conversation.cancelProcess() }
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
