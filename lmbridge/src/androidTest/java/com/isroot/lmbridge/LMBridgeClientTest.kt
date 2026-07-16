package com.isroot.lmbridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 계측 테스트. 실제 엔진/모델이 필요하므로 기기/에뮬레이터에서 실행한다.
 * (모델 에셋 `gemma-4-E2B-it.litertlm`가 소비 앱 assets에 있어야 함)
 */
@RunWith(AndroidJUnit4::class)
class LMBridgeClientTest {

    private lateinit var context: Context
    private lateinit var client: LMBridgeClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = LMBridgeClient.Builder(context)
            .setMaxNumTokens(512)
            .build()
    }

    @Test
    fun testInitialization() = runBlocking {
        client.initialize()
        assertNotNull(client)
        client.release()
    }

    @Test
    fun testInitializeIsIdempotent() = runBlocking {
        client.initialize()
        client.initialize() // 두 번째 호출은 no-op이어야 하며 엔진을 누수하지 않아야 함
        assertNotNull(client)
        client.release()
    }

    @Test
    fun testSimpleGeneration() = runBlocking {
        client.initialize()

        val chunks = client.generate("Hello, who are you?").toList()

        assertTrue("Chunks should not be empty", chunks.isNotEmpty())
        assertTrue("Stream should end with Done", chunks.last() is GenerationChunk.Done)
        assertTrue("Should emit at least one token", chunks.any { it is GenerationChunk.Token })
        client.release()
    }

    @Test
    fun testStatefulChat() = runBlocking {
        client.initialize()

        val chat = client.newChat(ChatConfig(systemInstruction = "You are concise."))
        val chunks = chat.send("Say hi in one word.").toList()

        assertTrue("Chat should produce a Done", chunks.last() is GenerationChunk.Done)
        chat.close()
        client.release()
    }
}
