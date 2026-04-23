package com.isroot.lmbridge

import kotlinx.coroutines.flow.*

/**
 * LlmOrchestrator is the "Brain" of LMBridge.
 * It handles high-level logic like prompt engineering, token-aware chunking, 
 * and coordinating the low-level LlmEngine.
 */
class LlmOrchestrator(
    private val engine: LlmEngine,
    private val config: OrchestratorConfig = OrchestratorConfig()
) {
    data class OrchestratorConfig(
        val maxNumTokens: Int = 8192,
        val defaultSystemInstruction: String = "You are a helpful AI assistant.",
        val chunkOverlap: Int = 100 // Overlap tokens to preserve context between chunks
    )

    /**
     * Generates a response from a prompt. 
     * If the prompt is too long, it automatically splits it into chunks.
     */
    fun generate(
        prompt: String,
        systemInstruction: String = config.defaultSystemInstruction
    ): Flow<GenerationResult> = flow {
        val chunks = splitByTokenLimit(prompt, config.maxNumTokens)
        
        if (chunks.size == 1) {
            // Single chunk: direct stream
            emitAll(engine.sendMessage(prompt, systemInstruction))
        } else {
            // Multi-chunk: sequential processing with context markers
            emit(GenerationResult.Token("Processing ${chunks.size} chunks of input...\n"))
            
            chunks.forEachIndexed { index, chunk ->
                emit(GenerationResult.Token("\n--- Part ${index + 1}/${chunks.size} ---\n"))
                
                engine.sendMessage(chunk, systemInstruction).collect { result ->
                    // We skip 'Done' results for intermediate chunks to keep the stream continuous
                    if (result !is GenerationResult.Done) {
                        emit(result)
                    }
                }
            }
            emit(GenerationResult.Done)
        }
    }

    /**
     * RAG-enhanced generation. Takes extracted context and a question.
     */
    fun generateWithContext(
        context: String,
        question: String,
        systemInstruction: String = config.defaultSystemInstruction
    ): Flow<GenerationResult> {
        val augmentedPrompt = """
            Based on the following provided context, please answer the question accurately.
            
            [CONTEXT]
            $context
            
            [QUESTION]
            $question
        """.trimIndent()
        
        return generate(augmentedPrompt, systemInstruction)
    }

    private fun splitByTokenLimit(text: String, maxTokens: Int): List<String> {
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

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
    }

    private fun estimateTokenCount(text: String): Int {
        // Heuristic for Gemma/LiteRT: Korean ~2 tokens/char, English ~0.25 tokens/char
        val koreanChars = text.count { it.code in 0xAC00..0xD7A3 }
        val otherChars = text.length - koreanChars
        return (koreanChars * 2) + (otherChars / 4) // Adjusted for better safety margin
    }
}
