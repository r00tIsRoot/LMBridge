package com.isroot.lmbridge.inference

import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend as LiteRtBackend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.isroot.lmbridge.LMBridge
import com.isroot.lmbridge.Logger
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.models.MultimodalContent
import com.isroot.lmbridge.models.Tool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * litertlm(`com.google.ai.edge.litertlm`) 구현 어댑터.
 *
 * **이 파일이 프로젝트에서 litertlm 타입을 import하는 유일한 곳이다**(어댑터 경계).
 * 모든 엔진/세션 호출은 단일 [dispatcher]에서 직렬화되어 스레드 안전을 보장한다.
 */
internal class LiteRtEngineAdapter(
    private val dispatcher: CoroutineDispatcher = defaultEngineDispatcher(),
) : InferenceEngine {

    private companion object {
        const val TAG = "LiteRtEngineAdapter"
    }

    @Volatile
    private var engine: Engine? = null

    override val isInitialized: Boolean
        get() = engine?.isInitialized() == true

    override suspend fun initialize(init: EngineInit) = withContext(dispatcher) {
        Logger.d(TAG, "Initializing engine (backend=${init.backend}, maxTokens=${init.maxTokens})")

        // MTP(speculative decoding) 활성화.
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableSpeculativeDecoding = init.enableSpeculativeDecoding

        val config = EngineConfig(
            modelPath = init.modelPath,
            backend = toLiteRtBackend(init.backend),
            maxNumTokens = init.maxTokens,
        )
        try {
            engine = Engine(config).apply { initialize() }
            Logger.d(TAG, "Engine initialized")
        } catch (e: OutOfMemoryError) {
            throw LMBridgeError.OutOfMemory(e)
        } catch (e: Throwable) {
            throw LMBridgeError.BackendUnavailable(init.backend, e)
        }
    }

    override fun newSession(config: ChatConfig): EngineSession {
        val eng = engine ?: throw LMBridgeError.NotInitialized()
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(config.systemInstruction),
            tools = config.tools.map { it.toToolProvider() },
        )
        val conversation = eng.createConversation(conversationConfig)
        return LiteRtSession(conversation, dispatcher)
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun toLiteRtBackend(backend: LMBridge.Backend): LiteRtBackend = when (backend) {
        LMBridge.Backend.CPU -> LiteRtBackend.CPU()
        LMBridge.Backend.GPU -> LiteRtBackend.GPU()
        LMBridge.Backend.NPU -> LiteRtBackend.NPU()
    }
}

/**
 * litertlm `Conversation` 래핑 세션.
 * 자신의 [conversation] 참조를 보관하므로 [cancel]이 실제로 동작한다(과거 no-op 버그 해결).
 */
private class LiteRtSession(
    private val conversation: Conversation,
    private val dispatcher: CoroutineDispatcher,
) : EngineSession {

    private companion object {
        const val TAG = "LiteRtSession"
    }

    override fun send(parts: List<MultimodalContent>): Flow<GenerationChunk> = callbackFlow {
        val contents = Contents.of(parts.toLiteRtContents())

        conversation.sendMessageAsync(
            contents,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    // 정식 텍스트 추출 (message.toString() 사용 금지 — 디버그 문자열 오염 방지)
                    val text = message.extractText()
                    if (text.isNotEmpty()) trySend(GenerationChunk.Token(text))
                    // 도구 호출을 구조화하여 노출
                    message.toolCalls.forEach { call ->
                        trySend(GenerationChunk.ToolCall(call.name, call.arguments.toJsonString()))
                    }
                }

                override fun onDone() {
                    trySend(GenerationChunk.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Logger.e(TAG, "Generation error", throwable)
                    trySend(GenerationChunk.Error(LMBridgeError.from(throwable)))
                    close()
                }
            },
        )
        awaitClose { runCatchingCancel() }
    }.flowOn(dispatcher)

    override fun cancel() = runCatchingCancel()

    override fun close() {
        runCatchingCancel()
        try {
            conversation.close()
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to close conversation: ${e.message}")
        }
    }

    private fun runCatchingCancel() {
        try {
            conversation.cancelProcess()
        } catch (e: Throwable) {
            Logger.w(TAG, "cancelProcess failed: ${e.message}")
        }
    }
}

// ---- litertlm 변환 헬퍼 (어댑터 경계 내부) ----

private fun Message.extractText(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

private fun List<MultimodalContent>.toLiteRtContents(): List<Content> = flatMap { part ->
    when (part) {
        is MultimodalContent.Text -> listOf(Content.Text(part.text))
        is MultimodalContent.Image -> listOf(Content.ImageBytes(part.bitmap.toPngBytes()))
        is MultimodalContent.Audio -> listOf(Content.AudioBytes(part.bytes))
        is MultimodalContent.Document -> {
            val body = readDocument(part.path)
            if (body != null) {
                listOf(
                    Content.Text(
                        "Based on the following document, answer the user's request.\n\n" +
                            "Document content:\n$body",
                    ),
                )
            } else {
                emptyList()
            }
        }
    }
}

private fun readDocument(path: String): String? = try {
    val file = File(path)
    if (file.exists()) file.readText() else null
} catch (e: Exception) {
    null
}

private fun Bitmap.toPngBytes(): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

private fun Map<String, Any?>.toJsonString(): String {
    val obj = JSONObject()
    forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
    return obj.toString()
}

/**
 * LMBridge [Tool] → litertlm [ToolProvider] 변환.
 *
 * OpenAI 함수-호출 스키마 형식의 JSON을 생성하여 [OpenApiTool]로 감싼다.
 * [Tool.executor]가 없으면 실행은 no-op("{}")이며, 모델의 호출은
 * [GenerationChunk.ToolCall]로 소비 앱에 전달된다.
 */
private fun Tool.toToolProvider(): ToolProvider {
    val declaration = this
    val schema = JSONObject().apply {
        put("name", declaration.name)
        put("description", declaration.description)
        put(
            "parameters",
            JSONObject().apply {
                put("type", "object")
                val properties = JSONObject()
                val required = JSONArray()
                declaration.params.forEach { p ->
                    properties.put(
                        p.name,
                        JSONObject().apply {
                            put("type", p.type)
                            if (p.description.isNotEmpty()) put("description", p.description)
                        },
                    )
                    if (p.required) required.put(p.name)
                }
                put("properties", properties)
                put("required", required)
            },
        )
    }.toString()

    val openApiTool = object : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = schema

        override fun execute(argumentsJson: String): String {
            val handler = declaration.executor ?: return "{}"
            return try {
                handler(parseArgs(argumentsJson))
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "tool execution failed").toString()
            }
        }
    }
    return tool(openApiTool)
}

/** 엔진 호출을 직렬화하는 단일 스레드 디스패처. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private fun defaultEngineDispatcher(): CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(1)

private fun parseArgs(json: String): Map<String, Any?> = try {
    val obj = JSONObject(json)
    obj.keys().asSequence().associateWith { key ->
        val v = obj.get(key)
        if (v == JSONObject.NULL) null else v
    }
} catch (e: Exception) {
    emptyMap()
}
