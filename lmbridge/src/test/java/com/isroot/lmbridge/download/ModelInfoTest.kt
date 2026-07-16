package com.isroot.lmbridge.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ModelInfo의 순수 로직(URL/디렉터리 규칙) 단위 테스트 — 기기 불필요. */
class ModelInfoTest {

    private val model = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/Gemma3-1B-IT",
        modelFile = "gemma3-1b-it-int4.litertlm",
        commitHash = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
        sizeInBytes = 584417280,
    )

    @Test
    fun `download url points to resolve endpoint with commit and file`() {
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/" +
                "42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
            model.toDownloadUrl(),
        )
    }

    @Test
    fun `dir name replaces slash and uses short commit`() {
        val dir = model.toDirName()
        assertEquals("litert-community_Gemma3-1B-IT_42d538a9", dir)
        assertTrue("dir name must not contain path separators", !dir.contains("/"))
    }

    @Test
    fun `sha256 defaults to null for catalog entries`() {
        assertEquals(null, model.sha256)
    }
}
