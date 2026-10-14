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

    /**
     * 수동 도구 호출([ChatConfig.automaticToolCalling] = false) 시, 앱이 실행한 도구 결과를
     * 모델에 돌려주고 이어지는 응답을 스트리밍한다.
     *
     * 흐름: [send] → [GenerationChunk.ToolCall] 수신 → 앱이 도구 실행 → 이 메서드로 결과 전달.
     */
    fun sendToolResponses(responses: List<ToolResponse>): Flow<GenerationChunk>

    /**
     * 현재 대화의 통계. [GenerationStats.contextTokens]는 항상 채워지며, 처리 속도 등
     * 벤치마크 수치는 `LMBridgeClient.Builder.setEnableBenchmark(true)`일 때만 채워진다.
     */
    suspend fun stats(): GenerationStats

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
 * @property sampler 샘플링 파라미터. `null`이면 모델 번들의 기본값을 사용한다.
 * @property history 대화 시작 전에 주입할 이전 메시지(대화 복원/퓨샷 예시).
 * @property thinking 추론(thinking) 설정. `null`이면 모델 기본값. 추론 텍스트는
 *   [GenerationChunk.Thought]로 답변과 분리되어 전달된다.
 * @property maxOutputTokens 한 응답의 최대 출력 토큰. `null`이면 엔진 기본값.
 * @property automaticToolCalling `true`(기본)면 [Tool.executor]를 엔진이 자동 실행한다.
 *   `false`면 [GenerationChunk.ToolCall]만 전달되고, 앱이 [Chat.sendToolResponses]로 결과를 돌려준다.
 */
data class ChatConfig(
    val systemInstruction: String = "You are a helpful AI assistant.",
    val tools: List<Tool> = emptyList(),
    val sampler: Sampler? = null,
    val history: List<ChatMessage> = emptyList(),
    val thinking: Thinking? = null,
    val maxOutputTokens: Int? = null,
    val automaticToolCalling: Boolean = true,
) {
    init {
        require(maxOutputTokens == null || maxOutputTokens > 0) { "maxOutputTokens must be positive" }
    }
}

/**
 * 샘플링 파라미터.
 *
 * @property topK 상위 K개 토큰에서 샘플링
 * @property topP 누적 확률 P 이내 토큰에서 샘플링
 * @property temperature 무작위성(0에 가까울수록 결정적)
 * @property seed 난수 시드(재현성)
 */
data class Sampler(
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.8,
    val seed: Int = 0,
) {
    init {
        require(topK > 0) { "topK must be positive" }
        require(topP > 0.0 && topP <= 1.0) { "topP must be in (0, 1]" }
        require(temperature >= 0.0) { "temperature must be >= 0" }
    }
}

/**
 * 추론(thinking) 설정.
 *
 * @property enabled 추론 사용 여부
 * @property tokenBudget 추론 토큰 예산. `-1`=무제한(기본), `0`=비활성, 양수=엄격한 상한.
 */
data class Thinking(
    val enabled: Boolean = true,
    val tokenBudget: Int = UNLIMITED,
) {
    init {
        require(tokenBudget >= UNLIMITED) { "tokenBudget must be -1 (unlimited), 0, or positive" }
    }

    companion object {
        const val UNLIMITED = -1
    }
}

/** [ChatConfig.history]에 주입하는 이전 대화 메시지. */
data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, MODEL }

    companion object {
        fun user(text: String) = ChatMessage(Role.USER, text)
        fun model(text: String) = ChatMessage(Role.MODEL, text)
    }
}

/**
 * 수동 도구 호출 시 앱이 모델에 돌려주는 도구 실행 결과.
 *
 * @property name 호출된 도구 이름([GenerationChunk.ToolCall.name])
 * @property resultJson 실행 결과(JSON 문자열)
 */
data class ToolResponse(val name: String, val resultJson: String)

/**
 * 대화 통계.
 *
 * @property contextTokens 현재 대화가 사용 중인 컨텍스트(KV 캐시) 토큰 수
 * @property timeToFirstTokenSeconds 직전 응답의 첫 토큰까지 걸린 시간
 * @property prefillTokens 직전 응답의 입력(prefill) 토큰 수
 * @property decodeTokens 직전 응답의 출력(decode) 토큰 수
 * @property prefillTokensPerSecond 입력 처리 속도
 * @property decodeTokensPerSecond 출력 생성 속도
 */
data class GenerationStats(
    val contextTokens: Int,
    val timeToFirstTokenSeconds: Double? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
)
