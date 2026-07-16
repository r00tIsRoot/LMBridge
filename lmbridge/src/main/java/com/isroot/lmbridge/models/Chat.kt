package com.isroot.lmbridge.models

import kotlinx.coroutines.flow.Flow

/**
 * 상태 유지 대화 세션.
 *
 * litertlm `Conversation`을 감싼 LMBridge 자체 타입으로, 히스토리를 유지한 채
 * 여러 번 [send]할 수 있다. 사용 후 [close]로 자원을 해제해야 한다.
 */
interface Chat {
    /** 텍스트 프롬프트를 전송하고 응답을 스트리밍한다. */
    fun send(prompt: String): Flow<GenerationChunk>

    /** 멀티모달 입력을 전송하고 응답을 스트리밍한다. */
    fun send(input: MultimodalInput): Flow<GenerationChunk>

    /** 진행 중인 생성을 취소한다. */
    fun stop()

    /** 세션 자원을 해제한다. 이후 이 인스턴스는 사용할 수 없다. */
    fun close()
}

/**
 * [Chat] 생성 설정.
 *
 * @property systemInstruction 시스템 지시문
 * @property tools 사용 가능한 도구 목록(litertlm 타입 비노출)
 */
data class ChatConfig(
    val systemInstruction: String = "You are a helpful AI assistant.",
    val tools: List<Tool> = emptyList(),
)
