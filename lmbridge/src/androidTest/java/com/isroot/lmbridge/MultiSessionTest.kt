package com.isroot.lmbridge

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isroot.lmbridge.models.Chat
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.Sampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * litertlm 0.18 다중 대화 세션 검증.
 *
 * 0.16 계열은 엔진당 세션 1개("Only one session is supported at a time")였으나 0.18 네이티브에서
 * 해당 문자열이 사라지고 `session list`/`ContextHandler`가 보인다. 실제로 한 엔진에서 대화를
 * 여러 개 열 수 있는지, 서로 오염되지 않는지, 전환 후에도 KV가 재사용되는지, 비용(시간·메모리)을 잰다.
 *
 * 인자: `modelPath`(기본 gemma-4-E2B-it), `backend`(기본 GPU), `concurrent=true`면 동시 생성까지 시험
 * (네이티브 크래시 가능성이 있어 기본 off).
 */
@RunWith(AndroidJUnit4::class)
class MultiSessionTest {

    companion object {
        private const val TAG = "LMBridgeMS"
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
            client.initialize()
            Log.i(TAG, "initialized $modelPath on $backend, pss=${pssMb()}MB")
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            client.release()
        }

        private fun pssMb(): Int {
            val am = ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss / 1024
        }
    }

    private val greedy = Sampler(topK = 1, topP = 1.0, temperature = 0.0, seed = 7)

    private fun newChat() = client.newChat(
        ChatConfig(systemInstruction = "You are a concise assistant.", sampler = greedy, maxOutputTokens = 64),
    )

    /** 한 턴을 보내고 (답, prefill 토큰, 소요 ms)를 남긴다. */
    private suspend fun Chat.turn(label: String, prompt: String): String {
        val start = System.currentTimeMillis()
        val chunks = send(prompt).toList()
        val elapsed = System.currentTimeMillis() - start
        val err = chunks.filterIsInstance<GenerationChunk.Error>().firstOrNull()
        assertTrue("$label: unexpected error ${err?.error}", err == null)
        val text = chunks.filterIsInstance<GenerationChunk.Token>().joinToString("") { it.text }
        val s = stats()
        Log.i(
            TAG,
            "[$label] ${elapsed}ms ttft=${s.timeToFirstTokenSeconds} prefill=${s.prefillTokens} " +
                "ctx=${s.contextTokens} pss=${pssMb()}MB answer='${text.trim().take(120)}'",
        )
        return text
    }

    @Test
    fun twoChats_coexist_isolated_andKeepKv(): Unit = runBlocking {
        val pss0 = pssMb()
        val a = newChat()
        val pssA = pssMb()
        val b = try {
            newChat()
        } catch (e: Throwable) {
            a.close()
            throw AssertionError("second concurrent chat rejected → single-session limit still applies", e)
        }
        val pssB = pssMb()
        Log.i(TAG, "pss: base=$pss0 +A=$pssA +B=$pssB (MB)")
        try {
            a.turn("A1", "My dog's name is Biscuit. Just say OK.")
            a.turn("A2", "I live in Busan. Just say OK.")

            // 큰 단발(사실 추출류) 프롬프트를 다른 대화 B에서 실행한다.
            val extraction = buildString {
                append("Extract every personal fact from the conversation below as JSON lines ")
                append("{\"category\":..., \"key\":..., \"value\":...}. Conversation:\n")
                repeat(8) { append("User: I am planning a trip to Jeju next month and I like hiking and photography.\n") }
            }
            b.turn("B1-extract", extraction)

            // A로 돌아와 회상 + 전환 후 prefill이 새 턴 분량에 그치는지(KV 유지) 확인.
            val recall = a.turn("A3-after-switch", "What is my dog's name? Answer with the name only.")
            val prefillAfterSwitch = a.stats().prefillTokens
            assertTrue("A should recall Biscuit after B ran", recall.contains("Biscuit", ignoreCase = true))
            assertFalse("A must not be steered by B's extraction prompt", recall.contains("{\"category\""))

            // B는 A의 사실을 몰라야 한다(역방향 격리).
            val leak = b.turn("B2-isolation", "What is my dog's name? If you do not know, say UNKNOWN.")
            assertFalse("B must not see A's context", leak.contains("Biscuit", ignoreCase = true))

            // 비교 기준: 전환 없이 A만 연속 턴.
            a.turn("A4-no-switch", "Which city do I live in? Answer with the city only.")
            Log.i(TAG, "prefillAfterSwitch=$prefillAfterSwitch (small ≈ KV kept; ~whole history ≈ re-prefilled)")
        } finally {
            b.close()
            a.close()
        }
        Log.i(TAG, "pss after close=${pssMb()}MB")
    }

    @Test
    fun generateText_sideTask_leavesOpenChatIntact(): Unit = runBlocking {
        val a = newChat()
        try {
            a.turn("S-A1", "My cat's name is Nabi. Just say OK.")
            val ctxBefore = a.stats().contextTokens

            val start = System.currentTimeMillis()
            val facts = client.generateText(
                "Extract the pet name from this sentence as JSON {\"pet\": ...}: My cat's name is Nabi.",
                ChatConfig(systemInstruction = "You extract facts. Output JSON only.", sampler = greedy, maxOutputTokens = 32),
            )
            Log.i(TAG, "[S-side] ${System.currentTimeMillis() - start}ms facts='${facts.trim()}'")
            assertTrue("side task should produce output", facts.contains("Nabi", ignoreCase = true))
            assertTrue("side task must not grow the open chat's context", a.stats().contextTokens == ctxBefore)

            val recall = a.turn("S-A2", "What is my cat's name? Answer with the name only.")
            assertTrue("open chat should still recall Nabi", recall.contains("Nabi", ignoreCase = true))
            assertFalse("open chat must not answer in the side task's JSON format", recall.contains("{"))
        } finally {
            a.close()
        }
    }

    @Test
    fun timeToFirstToken_isMeasuredPerTurn(): Unit = runBlocking {
        val a = newChat()
        try {
            a.turn("T1", "Say hello.")
            val first = a.stats().timeToFirstTokenSeconds
            // 두 번째 턴은 긴 입력으로 prefill을 늘려 TTFT가 달라지게 한다.
            a.turn("T2", "Summarize in one word: " + "The quick brown fox jumps over the lazy dog. ".repeat(20))
            val second = a.stats().timeToFirstTokenSeconds
            assertTrue("TTFT should be reported", first != null && second != null)
            assertTrue("TTFT should be per turn, not frozen at the first turn ($first vs $second)", first != second)
        } finally {
            a.close()
        }
    }

    @Test
    fun manyChats_openAndClose(): Unit = runBlocking {
        val chats = mutableListOf<Chat>()
        try {
            repeat(4) { i ->
                chats += newChat()
                Log.i(TAG, "opened chat #${i + 1}, pss=${pssMb()}MB")
            }
            chats.forEachIndexed { i, c -> c.turn("C$i", "Reply with the number ${i + 1} only.") }
        } finally {
            chats.forEach { runCatching { it.close() } }
        }
    }

    @Test
    fun concurrentGeneration_inTwoChats(): Unit = runBlocking {
        assumeTrue(
            "set -e concurrent true to run (may crash natively)",
            InstrumentationRegistry.getArguments().getString("concurrent") == "true",
        )
        val a = newChat()
        val b = newChat()
        try {
            val start = System.currentTimeMillis()
            val answers = listOf(
                async { a.turn("P-A", "Name three fruits.") },
                async { b.turn("P-B", "Name three colors.") },
            ).awaitAll()
            Log.i(TAG, "concurrent both done in ${System.currentTimeMillis() - start}ms")
            assertTrue(answers.all { it.isNotBlank() })
        } finally {
            b.close()
            a.close()
        }
    }
}
