package com.isroot.lmbridge

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.ChatMessage
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.Sampler
import com.isroot.lmbridge.models.Thinking
import com.isroot.lmbridge.models.Tool
import com.isroot.lmbridge.models.ToolResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * litertlm 0.16.1 신규 기능(sampler/history/thinking/maxOutputTokens/수동·자동 도구 호출/통계) 계측 테스트.
 *
 * 모델 경로는 계측 인자 `modelPath`로 지정한다(기본: /data/local/tmp/lmbridge/gemma-4-E2B-it.litertlm).
 * 백엔드는 인자 `backend`(CPU/GPU/NPU, 기본 GPU).
 */
@RunWith(AndroidJUnit4::class)
class LiteRtFeaturesTest {

    companion object {
        private const val TAG = "LMBridgeIT"
        private lateinit var client: LMBridgeClient

        @JvmStatic
        @BeforeClass
        fun setUpClass(): Unit = runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val modelPath = args.getString("modelPath") ?: "/data/local/tmp/lmbridge/gemma-4-E2B-it.litertlm"
            val backend = LMBridge.Backend.valueOf(args.getString("backend") ?: "GPU")
            LMBridge.logLevel = LMBridge.LogLevel.DEBUG
            client = LMBridgeClient.Builder(ApplicationProvider.getApplicationContext())
                .setModelPath(modelPath)
                .setBackend(backend)
                .setMaxNumTokens(2048)
                .setEnableBenchmark(true)
                .build()
            val start = System.currentTimeMillis()
            client.initialize()
            Log.i(TAG, "initialized $modelPath on $backend in ${System.currentTimeMillis() - start}ms")
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            client.release()
        }
    }

    private fun List<GenerationChunk>.text() =
        filterIsInstance<GenerationChunk.Token>().joinToString("") { it.text }

    private fun List<GenerationChunk>.assertDone(label: String) {
        val err = filterIsInstance<GenerationChunk.Error>().firstOrNull()
        assertTrue("$label: unexpected error ${err?.error}", err == null)
        assertTrue("$label: stream should end with Done", lastOrNull() is GenerationChunk.Done)
    }

    @Test
    fun history_isUsedAsContext(): Unit = runBlocking {
        val chat = client.newChat(
            ChatConfig(
                systemInstruction = "You are concise.",
                sampler = Sampler(topK = 1, topP = 1.0, temperature = 0.0, seed = 7),
                history = listOf(
                    ChatMessage.user("My name is Jiwoo."),
                    ChatMessage.model("Nice to meet you, Jiwoo."),
                ),
            ),
        )
        try {
            val chunks = chat.send("What is my name? Answer with the name only.").toList()
            chunks.assertDone("history")
            Log.i(TAG, "history answer='${chunks.text()}'")
            assertTrue("answer should recall name from history", chunks.text().contains("Jiwoo"))
        } finally {
            chat.close()
        }
    }

    @Test
    fun maxOutputTokens_capsResponse_andStatsReported(): Unit = runBlocking {
        val chat = client.newChat(ChatConfig(maxOutputTokens = 8))
        try {
            val chunks = chat.send("Count from 1 to 100, separated by spaces.").toList()
            chunks.assertDone("maxOutputTokens")
            val stats = chat.stats()
            Log.i(TAG, "maxOutputTokens answer='${chunks.text()}' stats=$stats")
            assertTrue("contextTokens should be > 0", stats.contextTokens > 0)
            assertTrue("benchmark decode tokens should be reported", stats.decodeTokens != null)
            assertTrue("decode tokens ${stats.decodeTokens} should be capped near 8", stats.decodeTokens!! <= 10)
            assertTrue("decode speed should be > 0", (stats.decodeTokensPerSecond ?: 0.0) > 0.0)
        } finally {
            chat.close()
        }
    }

    @Test
    fun thinking_emitsThoughtChannelSeparately(): Unit = runBlocking {
        val chat = client.newChat(
            ChatConfig(thinking = Thinking(enabled = true, tokenBudget = 256), maxOutputTokens = 512),
        )
        try {
            val chunks = chat.send("What is 17 * 23? Give only the number.").toList()
            chunks.assertDone("thinking")
            val thought = chunks.filterIsInstance<GenerationChunk.Thought>().joinToString("") { it.text }
            Log.i(TAG, "thinking thought(${thought.length} chars)='${thought.take(300)}' answer='${chunks.text()}'")
            assertTrue("thought channel should be emitted", thought.isNotEmpty())
            assertTrue("answer should contain 391", chunks.text().contains("391"))
        } finally {
            chat.close()
        }
    }

    private val weatherParams = Tool.builder("get_weather", "Get the current weather for a city.")
        .param("location", type = "string", description = "City name, e.g. Seoul")

    @Test
    fun manualToolCalling_roundTrip(): Unit = runBlocking {
        val chat = client.newChat(
            ChatConfig(
                tools = listOf(weatherParams.build()),
                automaticToolCalling = false,
                sampler = Sampler(topK = 1, topP = 1.0, temperature = 0.0),
            ),
        )
        try {
            val first = chat.send("What's the weather in Busan right now? Use the tool.").toList()
            first.assertDone("manual tool #1")
            val calls = first.filterIsInstance<GenerationChunk.ToolCall>()
            Log.i(TAG, "manual tool calls=$calls text='${first.text()}'")
            assertTrue("model should request a tool call", calls.isNotEmpty())
            assertEquals("get_weather", calls.first().name)

            val second = chat.sendToolResponses(
                calls.map { ToolResponse(it.name, """{"temperature_c": 24, "sky": "sunny"}""") },
            ).toList()
            second.assertDone("manual tool #2")
            Log.i(TAG, "manual tool final answer='${second.text()}'")
            assertTrue("final answer should use tool result", second.text().contains("24"))
        } finally {
            chat.close()
        }
    }

    @Test
    fun automaticToolCalling_invokesExecutor(): Unit = runBlocking {
        val invocations = AtomicInteger()
        val tool = weatherParams.executor { args ->
            invocations.incrementAndGet()
            Log.i(TAG, "executor args=$args")
            """{"temperature_c": 31, "sky": "cloudy"}"""
        }.build()
        val chat = client.newChat(
            ChatConfig(tools = listOf(tool), sampler = Sampler(topK = 1, topP = 1.0, temperature = 0.0)),
        )
        try {
            val chunks = chat.send("What's the weather in Busan right now? Use the tool.").toList()
            chunks.assertDone("auto tool")
            Log.i(TAG, "auto tool invocations=${invocations.get()} answer='${chunks.text()}'")
            assertTrue("executor should be invoked", invocations.get() > 0)
            assertTrue("final answer should use tool result", chunks.text().contains("31"))
        } finally {
            chat.close()
        }
    }
}
