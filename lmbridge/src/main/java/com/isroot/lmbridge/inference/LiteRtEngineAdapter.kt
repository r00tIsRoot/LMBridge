package com.isroot.lmbridge.inference

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
import com.isroot.lmbridge.models.DocumentReader
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.ImageEncoder
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

    /** 인메모리 입력 1건당 최대 바이트(초기화 시 [EngineInit]에서 확정). */
    @Volatile
    private var maxInputBytes: Long = Long.MAX_VALUE

    override val isInitialized: Boolean
        get() = engine?.isInitialized() == true

    override suspend fun initialize(init: EngineInit) = withContext(dispatcher) {
        Logger.d(
            TAG,
            "Initializing engine (backend=${init.backend}, maxTokens=${init.maxTokens}, " +
                "maxNumImages=${init.maxNumImages}, visionBackend=${init.visionBackend}, " +
                "audioBackend=${init.audioBackend}, maxInputBytes=${init.maxInputBytes})",
        )
        maxInputBytes = init.maxInputBytes

        // MTP(speculative decoding) 활성화.
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableSpeculativeDecoding = init.enableSpeculativeDecoding

        // 비전 활성 시(maxNumImages>0) 비전 백엔드가 지정되지 않았으면 메인 백엔드를 따른다.
        // 지정되지 않고 비전도 비활성이면 null로 두어 native 기본(비전 미할당)을 유지한다.
        val visionEnabled = (init.maxNumImages ?: 0) > 0
        val visionBackend = when {
            init.visionBackend != null -> toLiteRtBackend(init.visionBackend)
            visionEnabled -> toLiteRtBackend(init.backend)
            else -> null
        }

        val config = EngineConfig(
            modelPath = init.modelPath,
            backend = toLiteRtBackend(init.backend),
            visionBackend = visionBackend,
            audioBackend = init.audioBackend?.let { toLiteRtBackend(it) },
            maxNumTokens = init.maxTokens,
            maxNumImages = init.maxNumImages,
            cacheDir = init.cacheDir,
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
        return LiteRtSession(conversation, dispatcher, maxInputBytes)
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
    private val maxInputBytes: Long,
) : EngineSession {

    private companion object {
        const val TAG = "LiteRtSession"
    }

    override fun send(parts: List<MultimodalContent>): Flow<GenerationChunk> = callbackFlow {
        // 입력 변환(파일 IO/인코딩)은 실패할 수 있다. 조용히 삼키지 않고
        // GenerationChunk.Error로 표면화한 뒤 스트림을 닫는다.
        val contents = try {
            Contents.of(parts.toLiteRtContents(maxInputBytes))
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to convert multimodal input", e)
            trySend(GenerationChunk.Error(LMBridgeError.from(e)))
            close()
            null
        }

        if (contents != null) {
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
        }
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

private fun List<MultimodalContent>.toLiteRtContents(maxInputBytes: Long): List<Content> = map { part ->
    when (part) {
        is MultimodalContent.Text -> Content.Text(part.text)
        is MultimodalContent.Image -> {
            val encoded = ImageEncoder.encode(part.bitmap, part.encoding)
            requireWithinBudget(encoded.size.toLong(), maxInputBytes, "Image")
            Content.ImageBytes(encoded)
        }
        is MultimodalContent.ImageBytes -> {
            requireWithinBudget(part.bytes.size.toLong(), maxInputBytes, "Image")
            Content.ImageBytes(part.bytes)
        }
        is MultimodalContent.ImageFile -> Content.ImageFile(requireReadableFile(part.path, "Image"))
        is MultimodalContent.AudioBytes -> {
            requireWithinBudget(part.bytes.size.toLong(), maxInputBytes, "Audio")
            Content.AudioBytes(part.bytes)
        }
        is MultimodalContent.AudioFile -> Content.AudioFile(requireReadableFile(part.path, "Audio"))
        is MultimodalContent.Document ->
            Content.Text(buildDocumentPrompt(DocumentReader.read(part.path, part.charset, maxInputBytes)))
    }
}

/**
 * 인메모리 입력(오디오/이미지 바이트) 1건의 크기가 기기-적응형 예산을 넘으면
 * [LMBridgeError.InvalidInput]으로 표면화한다. 파일 경로 소스는 힙에 적재되지 않으므로
 * 이 검사를 거치지 않는다.
 */
private fun requireWithinBudget(size: Long, maxBytes: Long, label: String) {
    if (size > maxBytes) {
        throw LMBridgeError.InvalidInput(
            "$label too large: $size bytes (max $maxBytes). " +
                "Use a file source (imageFile/audioFile) or raise the limit via " +
                "LMBridgeClient.Builder.setMaxInputBytes().",
        )
    }
}

private fun buildDocumentPrompt(body: String): String =
    "Based on the following document, answer the user's request.\n\nDocument content:\n$body"

/** 파일 소스(이미지/오디오)의 존재·가독성을 검증하고 절대경로를 돌려준다. */
private fun requireReadableFile(path: String, label: String): String {
    val file = File(path)
    if (!file.exists() || !file.isFile || !file.canRead()) {
        throw LMBridgeError.InvalidInput("$label file not found or unreadable: $path")
    }
    return file.absolutePath
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
