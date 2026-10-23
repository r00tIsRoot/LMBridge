package com.isroot.lmbridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isroot.lmbridge.models.LMBridgeError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 초기화 실패 분류 검증. 모델 파일 문제는 백엔드와 무관하게 [LMBridgeError.InvalidModel]이어야
 * 소비 앱이 GPU→CPU 재시도를 헛되이 하지 않는다.
 *
 * 인자: `modelPath`(잘린 파일을 만들 원본, 기본 gemma-4-E2B-it), `backend`(기본 GPU).
 */
@RunWith(AndroidJUnit4::class)
class InitFailureTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val args = InstrumentationRegistry.getArguments()
    private val backend = LMBridge.Backend.valueOf(args.getString("backend") ?: "GPU")

    private fun initError(path: String): Throwable? = runBlocking {
        val client = LMBridgeClient.Builder(context).setModelPath(path).setBackend(backend).setMaxNumTokens(1024).build()
        try {
            client.initialize()
            null
        } catch (e: Throwable) {
            e
        } finally {
            client.release()
        }
    }

    /** 헤더가 깨진 파일은 litertlm 0.18에서 JNI 중단(SIGABRT)을 일으킨다 → 엔진 생성 전에 걸러야 한다. */
    @Test
    fun garbageFile_isInvalidModel_withoutCrash() {
        val file = File(context.cacheDir, "garbage.litertlm").apply { writeBytes(ByteArray(1 shl 20) { (it * 31 + 0x8a).toByte() }) }
        try {
            val e = initError(file.path)
            assertTrue("expected InvalidModel but was $e", e is LMBridgeError.InvalidModel)
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedModel_isInvalidModel_notBackendUnavailable() {
        val source = File(args.getString("modelPath") ?: "/data/local/tmp/lmbridge/gemma-4-E2B-it.litertlm")
        assumeTrue("source model not found: $source", source.canRead())
        val file = File(context.cacheDir, "truncated.litertlm")
        source.inputStream().use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                repeat(40) { out.write(buf, 0, input.read(buf)) }
            }
        }
        try {
            val e = initError(file.path)
            assertTrue("expected InvalidModel but was $e", e is LMBridgeError.InvalidModel)
        } finally {
            file.delete()
        }
    }
}
