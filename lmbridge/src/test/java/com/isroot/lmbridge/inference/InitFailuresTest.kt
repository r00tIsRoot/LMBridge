package com.isroot.lmbridge.inference

import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.models.LMBridgeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 초기화 실패 분류·헤더 검사 단위 테스트 — 기기 불필요. 메시지는 litertlm 0.18 실기기 출력. */
class InitFailuresTest {

    private fun tempFile(name: String, bytes: ByteArray): File =
        File.createTempFile("init", name).also { it.writeBytes(bytes); it.deleteOnExit() }

    @Test
    fun `truncated model is classified as invalid model`() {
        val e = RuntimeException(
            "Failed to create engine: NOT_FOUND: ERROR: [llm_litert_compiled_model_executor_factory.cc:91]\n" +
                "└ tf_lite_prefill_decode not found in the model.",
        )
        val result = InitFailures.classify(e, LMBridge.Backend.GPU, "/m.litertlm")
        assertTrue(result is LMBridgeError.InvalidModel)
        assertEquals("/m.litertlm", (result as LMBridgeError.InvalidModel).path)
    }

    @Test
    fun `model marker in cause chain is detected`() {
        val e = IllegalStateException("wrapper", RuntimeException("INVALID_ARGUMENT: Invalid magic number or failed to read"))
        assertTrue(InitFailures.classify(e, LMBridge.Backend.CPU, "/m") is LMBridgeError.InvalidModel)
    }

    @Test
    fun `unknown failure is backend unavailable with backend kept`() {
        val e = RuntimeException("Failed to create engine: INTERNAL: OpenCL not available")
        val result = InitFailures.classify(e, LMBridge.Backend.GPU, "/m")
        assertTrue(result is LMBridgeError.BackendUnavailable)
        assertEquals(LMBridge.Backend.GPU, (result as LMBridgeError.BackendUnavailable).backend)
    }

    @Test
    fun `out of memory stays out of memory`() {
        assertTrue(InitFailures.classify(OutOfMemoryError(), LMBridge.Backend.GPU, "/m") is LMBridgeError.OutOfMemory)
    }

    @Test
    fun `valid litertlm header passes`() {
        val f = tempFile(".litertlm", "LITERTLM".toByteArray() + ByteArray(24))
        assertNull(InitFailures.checkHeader(f.path))
    }

    @Test
    fun `garbage litertlm header is rejected`() {
        val f = tempFile(".litertlm", byteArrayOf(0x8a.toByte(), 1, 2, 3, 4, 5, 6, 7, 8))
        assertNotNull(InitFailures.checkHeader(f.path))
    }

    @Test
    fun `file shorter than header is rejected`() {
        assertNotNull(InitFailures.checkHeader(tempFile(".litertlm", "LITE".toByteArray()).path))
    }

    @Test
    fun `other extensions are not checked`() {
        assertNull(InitFailures.checkHeader(tempFile(".task", byteArrayOf(1, 2, 3)).path))
    }
}
