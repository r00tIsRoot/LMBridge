package com.isroot.lmbridge.inference

import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.models.LMBridgeError
import java.io.File

/**
 * 엔진 초기화 실패를 "모델 파일 문제"와 "백엔드 문제"로 가른다.
 *
 * 소비 앱은 [LMBridgeError.BackendUnavailable]을 받으면 다른 백엔드로 재시도하므로,
 * 다른 백엔드로도 똑같이 실패할 모델 문제는 [LMBridgeError.InvalidModel]로 구분해야 한다.
 * litertlm은 원인을 예외 타입이 아닌 메시지(absl 상태 문자열)로만 전달해 메시지로 판정한다.
 */
internal object InitFailures {

    /** `.litertlm` 컨테이너 첫 8바이트. */
    private val LITERTLM_MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)

    // 기기에서 확인한 모델 문제 메시지(litertlm 0.18):
    // - 잘린 파일: "NOT_FOUND: ... tf_lite_prefill_decode not found in the model."
    // - 헤더 손상: "INVALID_ARGUMENT: Invalid magic number or failed to read: ..."
    private val MODEL_MARKERS = listOf(
        "not found in the model",
        "invalid magic number",
        "failed to read",
        "data_loss",
    )

    /**
     * `.litertlm` 파일의 헤더(매직 넘버)를 검사한다. 맞지 않으면 사유를, 문제없으면 null을 돌려준다.
     *
     * 헤더가 깨진 파일을 엔진에 넘기면 litertlm 0.18이 원본 바이트를 담은 오류 메시지를 JNI 문자열로
     * 만들다 "input is not valid Modified UTF-8"로 프로세스를 중단(SIGABRT)시킨다. 예외로 잡을 수
     * 없으므로 엔진 생성 전에 걸러낸다. 확장자가 `.litertlm`이 아닌 파일은 검사하지 않는다.
     */
    fun checkHeader(path: String): String? {
        val file = File(path)
        if (!file.name.endsWith(".litertlm", ignoreCase = true)) return null
        val head = ByteArray(LITERTLM_MAGIC.size)
        val read = runCatching { file.inputStream().use { it.read(head) } }
            .getOrElse { return "cannot read file: ${it.message}" }
        return if (read == head.size && head.contentEquals(LITERTLM_MAGIC)) null else "not a .litertlm file (bad header)"
    }

    /** 엔진 생성 중 발생한 [t]를 도메인 오류로 변환한다. */
    fun classify(t: Throwable, backend: LMBridge.Backend, modelPath: String): LMBridgeError {
        if (t is OutOfMemoryError) return LMBridgeError.OutOfMemory(t)
        val message = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
        return if (MODEL_MARKERS.any { it in message }) {
            LMBridgeError.InvalidModel(modelPath, t)
        } else {
            LMBridgeError.BackendUnavailable(backend, t)
        }
    }
}
