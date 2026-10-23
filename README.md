# LMBridge

Google LiteRT-LM 기반 **온디바이스 LLM 추론** Android SDK

![version](https://img.shields.io/badge/version-0.2.2-blue)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![license](https://img.shields.io/badge/license-Apache--2.0-lightgrey)

## 개요

LMBridge는 Google의 [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) 엔진을 감싸
**온디바이스 LLM 추론**을 제공하는 Android 라이브러리입니다. 네트워크 없이 기기에서 직접
텍스트 생성, 멀티모달(이미지·오디오·문서 + 텍스트) 추론, 도구 호출(function calling)을 수행합니다.

- **litertlm 타입 비노출** — 공개 API는 LMBridge 자체 타입만 노출하는 어댑터 경계로 설계되어,
  엔진 교체·업그레이드가 앱 코드에 새지 않습니다.
- **스트리밍 우선** — 모든 생성은 `Flow<GenerationChunk>`로 토큰 단위 스트리밍됩니다.
- **모델 관리 내장** — HuggingFace에서 `.litertlm` 모델을 이어받기·SHA-256 검증과 함께 내려받는
  다운로드 API와, litert-community 텍스트 모델 **40종**을 담은 [ModelCatalog]를 제공합니다.
- **상태 유지 대화** — 히스토리를 유지하는 [Chat] 세션과 시스템 프롬프트·도구 설정을 지원합니다.

> ⚠️ 엔진이 로드하는 모델은 **`.litertlm` 번들 전용**입니다. MediaPipe `.task` 파일은 로드할 수 없습니다.

## 샘플 앱

동작하는 전체 예제는 별도 저장소에 있습니다:

### 👉 [github.com/r00tIsRoot/LMBridgeSample](https://github.com/r00tIsRoot/LMBridgeSample)

Jetpack Compose로 작성된 샘플 앱이며 이 SDK의 실전 사용 패턴을 보여줍니다:

- **모델 선택 화면** — [ModelCatalog.ALL]을 읽어 40종 모델을 카드 목록으로 렌더링(게이트 배지 포함)
- **다운로드 화면** — 진행률·무결성 검증 표시, 게이트 모델용 HF Access Token 입력
- **다중 채팅방** — 한 모델에 여러 대화방, 방 전환 시 스트리밍 대화 유지
- **모델 전환** — 로드된 모델을 바꿀 때 이전 클라이언트를 안전하게 release

아래 [실전 패턴](#실전-패턴-샘플-앱에서) 절의 코드 스니펫은 모두 이 샘플에서 발췌한 것입니다.

## 요구사항

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 2.4 (litertlm 0.18이 kotlin-stdlib 2.4.0에 의존 → **소비 앱은 Kotlin 2.3 이상** 필요)
- Gradle 8.10.2+

## 설치

### 1. Maven 저장소 추가

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://r00tisroot.github.io/packages/") }
    }
}
```

### 2. 의존성 추가

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.isroot:lmbridge:0.2.2")
}
```

또는 `lmbridge/build/outputs/aar/lmbridge-release.aar` 에서 AAR을 직접 참조할 수도 있습니다.

## 빠른 시작

```kotlin
// 1. 클라이언트 생성 (모델 지정: 카탈로그 · 경로 · 번들 에셋 중 택1)
val client = LMBridgeClient.Builder(context)
    .setModel(ModelCatalog.QWEN2_5_1_5B_INSTRUCT)  // 로컬에 받아둔 카탈로그 모델
    .build()

// 2. 초기화 (suspend, 멱등)
lifecycleScope.launch {
    client.initialize()

    // 3. 텍스트 생성 (스트리밍) — Flow<GenerationChunk>
    client.generate("안녕하세요").collect { chunk ->
        when (chunk) {
            is GenerationChunk.Token -> print(chunk.text)
            is GenerationChunk.ToolCall -> println("[도구호출: ${chunk.name} ${chunk.argsJson}]")
            is GenerationChunk.Done -> println("\n[완료]")
            is GenerationChunk.Error -> println("[오류: ${chunk.error.message}]")
        }
    }

    // 4. 정리
    client.release()
}

// 진행 중 생성 취소
client.stop()
```

