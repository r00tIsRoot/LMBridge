package com.isroot.lmbridge.models

/**
 * 생성 스트림의 단위 이벤트.
 *
 * 소비 앱은 [Flow][kotlinx.coroutines.flow.Flow]로 이 타입을 수신한다.
 * litertlm 타입을 노출하지 않는 LMBridge 자체 출력 타입이다(어댑터 경계).
 */
sealed interface GenerationChunk {
    /** 스트리밍 토큰(부분 텍스트). */
    data class Token(val text: String) : GenerationChunk

    /**
     * 모델이 요청한 도구 호출.
     *
     * @property name 도구 이름
     * @property argsJson 인자(JSON 문자열)
     */
    data class ToolCall(val name: String, val argsJson: String) : GenerationChunk

    /** 생성 정상 종료. */
    data object Done : GenerationChunk

    /** 생성 중 오류로 종료. 스트림은 이 이벤트 이후 종료된다. */
    data class Error(val error: com.isroot.lmbridge.models.LMBridgeError) : GenerationChunk
}
