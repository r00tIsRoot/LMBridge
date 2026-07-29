package com.isroot.lmbridge.models

import java.io.File
import java.nio.charset.Charset

/**
 * 로컬 텍스트 문서를 읽어 컨텍스트 주입용 문자열로 변환한다.
 *
 * 실패를 조용히 삼키지 않고 [LMBridgeError.InvalidInput]으로 표면화한다. 순수
 * JVM 로직이라 단위 테스트가 가능하다(Android 의존 없음).
 *
 * 텍스트 문서만 지원한다. PDF·오피스 등 바이너리 문서는 litertlm에 문서 `Content`
 * 타입이 없어 범위 밖이며, 선두 NUL 바이트 감지로 바이너리를 걸러 오류로 알린다.
 */
internal object DocumentReader {
    /** 문서 텍스트 크기 상한(초과 시 오류). */
    const val MAX_DOCUMENT_BYTES: Long = 1L * 1024 * 1024

    /** 바이너리 판별을 위해 선두에서 검사할 최대 바이트 수. */
    private const val BINARY_SNIFF_BYTES = 8000

    /**
     * @throws LMBridgeError.InvalidInput 파일 없음/읽기 불가/크기 초과/바이너리인 경우.
     */
    fun read(
        path: String,
        charset: Charset = Charsets.UTF_8,
        maxBytes: Long = MAX_DOCUMENT_BYTES,
    ): String {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            throw LMBridgeError.InvalidInput("Document not found or unreadable: $path")
        }
        val length = file.length()
        if (length > maxBytes) {
            throw LMBridgeError.InvalidInput("Document too large: $length bytes (max $maxBytes): $path")
        }
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            throw LMBridgeError.InvalidInput("Failed to read document: $path", e)
        }
        if (looksBinary(bytes)) {
            throw LMBridgeError.InvalidInput("Document appears to be binary (unsupported): $path")
        }
        return String(bytes, charset)
    }

    /** 선두 일부에 NUL 바이트가 있으면 바이너리로 간주한다(PDF/이미지/실행파일 등). */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, BINARY_SNIFF_BYTES)
        for (i in 0 until n) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }
}
