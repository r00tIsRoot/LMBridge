package com.isroot.lmbridge.inference

import com.isroot.lmbridge.models.Chat
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.MultimodalContent
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.flow.Flow

/**
 * [EngineSession]을 공개 [Chat] 인터페이스로 노출하는 어댑터.
 * litertlm 타입을 소비 앱에 드러내지 않는다.
 */
internal class EngineSessionChat(
    private val session: EngineSession,
) : Chat {

    override fun send(prompt: String): Flow<GenerationChunk> =
        session.send(listOf(MultimodalContent.Text(prompt)))

    override fun send(input: MultimodalInput): Flow<GenerationChunk> =
        session.send(input.parts)

    override fun stop() = session.cancel()

    override fun close() = session.close()
}
