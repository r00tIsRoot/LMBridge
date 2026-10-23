package com.isroot.lmbridge

import android.app.ActivityManager
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
import com.isroot.lmbridge.models.InputLimits
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
import java.util.concurrent.ConcurrentHashMap

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
    private val maxNumTokens: Int?,
    private val enableSpeculativeDecoding: Boolean,
    private val maxNumImages: Int?,
    private val visionBackend: LMBridge.Backend?,
    private val audioBackend: LMBridge.Backend?,
    private val maxInputBytesOverride: Long?,
    private val enableBenchmark: Boolean,
    private val cpuThreadCount: Int?,
) {
    private enum class State { IDLE, READY, RELEASED }

    private val engine: InferenceEngine = LiteRtEngineAdapter()
    private val store = ModelStore(context)
    private val initMutex = Mutex()

    @Volatile
    private var state: State = State.IDLE

    /** 진행 중인 단발 생성 세션들. 여러 단발 생성이 동시에 돌 수 있어 집합으로 추적한다. */
    private val oneShotChats: MutableSet<EngineSessionChat> = ConcurrentHashMap.newKeySet()

    /** 모델 다운로드 관리자. */
    val models: ModelDownloadManager = ModelDownloadManager(context, store)

    /**
     * 엔진을 초기화한다(off-main, 멱등).
     * 이미 초기화된 경우 아무 것도 하지 않는다.
     *
     * @throws LMBridgeError.Released release() 이후 호출 시
     * @throws LMBridgeError.ModelNotFound 모델을 찾을 수 없을 때
     * @throws LMBridgeError.InvalidModel 모델 파일이 손상되었거나 읽을 수 없을 때(백엔드를 바꿔도 실패)
     * @throws LMBridgeError.BackendUnavailable 요청한 백엔드로 엔진을 만들 수 없을 때. 다른 백엔드로
     *   새 클라이언트를 만들어 재시도할 수 있다(예: GPU 실패 → CPU).
     * @throws LMBridgeError.OutOfMemory 엔진 생성 중 메모리가 부족할 때
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
                maxNumImages = maxNumImages,
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                maxInputBytes = resolveMaxInputBytes(),
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                enableBenchmark = enableBenchmark,
                cpuThreadCount = cpuThreadCount,
            ),
        )
        state = State.READY
    }

    /**
     * 인메모리 입력(오디오/이미지 바이트·문서) 1건당 최대 바이트를 확정한다.
     *
     * 명시적 오버라이드([Builder.setMaxInputBytes])가 있으면 그 값을, 없으면 초기화
     * 시점의 기기 총 RAM으로 [InputLimits.budgetForDevice]가 산출한 값을 쓴다. 큰 미디어
     * 바이트가 저사양 기기에서 OOM/네이티브 크래시로 이어지기 전에 [LMBridgeError.InvalidInput]
     * 으로 막기 위함이다.
     */
    private fun resolveMaxInputBytes(): Long {
        maxInputBytesOverride?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return InputLimits.budgetForDevice(memInfo.totalMem, am.isLowRamDevice)
    }

    /** 텍스트 프롬프트로 단발 생성(상태 비유지). */
    fun generate(prompt: String, config: ChatConfig = ChatConfig()): Flow<GenerationChunk> =
        generate(MultimodalInput.text(prompt), config)

    /**
     * 멀티모달 입력으로 단발 생성(상태 비유지).
     *
     * 매 호출마다 **독립된 임시 세션**을 열고 끝나면 닫는다. 열려 있는 [Chat]들의 문맥(KV 캐시)은
     * 건드리지 않으므로, 대화 도중 사실 추출·요약·판단 같은 부수 작업을 대화에 끼워 넣지 말고
     * 이 메서드로 실행한다(같은 대화에 큰 지시를 섞으면 이후 응답이 오염된다).
     * 엔진은 생성을 직렬로 처리하므로, 진행 중인 대화 응답이 있으면 끝난 뒤 실행된다.
     *
     * @param config 임시 세션 설정(시스템 지시·샘플러·출력 상한 등). [ChatConfig.history]로 퓨샷 예시를 줄 수 있다.
     */
    fun generate(input: MultimodalInput, config: ChatConfig = ChatConfig()): Flow<GenerationChunk> = flow {
        ensureReady()
        val chat = EngineSessionChat(engine.newSession(config))
        oneShotChats += chat
        try {
            emitAll(chat.send(input))
        } finally {
            oneShotChats -= chat
            chat.close()
        }
    }

    /**
     * [generate]의 결과를 답변 텍스트로 모아 반환한다(추론·도구 호출 청크는 제외).
     *
     * @throws LMBridgeError 생성 중 오류가 나면 그 오류
     */
    suspend fun generateText(prompt: String, config: ChatConfig = ChatConfig()): String =
        generateText(MultimodalInput.text(prompt), config)

    /** 멀티모달 입력 버전의 [generateText]. */
    suspend fun generateText(input: MultimodalInput, config: ChatConfig = ChatConfig()): String =
        buildString {
            generate(input, config).collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Token -> append(chunk.text)
                    is GenerationChunk.Error -> throw chunk.error
                    else -> Unit
                }
            }
        }

    /** 상태를 유지하는 새 대화 세션을 만든다. 사용 후 [Chat.close]로 해제한다. */
    fun newChat(config: ChatConfig = ChatConfig()): Chat {
        ensureReady()
        return EngineSessionChat(engine.newSession(config))
    }

    /** [generate]로 진행 중인 단발 생성을 모두 취소한다(대화 세션은 각 [Chat.stop] 사용). */
    fun stop() {
        oneShotChats.forEach { it.stop() }
    }

    /** 엔진과 모든 자원을 해제한다. 이후 재사용할 수 없다. */
    fun release() {
        oneShotChats.forEach { it.close() }
        oneShotChats.clear()
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

    @Deprecated("Use generate(MultimodalInput.Builder().audioBytes(...).text(...).build()) instead.")
    @Suppress("DEPRECATION")
    fun generateWithAudio(prompt: String, audioBytes: ByteArray): Flow<GenerationResult> =
        generate(
            MultimodalInput.Builder().audioBytes(audioBytes).text(prompt).build(),
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
        private var maxNumTokens: Int? = null
        private var enableSpeculativeDecoding: Boolean = false
        private var maxNumImages: Int? = null
        private var visionBackend: LMBridge.Backend? = null
        private var audioBackend: LMBridge.Backend? = null
        private var maxInputBytesOverride: Long? = null
        private var enableBenchmark: Boolean = false
        private var cpuThreadCount: Int? = null

        /** 로컬 모델 파일 경로를 직접 지정. */
        fun setModelPath(path: String): Builder = apply { modelPath = path }

        /** 카탈로그(또는 커스텀) 모델을 지정. initialize()가 로컬 존재를 확인하여 로드. */
        fun setModel(info: ModelDownloadManager.ModelInfo): Builder = apply { modelInfo = info }

        fun setBackend(backend: LMBridge.Backend): Builder = apply { this.backend = backend }

        /**
         * 총 컨텍스트(입력+출력 합산) 토큰 상한 = KV 캐시 길이.
         *
         * **설정하지 않으면(기본 null) 각 모델 번들에 내장된 최대값(파일명 `ekvNNNN`,
         * 예: ekv4096)을 그대로 사용한다.** 값을 지정하면 그 값으로 상한을 낮춰 KV 캐시
         * 메모리를 줄일 수 있다(단, 큰 문서/오디오는 잘림). 양수만 유효하다.
         *
         * 주의: 상한을 키우면 KV 캐시가 커져 메모리 사용량이 늘어난다. 저사양 기기에서
         * 큰 모델 + 큰 컨텍스트는 OOM될 수 있으니, 필요 시 이 값으로 낮춰 조절한다.
         */
        fun setMaxNumTokens(maxNumTokens: Int): Builder = apply { this.maxNumTokens = maxNumTokens }

        /**
         * MTP(Multi-Token Prediction, speculative decoding) 사용 여부. 기본값 false.
         *
         * 출력 품질 저하 없이 decode 속도를 높인다(Gemma 4 기준 모바일 GPU 최대 2.2배,
         * CPU 최대 1.5배). Google은 GPU 백엔드에서 사용을 권장한다.
         * 단, MTP 드래프터를 포함한 모델(예: Gemma 4 E2B/E4B)에서만 켜야 한다. 일반
         * litert-community `.litertlm`에서 켜면 초기화가 `embedding_lookup == nullptr`로 실패한다.
         */
        fun setEnableSpeculativeDecoding(enabled: Boolean): Builder =
            apply { this.enableSpeculativeDecoding = enabled }

        /**
         * 비전(이미지) 입력 활성화 — 엔진이 미리 확보할 이미지 슬롯 수.
         *
         * **멀티모달(비전) 모델에 이미지를 넣으려면 반드시 1 이상으로 설정해야 한다.**
         * 설정하지 않으면(기본 null) 엔진이 `max_num_images: 0`으로 초기화되어, 이미지를
         * 주입하는 순간 네이티브에서 크래시한다. 텍스트 전용 모델에서는 설정하지 않는다
         * (불필요한 비전 인코더 할당을 피함).
         */
        fun setMaxNumImages(maxNumImages: Int): Builder =
            apply { this.maxNumImages = maxNumImages }

        /**
         * 비전 인코더 백엔드를 메인 백엔드와 다르게 지정(선택).
         *
         * 기본(null)은 [setMaxNumImages]>0일 때 메인 [setBackend]를 따른다. 일부 GPU는
         * 이미지 샘플러(OpenCL image sampler)를 지원하지 않아 GPU 비전이 실패할 수 있는데,
         * 이때 비전만 [LMBridge.Backend.CPU]로 분리해 안정적으로 돌릴 수 있다.
         */
        fun setVisionBackend(backend: LMBridge.Backend): Builder =
            apply { this.visionBackend = backend }

        /**
         * 오디오 입력 활성화 — 오디오 인코더 백엔드 지정.
         *
         * 설정하지 않으면(기본 null) 오디오가 비활성이다. 오디오를 주입하려면
         * (예: [MultimodalInput.Builder.audioBytes]) 이 값을 지정해야 한다.
         */
        fun setAudioBackend(backend: LMBridge.Backend): Builder =
            apply { this.audioBackend = backend }

        /**
         * 인메모리 입력(오디오/이미지 바이트·문서 텍스트) 1건당 최대 바이트를 직접 지정.
         *
         * 설정하지 않으면(기본 null) 초기화 시점의 기기 총 RAM으로 자동 산출한다
         * (≤4GB→4MB, ≤6GB→16MB, >6GB→64MB, 저사양 기기는 한 단계 낮춤). 초과 입력은
         * [LMBridgeError.InvalidInput]으로 표면화되어 OOM/네이티브 크래시를 예방한다.
         * 대용량 미디어는 파일 경로 소스([MultimodalInput.Builder.imageFile]/
         * [MultimodalInput.Builder.audioFile])를 쓰면 이 제한을 우회한다(힙 미적재).
         */
        fun setMaxInputBytes(bytes: Long): Builder =
            apply { this.maxInputBytesOverride = bytes }

        /**
         * 처리 속도 통계 수집 여부. 기본값 false.
         *
         * 켜면 [com.isroot.lmbridge.models.Chat.stats]가 prefill/decode 토큰 수와 tokens/s를
         * 함께 반환한다. 성능 측정·튜닝 용도. (첫 토큰 지연(TTFT)은 이 설정과 무관하게 항상 실측된다.)
         */
        fun setEnableBenchmark(enabled: Boolean): Builder =
            apply { this.enableBenchmark = enabled }

        /**
         * CPU 백엔드 추론 스레드 수. 설정하지 않으면(기본 null) litertlm 기본값을 쓴다.
         *
         * 메인 백엔드가 CPU일 때뿐 아니라 CPU로 지정한 비전·오디오 인코더에도 적용된다.
         * 기본값은 기기 코어 수보다 적게 잡힐 수 있다. SM-A426N(8코어, Gemma 4 E2B CPU)에서는 기본값 대비
         * 4~6 스레드가 prefill 약 1.6배·첫 토큰 지연 약 절반이었고, 8 스레드는 더 빨라지지 않았다.
         * 최적값은 기기마다 다르므로 대상 기기에서 [setEnableBenchmark]로 측정해 정한다.
         */
        fun setCpuThreadCount(threadCount: Int): Builder = apply {
            require(threadCount > 0) { "threadCount must be positive: $threadCount" }
            this.cpuThreadCount = threadCount
        }

        fun build(): LMBridgeClient =
            LMBridgeClient(
                context,
                modelPath,
                modelInfo,
                backend,
                maxNumTokens,
                enableSpeculativeDecoding,
                maxNumImages,
                visionBackend,
                audioBackend,
                maxInputBytesOverride,
                enableBenchmark,
                cpuThreadCount,
            )
    }

    companion object {
        private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"
    }
}
