package com.isroot.lmbridge.inference

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelInferenceManagerTest {
    private lateinit var modelInferenceManager: ModelInferenceManager
    private val mockContext = mockk<Context>()

    @Before
    fun setUp() {
        modelInferenceManager = ModelInferenceManager(
            context = mockContext,
            modelPath = null,
            backend = com.isroot.lmbridge.LMBridge.Backend.CPU,
            maxNumTokens = 8192
        )
    }

    @Test
    fun `splitByTokenLimit should return a single chunk when text is within limit`() {
        val text = "안녕하세요. 반갑습니다."
        val limit = 100
        val result = modelInferenceManager.splitByTokenLimit(text, limit)
        
        assertEquals(1, result.size)
        assertEquals(text, result[0])
    }

    @Test
    fun `splitByTokenLimit should split text into multiple chunks when it exceeds limit`() {
        // 한글은 대략 2자로 계산되므로, 매우 긴 텍스트를 생성하여 제한을 초과하게 만듭니다.
        val longText = "가나다라마바사".repeat(100) // 충분히 긴 텍스트
        val limit = 10 // 매우 작은 제한 설정
        
        val result = modelInferenceManager.splitByTokenLimit(longText, limit)
        
        assertTrue("Result should be split into multiple chunks", result.size > 1)
        assertTrue("All chunks combined should not lose text content", 
            result.joinToString("\n").contains("가나다라마바사"))
    }

    @Test
    fun `splitByTokenLimit should handle empty text`() {
        val text = ""
        val limit = 100
        val result = modelInferenceManager.splitByTokenLimit(text, limit)
        
        assertEquals(1, result.size)
        assertEquals("", result[0])
    }

    @Test
    fun `splitByTokenLimit should split by line breaks to avoid cutting mid-sentence`() {
        val text = """
            첫 번째 문장입니다.
            두 번째 문장은 매우 길어서 제한을 초과할 가능성이 높습니다.
            세 번째 문장입니다.
        """.trimIndent()
        
        // 두 번째 문장 중간에서 잘리도록 매우 작은 제한 설정
        val limit = 5 
        val result = modelInferenceManager.splitByTokenLimit(text, limit)
        
        assertTrue("Should split into multiple chunks", result.size > 1)
        // 줄바꿈 기준으로 잘렸으므로, 각 청크는 문장의 시작부분을 포함해야 함
        assertTrue(result.any { it.contains("첫 번째") })
        assertTrue(result.any { it.contains("두 번째") })
        assertTrue(result.any { it.contains("세 번째") })
    }
}
