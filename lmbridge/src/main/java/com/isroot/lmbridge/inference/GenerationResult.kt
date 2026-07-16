package com.isroot.lmbridge.inference

import com.isroot.lmbridge.models.GenerationChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * (구) 생성 결과 타입.
 *
 * [GenerationChunk]로 대체되었으며, 한 릴리스 주기 동안 마이그레이션 편의를 위해 유지된다.
 */
@Deprecated(
    message = "Use GenerationChunk instead.",
    replaceWith = ReplaceWith("GenerationChunk", "com.isroot.lmbridge.models.GenerationChunk"),
)
sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()
    data object Done : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

/** 새 [GenerationChunk] 스트림을 구 [GenerationResult] 스트림으로 변환(마이그레이션 shim). */
@Suppress("DEPRECATION")
internal fun Flow<GenerationChunk>.asLegacyResults(): Flow<GenerationResult> = map { chunk ->
    when (chunk) {
        is GenerationChunk.Token -> GenerationResult.Token(chunk.text)
        is GenerationChunk.ToolCall ->
            GenerationResult.Token("""{"tool_call":{"name":"${chunk.name}","args":${chunk.argsJson}}}""")
        is GenerationChunk.Done -> GenerationResult.Done
        is GenerationChunk.Error -> GenerationResult.Error(chunk.error.message ?: "Unknown error")
    }
}
