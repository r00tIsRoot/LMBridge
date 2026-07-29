package com.isroot.lmbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

/** DocumentReader의 순수 로직(존재·크기·바이너리·문자셋) 단위 테스트 — 기기 불필요. */
class DocumentReaderTest {

    private fun tempFile(bytes: ByteArray, suffix: String = ".txt"): File =
        File.createTempFile("docreader", suffix).apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `reads utf-8 text content`() {
        val file = tempFile("안녕하세요 hello".toByteArray(Charsets.UTF_8))
        assertEquals("안녕하세요 hello", DocumentReader.read(file.absolutePath))
    }

    @Test
    fun `missing file throws InvalidInput`() {
        val ex = assertThrows(LMBridgeError.InvalidInput::class.java) {
            DocumentReader.read("/no/such/path/does-not-exist.txt")
        }
        assertTrue(ex.message!!.contains("not found or unreadable"))
    }

    @Test
    fun `oversize file throws InvalidInput`() {
        val file = tempFile(ByteArray(64) { 'a'.code.toByte() })
        val ex = assertThrows(LMBridgeError.InvalidInput::class.java) {
            DocumentReader.read(file.absolutePath, maxBytes = 32)
        }
        assertTrue(ex.message!!.contains("too large"))
    }

    @Test
    fun `binary file with NUL byte throws InvalidInput`() {
        val file = tempFile(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0, 3, 4), suffix = ".bin")
        val ex = assertThrows(LMBridgeError.InvalidInput::class.java) {
            DocumentReader.read(file.absolutePath)
        }
        assertTrue(ex.message!!.contains("binary"))
    }

    @Test
    fun `honors non-utf8 charset`() {
        val charset: Charset = charset("EUC-KR")
        val file = tempFile("가나다".toByteArray(charset))
        assertEquals("가나다", DocumentReader.read(file.absolutePath, charset = charset))
    }
}