> ⚠️ **v2 파괴적 변경**: 공개 API에서 litertlm 타입을 제거했습니다.
> `Flow<GenerationResult>` → `Flow<GenerationChunk>`, `newConversation()` → `newChat()`,
> `stopGeneration()` → `stop()`. 구 메서드는 `@Deprecated`로 한 사이클 유지됩니다.
> 자세한 배경은 [docs/](docs/README.md)와 [docs/GAP-ANALYSIS.md](docs/GAP-ANALYSIS.md) 참고.

## 생명주기

```
Builder.build()  →  initialize()  →  generate() / newChat()  →  release()
```

- `initialize()` 는 **off-main·멱등**입니다. 여러 번 호출해도 안전합니다.
- 네이티브 세션 생성/해제(`newChat`·`Chat.close`·`release`)는 **블로킹**이므로 백그라운드
  디스패처에서 호출하세요(메인 스레드 호출 시 ANR).
- 한 엔진에서 **대화 세션을 여러 개 동시에** 열 수 있습니다(litertlm 0.18+). 세션끼리 문맥이 섞이지 않고,
  생성 요청은 엔진이 순서대로 처리합니다. 자세한 내용은 [실전 패턴](#3-다중-채팅방과-부수-작업) 참고.

## API 참조

### LMBridgeClient

| 메서드 | 설명 |
|--------|------|
| `initialize()` | LLM 엔진 초기화 (suspend, 멱등, off-main) |
| `generate(prompt, config?)` | 텍스트 단발 생성 → `Flow<GenerationChunk>`. 독립된 임시 세션에서 실행되어 열린 `Chat`에 영향 없음 |
| `generate(input, config?)` | `MultimodalInput`(텍스트·이미지·오디오·문서 혼합) 단발 생성 |
| `generateText(prompt, config?)` | 단발 생성 결과를 답변 문자열로 반환(suspend). 사실 추출·요약 같은 부수 작업용 |
| `newChat(config)` | 상태 유지 대화 세션([Chat]) 생성 |
| `stop()` | 진행 중인 단발 생성 모두 취소 |
| `release()` | 모든 리소스 및 엔진 해제 (이후 재사용 불가) |
| `models` | 모델 다운로드 관리자([ModelDownloadManager]) |

**Builder 옵션**

| 메서드 | 설명 |
|--------|------|
| `setModel(info)` | 카탈로그/커스텀 모델 지정. `initialize()`가 로컬 존재를 확인해 로드 |
| `setModelPath(path)` | 로컬 `.litertlm` 파일 경로를 직접 지정 |
| `setBackend(backend)` | `CPU`(기본) · `GPU` · `NPU` |
| `setMaxNumTokens(n)` | 총 컨텍스트(입력+출력 합산) 토큰 상한 = KV 캐시 길이. **지정하지 않으면 각 모델 내장 최대값(파일명 `ekvNNNN`, 예 ekv4096) 사용.** 값을 지정하면 상한을 낮춰 메모리를 줄임(큰 입력은 잘림) |
| `setEnableSpeculativeDecoding(b)` | MTP 사용 여부(기본 false). 품질 저하 없이 decode 가속(GPU 권장). MTP 드래프터 포함 모델(Gemma 4 등) 전용 |
| `setEnableBenchmark(b)` | 처리 속도 통계 수집(기본 false). `Chat.stats()`에 prefill/decode 토큰 수·tokens/s 포함 |
| `setCpuThreadCount(n)` | CPU 백엔드(비전·오디오 CPU 인코더 포함) 추론 스레드 수. 미지정 시 litertlm 기본값 |

셋 다 지정하지 않으면 번들 에셋 `gemma-4-E2B-it.litertlm`을 사용합니다.

구 메서드(`generateWithImages/Audio/Files/Input`, `stopGeneration`, `getDownloadManager`,
`close`)는 `@Deprecated`로 유지되며 새 API로 위임합니다.

### Chat (상태 유지 대화)

```kotlin
val chat = client.newChat(ChatConfig(systemInstruction = "너는 친절한 도우미야."))
chat.send("안녕").collect { chunk -> /* ... */ }
chat.send("방금 뭐라고 했지?").collect { chunk -> /* 히스토리 유지 */ }
chat.stop()   // 진행 중 생성 취소
chat.close()  // 세션 해제 (off-main 권장)
```

**ChatConfig 옵션** — 모두 선택이며, 지정하지 않으면 모델 번들의 기본값을 씁니다.

```kotlin
val chat = client.newChat(
    ChatConfig(
        systemInstruction = "너는 친절한 도우미야.",
        sampler = Sampler(topK = 10, topP = 0.95, temperature = 0.8),   // 샘플링
        history = listOf(                                               // 이전 대화 복원/퓨샷
            ChatMessage.user("대한민국의 수도는?"),
            ChatMessage.model("서울입니다."),
        ),
        thinking = Thinking(enabled = true, tokenBudget = 1024),        // -1 무제한, 0 비활성
        maxOutputTokens = 2048,                                         // 응답 1건 출력 상한
    ),
)
chat.send("왜 그런지 설명해줘").collect { chunk ->
    when (chunk) {
        is GenerationChunk.Thought -> showReasoning(chunk.text)  // 추론 과정(답변과 분리)
        is GenerationChunk.Token -> print(chunk.text)            // 답변
        else -> {}
    }
}
val stats = chat.stats()  // contextTokens·TTFT(항상) + 토큰 수·tokens/s(setEnableBenchmark(true) 시)
```

### GenerationChunk

```kotlin
sealed interface GenerationChunk {
    data class Token(val text: String) : GenerationChunk                          // 스트리밍 토큰
    data class Thought(val text: String) : GenerationChunk                        // 추론(thinking) 텍스트
    data class ToolCall(val name: String, val argsJson: String) : GenerationChunk // 도구 호출
    data object Done : GenerationChunk                                            // 생성 완료
    data class Error(val error: LMBridgeError) : GenerationChunk                  // 타입화된 오류
}
```

### MultimodalInput

미디어는 **바이트**·**파일 경로**로 넣을 수 있고, 이미지는 추가로 **비트맵**을 받습니다. 파일 경로
소스(`imageFile`/`audioFile`)는 파일을 앱 힙에 적재하지 않고 엔진에 경로로 직접 전달하므로
대용량 미디어에 유리합니다.

```kotlin
// 편의 팩토리
MultimodalInput.text("안녕하세요")
MultimodalInput.textAndImages("이 이미지를 설명해줘", listOf(bitmap1, bitmap2))

// 빌더(순서대로 혼합)
MultimodalInput.Builder()
    .image(cameraBitmap)                       // 비트맵 → 무손실 PNG(기본)
    .imageBytes(jpegBytes)                      // 이미 인코딩된 이미지 바이트(재인코딩 없음)
    .imageFile("/sdcard/photo.jpg")            // 이미지 파일 경로(힙 적재 없음)
    .audioBytes(recordedPcmBytes)              // 이미 인코딩된 오디오 바이트
    .audioFile("/sdcard/clip.wav")             // 오디오 파일 경로(힙 적재 없음)
    .document("/path/to/notes.txt")            // 텍스트 문서(UTF-8 기본, charset 지정 가능)
    .text("이 자료들을 종합해서 설명해줘")
    .build()
```

**이미지 인코딩(옵트인).** 기본은 무손실 PNG라 카메라 사진이 수십 MB가 될 수 있습니다.
경량화가 필요하면 `ImageEncoding`으로 다운스케일·JPEG를 명시하세요.

```kotlin
MultimodalInput.Builder()
    .image(cameraBitmap, ImageEncoding.COMPACT_JPEG)          // 긴 변 1024px + JPEG q90
    .image(cameraBitmap, ImageEncoding(maxDimension = 768, format = ImageEncoding.Format.JPEG, quality = 85))
    .text("설명해줘")
    .build()
```

**문서 처리.** 텍스트 문서만 지원합니다. 파일이 없거나·읽을 수 없거나·크기 예산을 초과하거나·바이너리
(PDF·오피스·이미지 등, litertlm에 문서 `Content` 타입 없음)이면 조용히 무시하지 않고
`GenerationChunk.Error(LMBridgeError.InvalidInput)`으로 표면화합니다. 잘못된 이미지/오디오 파일
경로도 동일하게 `InvalidInput`으로 전달됩니다.

**인메모리 입력 크기 예산(기기-적응형).** 오디오·이미지 바이트와 문서 텍스트처럼 앱 힙에 적재되는
입력은 저사양 기기에서 OOM/네이티브 크래시를 일으킬 수 있어, 초기화 시점의 기기 총 RAM으로 1건당
최대 바이트 예산을 자동 산출합니다(`≤4GB → 4MB`, `≤6GB → 16MB`, `>6GB → 64MB`, 저사양 기기는
한 단계 낮춤). 예산을 넘는 입력은 `LMBridgeError.InvalidInput`으로 표면화됩니다. 파일 경로
소스(`imageFile`/`audioFile`)는 힙에 적재되지 않으므로 이 예산의 적용을 받지 않습니다 — 대용량
미디어는 파일 경로로 넣으세요. 예산을 직접 지정하려면 `Builder.setMaxInputBytes(bytes)`를 씁니다.

```kotlin
val client = LMBridgeClient.Builder(context)
    .setModel(model)
    .setAudioBackend(LMBridge.Backend.CPU)
    .setMaxInputBytes(32L * 1024 * 1024)   // (선택) 자동 산출 대신 32MB로 고정
    .build()
```

### Tool (도구 호출)

```kotlin
val weatherTool = Tool.builder("get_weather", "위치의 현재 날씨 가져오기")
    .param("location", type = "string", description = "도시명", required = true)
    // (선택) 자동 실행 핸들러 — 없으면 GenerationChunk.ToolCall로 앱에 전달됨
    .executor { args -> """{"temp": 24, "sky": "맑음"}""" }
    .build()

val chat = client.newChat(ChatConfig(tools = listOf(weatherTool)))
chat.send("서울 날씨 어때?").collect { chunk ->
    when (chunk) {
        is GenerationChunk.ToolCall -> { /* 앱이 직접 처리하는 경우 */ }
        is GenerationChunk.Token -> print(chunk.text)
        else -> {}
    }
}
```

**수동 도구 호출.** `automaticToolCalling = false`면 엔진이 `executor`를 실행하지 않고
`ToolCall`만 전달합니다. 앱이 도구를 실행(권한 확인·사용자 승인 등)한 뒤 결과를 돌려줍니다.

```kotlin
val chat = client.newChat(ChatConfig(tools = listOf(weatherTool), automaticToolCalling = false))
val calls = mutableListOf<GenerationChunk.ToolCall>()
chat.send("서울 날씨 어때?").collect { if (it is GenerationChunk.ToolCall) calls += it }
val results = calls.map { ToolResponse(it.name, runMyTool(it.name, it.argsJson)) }
chat.sendToolResponses(results).collect { if (it is GenerationChunk.Token) print(it.text) }
```

도구 호출·추론에는 이를 지원하는 모델(FunctionGemma, Gemma 4 등)이 필요합니다.

## 모델 다운로드 & 카탈로그

### 모델 카탈로그

[ModelCatalog]는 LiteRT-LM이 로드 가능한 `.litertlm` 모델 **40종**(대부분
[litert-community](https://huggingface.co/litert-community) 텍스트 생성 모델)을 담고 있습니다.
각 항목의 `commitHash`·`sizeInBytes`·`modelFile`은 재현 가능한 다운로드를 위해 특정 리비전에
고정돼 있습니다.

```kotlin
import com.isroot.lmbridge.models.ModelCatalog

// 전체 목록(이름 abc 오름차순) — 모델 선택 UI에 그대로 사용
ModelCatalog.ALL.forEach { info ->
    println("${info.displayName}  ${info.sizeInBytes / 1_000_000}MB  gated=${info.gated}")
}

// modelId로 조회
val info = ModelCatalog.byId("litert-community/Qwen2.5-1.5B-Instruct")

// 자주 쓰는 모델은 명명 상수로 바로 참조
val gemma3_1B  = ModelCatalog.GEMMA3_1B_IT              // 584MB (gated)
val qwen2_5    = ModelCatalog.QWEN2_5_1_5B_INSTRUCT     // 1.6GB
val deepseekR1 = ModelCatalog.DEEPSEEK_R1_DISTILL_QWEN_1_5B  // 1.8GB
val gemma4E2B  = ModelCatalog.GEMMA_4_E2B_IT            // 2.5GB (gated)
val gemma4E4B  = ModelCatalog.GEMMA_4_E4B_IT            // 3.6GB (gated)
val gemma3nE2B = ModelCatalog.GEMMA_3N_E2B_IT           // 3.6GB (gated)
val gemma3nE4B = ModelCatalog.GEMMA_3N_E4B_IT           // 4.9GB (gated)
```

**ModelInfo 필드**

| 필드 | 설명 |
|------|------|
| `modelId` | HuggingFace 모델 ID |
| `modelFile` | 내려받을 `.litertlm` 파일명 |
| `commitHash` | 고정 리비전 커밋 해시 |
| `sizeInBytes` | 전체 크기(진행률·검증용) |
| `sha256` | 무결성 검증용 해시(있으면 SHA-256, 없으면 크기 검증) |
| `displayName` | 화면 표시 이름(미지정 시 `modelId`의 마지막 조각) |
| `gated` | HF 게이트(제한) 모델 여부. `true`면 다운로드에 Access Token 필요 |

### 다운로드

```kotlin
val downloadManager = client.models  // 또는 별도 클라이언트의 .models

lifecycleScope.launch {
    val model = ModelCatalog.QWEN2_5_1_5B_INSTRUCT
    if (!downloadManager.isModelDownloaded(model)) {
        downloadManager.downloadModel(model, accessToken = null).collect { status ->
            when (status) {
                is DownloadStatus.NotStarted -> println("시작 대기…")
                is DownloadStatus.Downloading ->
                    println("${status.progressPercent}%  " +
                        "(${status.receivedBytes / 1_000_000}/${status.totalBytes / 1_000_000}MB)")
                is DownloadStatus.Verifying -> println("무결성 검증 중…")
                is DownloadStatus.Completed -> println("완료: ${status.filePath}")
                is DownloadStatus.Failed -> println("실패: ${status.message}")
            }
        }
    }
    val path = downloadManager.getModelPath(model)
}
```

다운로드는 `.part` 임시 파일로 받아 **이어받기**를 지원하고, 완료 후 SHA-256(제공 시) 또는
크기를 검증한 뒤에만 최종 파일로 원자적 승격합니다.

### 게이트(제한) 모델

`gated = true` 모델(Gemma 계열 등)은 HuggingFace Access Token이 필요합니다:

1. 모델 페이지(`https://huggingface.co/{modelId}`)에서 로그인 후 라이선스에 동의
2. 읽기 권한 토큰(`hf_…`)을 발급
3. `downloadModel(model, accessToken = "hf_…")`로 전달

`401`/`403` 응답은 대개 라이선스 미동의 또는 토큰 누락입니다.

### 사용자 정의 · 삭제

```kotlin
// 카탈로그에 없는 모델
val custom = ModelDownloadManager.ModelInfo(
    modelId = "your-org/your-model",
    modelFile = "model.litertlm",
    commitHash = "abc123…",
    sizeInBytes = 1_000_000_000,
)
downloadManager.downloadModel(custom).collect { /* ... */ }

// 삭제
downloadManager.deleteModel(ModelCatalog.QWEN2_5_1_5B_INSTRUCT)
```

## 실전 패턴 (샘플 앱에서)

아래 스니펫은 [LMBridgeSample](https://github.com/r00tIsRoot/LMBridgeSample)의 `ChatViewModel`에서
발췌·요약한 것입니다.

### 1. 카탈로그로 모델 선택 UI 만들기

라이브러리 카탈로그를 그대로 읽어 목록을 구성하면, 라이브러리에 모델이 추가될 때 앱 수정 없이
자동 반영됩니다.

```kotlin
data class ModelUi(val id: String, val label: String, val sizeMb: Long, val gated: Boolean)

val models = ModelCatalog.ALL.map {
    ModelUi(it.modelId, it.displayName, it.sizeInBytes / 1_000_000, it.gated)
}
// 다운로드 여부는 downloadManager.isModelDownloaded(info)로 확인
```

### 2. 한 번에 한 모델만 로드 (전환 시 release)

온디바이스 메모리 한계상 모델은 **한 번에 하나만** 로드하고, 바꿀 때 이전 클라이언트를
반드시 release합니다. 다운로드 전용으로는 `initialize()`를 부르지 않는 별도 클라이언트를 두면
네이티브 자원을 점유하지 않고 `models`만 쓸 수 있습니다.

```kotlin
suspend fun loadModel(info: ModelDownloadManager.ModelInfo) {
    releaseActiveClient()  // 이전 모델 해제 (네이티브 release는 off-main)

    val client = LMBridgeClient.Builder(appContext)
        .setModel(info)
        .setBackend(LMBridge.Backend.CPU)
        // setMaxNumTokens 생략 → 모델 내장 최대 컨텍스트 사용(큰 문서/오디오 지원).
        // 메모리를 줄이려면 .setMaxNumTokens(1024)처럼 상한을 낮춘다.
        .build()
    client.initialize()    // suspend — 미다운로드 시 ModelNotFound
    activeClient = client
}

private suspend fun releaseActiveClient() = withContext(Dispatchers.Default) {
    activeChats.forEach { runCatching { it.close() } }
    runCatching { activeClient?.release() }
}
```

### 3. 다중 채팅방과 부수 작업

litertlm 0.18부터 한 엔진에서 대화 세션을 **여러 개 동시에** 열 수 있습니다. 세션마다 KV 캐시가
따로 유지되므로, 채팅방마다 세션을 열어 두면 방을 오가도 **다시 prefill하지 않습니다**(턴마다 새
입력만 처리 → 첫 토큰이 빠름). 단, 세션마다 KV 메모리가 들기 때문에 저사양 기기에서는 열어 둘
세션 수에 상한을 두고 가장 오래 안 쓴 방부터 닫으세요. 닫힌 방은 다시 열 때
`ChatConfig.history`로 기록을 주입해 문맥을 복원합니다.

```kotlin
// 방마다 세션을 유지하고, 상한을 넘으면 가장 오래 안 쓴 방의 세션을 닫는다(off-main).
private suspend fun bindSession(client: LMBridgeClient, target: Room) = withContext(Dispatchers.Default) {
    if (target.chat == null) {
        target.chat = client.newChat(ChatConfig(systemInstruction = systemPrompt, history = historyOf(target)))
    }
    target.lastUsed = System.nanoTime()
    rooms.filter { it.chat != null }
        .sortedByDescending { it.lastUsed }
        .drop(MAX_OPEN_SESSIONS)
        .forEach { r -> r.chat?.close(); r.chat = null }
}
```

**부수 작업은 대화에 끼워 넣지 마세요.** 사실 추출·요약·판단처럼 대화와 상관없는 지시를 같은 `Chat`에
보내면 이후 응답이 그 지시에 끌려가 대화가 오염됩니다. `generate`/`generateText`는 매번 독립된 임시
세션에서 실행되므로 열린 대화의 문맥을 건드리지 않습니다. 엔진은 생성을 순서대로 처리하므로, 대화
응답이 진행 중이면 그 뒤에 실행됩니다.

```kotlin
val facts = client.generateText(
    "다음 대화에서 사용자에 관한 사실을 JSON 줄로 뽑아줘:\n$recentTurns",
    ChatConfig(systemInstruction = "You extract facts.", maxOutputTokens = 256),
)
```

## 백엔드 설정

기본은 CPU입니다. 빌더에서 백엔드를 지정합니다:

```kotlin
val client = LMBridgeClient.Builder(context)
    .setBackend(LMBridge.Backend.GPU)  // 옵션: CPU, GPU, NPU
    .build()
```

NPU는 앱에 번들된 벤더 런타임을 `applicationInfo.nativeLibraryDir`에서 자동으로 찾습니다.
GPU는 prefill이 CPU보다 수 배 빠르고 메모리 사용량도 낮아(Gemma 4 E2B, S26 Ultra 기준
prefill 3808 vs 557 tk/s, 피크 메모리 676 vs 1733MB) 지원 기기에서는 GPU를 권장합니다.

GPU(OpenCL)/NPU 네이티브 라이브러리는 매니페스트에 `required="false"`로 선언되어, 미지원
기기에서도 설치가 가능합니다.

### GPU 실패 시 CPU로 폴백

폴백 여부는 앱이 정합니다(큰 모델은 CPU에서 너무 느려 차라리 실패가 나을 수 있음). 라이브러리는
실패 원인을 구분해 줍니다. `BackendUnavailable`이면 다른 백엔드로 새 클라이언트를 만들어 재시도하고,
`InvalidModel`(파일 손상·헤더 불일치)은 백엔드를 바꿔도 실패하므로 재다운로드를 안내합니다.

```kotlin
suspend fun createClient(backend: LMBridge.Backend): LMBridgeClient {
    val client = LMBridgeClient.Builder(context).setModel(model).setBackend(backend).build()
    try {
        client.initialize()
        return client
    } catch (e: LMBridgeError.BackendUnavailable) {
        client.release()
        if (backend == LMBridge.Backend.CPU) throw e
        return createClient(LMBridge.Backend.CPU)
    }
}
```

CPU 추론 스레드 수는 `setCpuThreadCount(n)`으로 정합니다. 기본값은 코어 수보다 적게 잡힐 수 있어,
SM-A426N(8코어, Gemma 4 E2B)에서는 4~6 스레드가 기본값 대비 prefill 약 1.6배·첫 토큰 지연 약 절반이었습니다
(8 스레드는 이득 없음). 최적값은 기기마다 다르니 `setEnableBenchmark(true)`로 측정해 정하세요.

## 로그 설정

```kotlin
// 0 = OFF, 1 = ERROR, 2 = WARN, 3 = DEBUG, 4 = VERBOSE
LMBridge.setLogLevel(3)                       // DEBUG
LMBridge.logLevel = LMBridge.LogLevel.DEBUG   // enum으로 직접 설정
```

| 레벨 | 설명 |
|------|------|
| OFF | 로그 출력 안함 |
| ERROR | 오류 메시지만 |
| WARN | 경고와 오류 |
| DEBUG | 디버그·경고·오류 |
| VERBOSE | 모든 로그(상세 실행 정보 포함) |

로그 레벨은 엔진 초기화 시 litertlm 네이티브(C++) 로그 하한에도 반영됩니다
(OFF→출력 없음, ERROR→ERROR, WARN→WARNING, DEBUG→INFO, VERBOSE→VERBOSE).

## 오류 처리

`initialize()`는 실패 시 타입화된 [LMBridgeError]를 던집니다(`ModelNotFound`, `InvalidModel`,
`BackendUnavailable`, `OutOfMemory`, `Released` 등). 생성 스트림의 오류는
`GenerationChunk.Error`로 전달됩니다.

```kotlin
lifecycleScope.launch {
    try {
        client.initialize()
    } catch (e: LMBridgeError.ModelNotFound) {
        // 모델을 먼저 다운로드하세요: client.models.downloadModel(...)
    } catch (e: LMBridgeError.InvalidModel) {
        // 파일이 손상됨 → 삭제 후 재다운로드
    } catch (e: LMBridgeError.BackendUnavailable) {
        // 다른 백엔드로 재시도(위 "GPU 실패 시 CPU로 폴백")
    } catch (e: Exception) {
        Log.e("LMBridge", "초기화 실패: ${e.message}")
    }
}
```

## 의존성

- `androidx.core:core-ktx:1.15.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
- `com.google.ai.edge.litertlm:litertlm-android:0.18.0`

## 문서

더 깊은 설계 문서는 [`docs/`](docs/README.md)에 있습니다:

- [00-overview](docs/00-overview.md) · [01-architecture](docs/01-architecture.md)
- [02-public-api](docs/02-public-api.md) · [03-model-management](docs/03-model-management.md)
- [04-inference-engine](docs/04-inference-engine.md) · [05-roadmap](docs/05-roadmap.md)
- [GAP-ANALYSIS](docs/GAP-ANALYSIS.md)

## 라이선스

Apache License 2.0
