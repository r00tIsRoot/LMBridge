package com.isroot.lmbridge

import android.content.Context
import android.graphics.Bitmap
import com.isroot.lmbridge.download.ModelDownloadManager
import com.isroot.lmbridge.download.ModelStore
import com.isroot.lmbridge.inference.EngineInit
import com.isroot.lmbridge.inference.GenerationResult
import com.isroot.lmbridge.inference.EngineSessionChat
import com.isroot.lmbridge.inference.InferenceEngine
import com.isroot.lmbridge.inference.LiteRtEngineAdapter
import com.isroot.lmbridge.inference.asLegacyResults
import com.isroot.lmbridge.models.Chat
import com.isroot.lmbridge.models.ChatConfig
import com.isroot.lmbridge.models.GenerationChunk
import com.isroot.lmbridge.models.LMBridgeError
import com.isroot.lmbridge.models.MultimodalContent
import com.isroot.lmbridge.models.MultimodalInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/**
 * LMBridge 진입점(Facade).
 *
 * 생명주기: `build() → initialize() → generate()/newChat() → release()`.
 * 모든 공개 시그니처는 litertlm 타입을 노출하지 않는다(어댑터 경계).
 */
class LMBridgeClient private constructor(
    private val context: Context,
    private val modelPath: String?,
    private val modelInfo: ModelDownloadManager.ModelInfo?,
    private val backend: LMBridge.Backend,
    private val maxNumTokens: Int,
    private val enableSpeculativeDecoding: Boolean,
) {
    private enum class State { IDLE, READY, RELEASED }

    private val engine: InferenceEngine = LiteRtEngineAdapter()
    private val store = ModelStore(context)
    private val initMutex = Mutex()

    @Volatile
    private var state: State = State.IDLE

    @Volatile
    private var activeChat: EngineSessionChat? = null

    /** 모델 다운로드 관리자. */
    val models: ModelDownloadManager = ModelDownloadManager(context, store)

    /**
     * 엔진을 초기화한다(off-main, 멱등).
     * 이미 초기화된 경우 아무 것도 하지 않는다.
     *
     * @throws LMBridgeError.Released release() 이후 호출 시
     * @throws LMBridgeError.ModelNotFound 모델을 찾을 수 없을 때
     */
    suspend fun initialize() = initMutex.withLock {
        when (state) {
            State.READY -> return@withLock
            State.RELEASED -> throw LMBridgeError.Released()
            State.IDLE -> Unit
        }
        val resolvedPath = resolveModelPath()
        engine.initialize(
            EngineInit(
                modelPath = resolvedPath,
                backend = backend,
                maxTokens = maxNumTokens,
                cacheDir = context.cacheDir.absolutePath,
                enableSpeculativeDecoding = enableSpeculativeDecoding,
            ),
        )
        state = State.READY
    }

    /** 텍스트 프롬프트로 단발 생성(상태 비유지). */
    fun generate(prompt: String): Flow<GenerationChunk> =
        generate(MultimodalInput.text(prompt))

    /** 멀티모달 입력으로 단발 생성(상태 비유지). */
    fun generate(input: MultimodalInput): Flow<GenerationChunk> = flow {
        ensureReady()
        val chat = EngineSessionChat(engine.newSession(ChatConfig()))
        activeChat = chat
        try {
            emitAll(chat.send(input))
        } finally {
            if (activeChat === chat) activeChat = null
            chat.close()
        }
    }

    /** 상태를 유지하는 새 대화 세션을 만든다. 사용 후 [Chat.close]로 해제한다. */
    fun newChat(config: ChatConfig = ChatConfig()): Chat {
        ensureReady()
        return EngineSessionChat(engine.newSession(config))
    }

    /** [generate]로 진행 중인 단발 생성을 취소한다(대화 세션은 각 [Chat.stop] 사용). */
    fun stop() {
        activeChat?.stop()
    }

    /** 엔진과 모든 자원을 해제한다. 이후 재사용할 수 없다. */
    fun release() {
        activeChat?.close()
        activeChat = null
        engine.close()
        state = State.RELEASED
    }

    private fun ensureReady() {
        when (state) {
            State.READY -> Unit
            State.RELEASED -> throw LMBridgeError.Released()
            State.IDLE -> throw LMBridgeError.NotInitialized()
        }
    }

    /**
     * 모델 경로 확정 순서:
     * 1) 명시적 [modelPath]가 존재하면 사용
     * 2) [modelInfo]가 로컬에 존재하면 그 경로
     * 3) 번들 에셋([DEFAULT_MODEL_FILE])을 추출
     */
    private fun resolveModelPath(): String {
        if (!modelPath.isNullOrEmpty() && File(modelPath).exists()) return modelPath

        modelInfo?.let { info ->
            store.pathIfPresent(info)?.let { return it }
            throw LMBridgeError.ModelNotFound(
                "Model ${info.modelId} is not downloaded. Use client.models.downloadModel(...) first.",
            )
        }

        return extractAssetIfNeeded(DEFAULT_MODEL_FILE)
    }

    private fun extractAssetIfNeeded(assetFileName: String): String {
        val outFile = File(context.filesDir, assetFileName)
        if (!outFile.exists()) {
            try {
                context.assets.open(assetFileName).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw LMBridgeError.ModelNotFound(assetFileName)
            }
        }
        return outFile.absolutePath
    }

    // ---------------------------------------------------------------------
    // 하위호환 shim (@Deprecated) — 다음 메이저에서 제거 예정
    // ---------------------------------------------------------------------

    @Deprecated("Use generate(MultimodalInput.textAndImages(...)) instead.")
    @Suppress("DEPRECATION")
    fun generateWithImages(prompt: String, images: List<Bitmap>): Flow<GenerationResult> =
        generate(MultimodalInput.textAndImages(prompt, images)).asLegacyResults()

    @Deprecated("Use generate(MultimodalInput.Builder().audio(...).text(...).build()) instead.")
    @Suppress("DEPRECATION")
    fun generateWithAudio(prompt: String, audioBytes: ByteArray): Flow<GenerationResult> =
        generate(
            MultimodalInput.Builder().audio(audioBytes).text(prompt).build(),
        ).asLegacyResults()

    @Deprecated("Use generate(MultimodalInput.Builder().document(...).text(...).build()) instead.")
    @Suppress("DEPRECATION")
    fun generateWithFiles(prompt: String, filePaths: List<String>): Flow<GenerationResult> {
        val builder = MultimodalInput.Builder()
        filePaths.forEach { builder.document(it) }
        builder.text(prompt)
        return generate(builder.build()).asLegacyResults()
    }

    @Deprecated("Use generate(input) which now returns Flow<GenerationChunk>.")
    @Suppress("DEPRECATION")
    fun generateWithInput(input: MultimodalInput): Flow<GenerationResult> =
        generate(input).asLegacyResults()

    @Deprecated("Use stop() instead.", ReplaceWith("stop()"))
    fun stopGeneration() = stop()

    @Deprecated("Use release() instead.", ReplaceWith("release()"))
    fun close() = release()

    @Deprecated("Use the models property instead.", ReplaceWith("models"))
    fun getDownloadManager(): ModelDownloadManager = models

    class Builder(private val context: Context) {
        private var modelPath: String? = null
        private var modelInfo: ModelDownloadManager.ModelInfo? = null
        private var backend: LMBridge.Backend = LMBridge.Backend.CPU
        private var maxNumTokens: Int = 1024
        private var enableSpeculativeDecoding: Boolean = false

        /** 로컬 모델 파일 경로를 직접 지정. */
        fun setModelPath(path: String): Builder = apply { modelPath = path }

        /** 카탈로그(또는 커스텀) 모델을 지정. initialize()가 로컬 존재를 확인하여 로드. */
        fun setModel(info: ModelDownloadManager.ModelInfo): Builder = apply { modelInfo = info }

        fun setBackend(backend: LMBridge.Backend): Builder = apply { this.backend = backend }

        fun setMaxNumTokens(maxNumTokens: Int): Builder = apply { this.maxNumTokens = maxNumTokens }

        /**
         * MTP(speculative decoding) 사용 여부. 기본값 false.
         *
         * 임베더 lookup 테이블을 포함한 전용 모델에서만 켜야 한다. 일반 litert-community
         * `.litertlm`에서 켜면 초기화가 `embedding_lookup == nullptr`로 실패한다.
         */
        fun setEnableSpeculativeDecoding(enabled: Boolean): Builder =
            apply { this.enableSpeculativeDecoding = enabled }

        fun build(): LMBridgeClient =
            LMBridgeClient(context, modelPath, modelInfo, backend, maxNumTokens, enableSpeculativeDecoding)
    }

    companion object {
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
    }
}
