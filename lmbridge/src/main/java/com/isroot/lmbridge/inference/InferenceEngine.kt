package com.isroot.lmbridge.inference

import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.MultimodalContent
import kotlinx.coroutines.flow.Flow

/**
 * 추론 엔진 어댑터 경계.
 *
 * 상위 레이어(Facade/Domain)는 이 인터페이스만 참조하며 litertlm 타입을 알지 못한다.
 * 구현체([LiteRtEngineAdapter])만 `com.google.ai.edge.litertlm.*`를 import한다.
 */
internal interface InferenceEngine {
    val isInitialized: Boolean

    /** 엔진을 초기화한다. 멱등이 아니며, 호출자(Facade)가 상태를 관리한다. */
    suspend fun initialize(init: EngineInit)

    /** 새 대화 세션을 생성한다. */
    fun newSession(config: ChatConfig): EngineSession

    /** 엔진과 모든 자원을 해제한다. */
    fun close()
}

/** 하나의 대화 세션(litertlm `Conversation` 래핑). */
internal interface EngineSession {
    /** [parts]를 전송하고 응답을 스트리밍한다. */
    fun send(parts: List<MultimodalContent>): Flow<GenerationChunk>

    /** 진행 중인 생성을 취소한다. */
    fun cancel()

    /** 세션 자원을 해제한다. */
    fun close()
}

/** 엔진 초기화 파라미터. 모델 경로는 상위에서 확정하여 전달한다. */
internal data class EngineInit(
    val modelPath: String,
    val backend: LMBridge.Backend,
    val maxTokens: Int,
    val cacheDir: String? = null,
    // MTP(speculative decoding)는 임베더 lookup 테이블을 포함한 전용 모델에서만 동작한다.
    // litert-community의 일반 .litertlm(Gemma3-1B / Qwen2.5 / DeepSeek 등)은 이를 포함하지
    // 않아, 켜면 초기화가 `embedding_lookup == nullptr`(RET_CHECK)로 실패한다. → 기본 off.
    val enableSpeculativeDecoding: Boolean = false,
    // 비전(이미지) 입력을 받으려면 엔진이 이미지 슬롯을 미리 할당해야 한다. null/0이면
    // 비전이 비활성(native `max_num_images: 0`)이라, 멀티모달 모델이라도 이미지를 주입하면
    // 네이티브가 크래시한다. 텍스트 전용 모델의 기본 동작을 보존하기 위해 기본값은 null(비활성).
    val maxNumImages: Int? = null,
    // 비전 인코더 백엔드. null이면 [maxNumImages]>0일 때 [backend]를 사용한다.
    // 이미지 샘플러(OpenCL image sampler)가 없는 GPU에서는 CPU 비전으로 분리 지정할 수 있다.
    val visionBackend: LMBridge.Backend? = null,
    // 오디오 인코더 백엔드. null이면 오디오가 비활성이다(오디오 입력 시 크래시 방지).
    val audioBackend: LMBridge.Backend? = null,
)
