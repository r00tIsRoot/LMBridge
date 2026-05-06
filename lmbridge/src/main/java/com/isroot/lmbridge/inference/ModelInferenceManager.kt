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
import kotlinx.coroutines.flow.emitAll
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
    ): Flow<GenerationResult> = processChunkedGenerate(prompt, systemInstruction)

    fun generateWithTexts(
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = generateSingle(texts, systemInstruction)

    private fun generateSingle(
        prompt: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = generateSingle(listOf(prompt), systemInstruction)

    private fun generateSingle(
        texts: List<String>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = kotlinx.coroutines.flow.flow {
        val totalTokens = texts.sumOf { estimateTokenCount(it) }
        if (totalTokens <= maxNumTokens) {
            emitAll(executeGenerateSingle(texts, systemInstruction))
        } else {
            val chunks = chunkTexts(texts, maxNumTokens)
            emit(GenerationResult.Token("Processing ${chunks.size} text chunks...\n"))
            chunks.forEachIndexed { index, chunk ->
                emit(GenerationResult.Token("\n--- Text Chunk ${index + 1}/${chunks.size} ---\n"))
                emitAll(
                    executeGenerateSingle(chunk, systemInstruction)
                        .filter { it !is GenerationResult.Done }
                )
            }
            emit(GenerationResult.Done)
        }
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
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }
        return chunks
    }

    private fun executeGenerateSingle(
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

    fun generateWithAudio(
        prompt: String,
        audioBytesList: List<ByteArray>,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    ): Flow<GenerationResult> = callbackFlow {
        val engine = this@ModelInferenceManager.engine
            ?: throw IllegalStateException("Engine not initialized")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
        )

        Logger.d(TAG, "Creating conversation for audio generation")
        val conversation = engine.createConversation(config)
        currentConversation = conversation

        val contents = mutableListOf<Content>()
        audioBytesList.forEach { bytes ->
            contents.add(Content.AudioBytes(bytes))
        }
        contents.add(Content.Text(prompt))

        Logger.d(TAG, "Sending audio messages (${audioBytesList.size} audio parts)")
        conversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    Logger.v(TAG, "onMessage: $message")
                    trySend(GenerationResult.Token(message.toString()))
                }

                override fun onDone() {
                    Logger.d(TAG, "Audio generation completed")
                    trySend(GenerationResult.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Audio generation error", throwable)
                    trySend(GenerationResult.Error(throwable.message ?: "Unknown error"))
                    close()
                }
            },
        )

        awaitClose {
            Logger.d(TAG, "Cancelling audio generation")
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

    private fun estimateTokenCount(text: String): Int {
        val koreanChars = text.count { it.code in 0xAC00..0xD7A3 }
        val otherChars = text.length - koreanChars
        return koreanChars / 2 + otherChars / 4
    }

    private fun splitByTokenLimit(text: String, maxTokens: Int): List<String> {
        val estimatedTokens = estimateTokenCount(text)
        if (estimatedTokens <= maxTokens) {
            return listOf(text)
        }

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

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        Logger.d(TAG, "Split into ${chunks.size} chunks, maxTokens: $maxTokens")
        return chunks
    }

    private fun processChunkedGenerate(
        prompt: String,
        systemInstruction: String,
    ): Flow<GenerationResult> = callbackFlow {
        val chunks = splitByTokenLimit(prompt, maxNumTokens)

        if (chunks.size == 1) {
            generateSingle(prompt, systemInstruction).collect { result ->
                trySend(result)
            }
        } else {
            trySend(GenerationResult.Token("Processing ${chunks.size} chunks...\n"))

            for ((index, chunk) in chunks.withIndex()) {
                Logger.d(TAG, "Processing chunk ${index + 1}/${chunks.size}, tokens: ${estimateTokenCount(chunk)}")

                trySend(GenerationResult.Token("\n--- Chunk ${index + 1}/${chunks.size} ---\n"))

                generateSingle(chunk, systemInstruction).collect { result ->
                    when (result) {
                        is GenerationResult.Token -> trySend(result)
                        is GenerationResult.Done -> { }
                        is GenerationResult.Error -> {
                            trySend(result)
                        }
                    }
                }
            }

            trySend(GenerationResult.Done)
            close()
        }
    }

    fun generateWithFiles(
        prompt: String,
        filePaths: List<String>,
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
        return processChunkedGenerate(contextPrompt, systemInstruction)
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
