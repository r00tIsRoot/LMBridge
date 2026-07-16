package com.isroot.lmbridge.models

import com.isroot.lmbridge.LMBridge

/**
 * LMBridge가 던지거나 스트림으로 전달하는 타입화된 오류.
 *
 * - 초기화/설정 실패는 `suspend` 함수에서 **throw**된다.
 * - 생성 스트림 도중의 실패는 [GenerationChunk.Error]로 **emit**된다.
 *
 * 문자열 단일 오류(구 `GenerationResult.Error(message)`)를 대체하여
 * 소비 앱이 유형별로 분기·복구할 수 있게 한다.
 */
sealed class LMBridgeError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 엔진이 초기화되지 않은 상태에서 추론을 시도. */
    class NotInitialized : LMBridgeError("Engine is not initialized. Call initialize() first.")

    /** 이미 release()된 클라이언트를 재사용. */
    class Released : LMBridgeError("Client has been released and cannot be reused.")

    /** 모델 파일을 찾을 수 없음. */
    class ModelNotFound(path: String?) :
        LMBridgeError("Model file not found${if (path != null) ": $path" else ""}")

    /** 요청한 백엔드를 사용할 수 없음. */
    class BackendUnavailable(backend: LMBridge.Backend, cause: Throwable? = null) :
        LMBridgeError("Backend unavailable: $backend", cause)

    /** 네이티브 메모리 부족. */
    class OutOfMemory(cause: Throwable? = null) : LMBridgeError("Out of memory during inference", cause)

    /** 다운로드 실패. */
    class DownloadFailed(message: String, cause: Throwable? = null) : LMBridgeError(message, cause)

    /** 다운로드 무결성 검증 실패(SHA-256/크기 불일치). */
    class IntegrityCheckFailed(expected: String, actual: String) :
        LMBridgeError("Integrity check failed. expected=$expected actual=$actual")

    /** 추론 중 일반 실패. */
    class InferenceFailed(message: String, cause: Throwable? = null) : LMBridgeError(message, cause)

    /** 사용자/시스템에 의한 생성 취소. */
    class Cancelled : LMBridgeError("Generation was cancelled")

    companion object {
        /**
         * 임의 [Throwable]을 가장 근접한 [LMBridgeError]로 매핑한다.
         * 어댑터 경계에서 litertlm/네이티브 예외를 도메인 오류로 변환하는 데 사용.
         */
        fun from(t: Throwable): LMBridgeError = when (t) {
            is LMBridgeError -> t
            is OutOfMemoryError -> OutOfMemory(t)
            else -> InferenceFailed(t.message ?: t.javaClass.simpleName, t)
        }
    }
}
