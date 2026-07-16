# LMBridge

Google LiteRT-LM (온디바이스 LLM 추론) Android SDK

## 개요

LMBridge는 Google's LiteRT-LM 엔진을 사용하여 온디바이스 LLM 추론을 제공하는 Android 라이브러리입니다. 텍스트 생성, 멀티모달 (이미지, 오디오 + 텍스트) 추론, 도구 호출을 지원합니다.

## 요구사항

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 2.2.0
- Gradle 8.10.2+

## 로그 설정

```kotlin
// 빌드 전에 로그 레벨 설정
// 0 = OFF, 1 = ERROR, 2 = WARN, 3 = DEBUG, 4 = VERBOSE
LMBridge.setLogLevel(3)  // DEBUG 레벨로 설정

// 또는 enum으로 직접 설정
LMBridge.logLevel = LMBridge.LogLevel.DEBUG
```

**로그 레벨 설명:**
| 레벨 | 설명 |
|------|------|
| OFF | 로그 출력 안함 |
| ERROR | 오류 메시지만 |
| WARN | 경고와 오류 |
| DEBUG | 디버그, 경고, 오류 |
| VERBOSE | 모든 로그 (상세 실행 정보 포함) |

## 설치

### 1. Maven Repository 추가

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
    implementation("com.isroot:lmbridge:0.2.0")
}
```

또는 `lmbridge/build/outputs/aar/lmbridge-release.aar` 에서 AAR 파일을 직접 참조할 수도 있습니다.

## 빠른 시작

```kotlin
// 1. 클라이언트 생성
val client = LMBridgeClient.Builder(context)
    .setModelPath("/path/to/model.litertlm")  // 선택사항, 설정하지 않으면 asset 사용
    .build()

// 2. 초기화 (비동기, 멱등)
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

## API 참조

### LMBridgeClient

| 메서드 | 설명 |
|--------|------|
| `initialize()` | LLM 엔진 초기화 (멱등, off-main) |
| `generate(prompt)` | 텍스트 단발 생성 → `Flow<GenerationChunk>` |
| `generate(input)` | `MultimodalInput`(텍스트·이미지·오디오·문서 혼합) 단발 생성 |
| `newChat(config)` | 상태 유지 대화 세션([Chat]) 생성 |
| `stop()` | 진행 중인 단발 생성 취소 |
| `release()` | 모든 리소스 및 엔진 해제 |
| `models` | 모델 다운로드 관리자(`ModelDownloadManager`) |

구 메서드(`generateWithImages/Audio/Files/Input`, `stopGeneration`, `getDownloadManager`,
`close`)는 `@Deprecated`로 유지되며 새 API로 위임합니다.

### Chat (상태 유지 대화)

```kotlin
val chat = client.newChat(ChatConfig(systemInstruction = "너는 친절한 도우미야."))
chat.send("안녕").collect { chunk -> /* ... */ }
chat.send("방금 뭐라고 했지?").collect { chunk -> /* 히스토리 유지 */ }
chat.close()
```

### GenerationChunk

```kotlin
sealed interface GenerationChunk {
    data class Token(val text: String) : GenerationChunk       // 스트리밍 토큰
    data class ToolCall(val name: String, val argsJson: String) : GenerationChunk  // 도구 호출
    data object Done : GenerationChunk                          // 생성 완료
    data class Error(val error: LMBridgeError) : GenerationChunk // 타입화된 오류
}
```

### ModelDownloadManager

```kotlin
sealed class DownloadStatus {
    data object NotStarted : DownloadStatus()
    data class Downloading(
        val totalBytes: Long,
        val receivedBytes: Long,
        val progressPercent: Int
    ) : DownloadStatus()
    data object Verifying : DownloadStatus()                    // SHA-256 무결성 검증 중
    data class Failed(val message: String, val error: LMBridgeError? = null) : DownloadStatus()
    data class Completed(val filePath: String) : DownloadStatus()
}
```

다운로드는 `.part` 임시 파일로 받아 **이어받기**를 지원하고, 완료 후 SHA-256(제공 시)
또는 크기를 검증한 뒤에만 최종 파일로 승격합니다.

### MultimodalInput

```kotlin
// 텍스트만
val input = MultimodalInput.text("안녕하세요")

// 텍스트 + 이미지
val input = MultimodalInput.textAndImages("이 이미지를 설명해줘", listOf(bitmap1, bitmap2))
```

## 예제

### 텍스트 생성

```kotlin
lifecycleScope.launch {
    client.generate("봄에 관한 하이쿠를 작성해줘:").collect { chunk ->
        when (chunk) {
            is GenerationChunk.Token -> appendToTextView(chunk.text)
            is GenerationChunk.Done -> showCompletionMessage()
            is GenerationChunk.Error -> showError(chunk.error.message ?: "오류")
            is GenerationChunk.ToolCall -> handleToolCall(chunk.name, chunk.argsJson)
        }
    }
}
```

### 멀티모달 추론 (이미지·오디오 혼합)

```kotlin
lifecycleScope.launch {
    // 텍스트 + 이미지 + 오디오를 순서대로 혼합
    val input = MultimodalInput.Builder()
        .image(cameraBitmap)
        .audio(audioRecorder.getAudioBytes())
        .text("이 사진과 소리를 함께 설명해줘")
        .build()
    client.generate(input).collect { chunk -> /* ... */ }

    // 편의 팩토리
    client.generate(MultimodalInput.textAndImages("이 이미지 설명해줘", listOf(bitmap)))
        .collect { chunk -> /* ... */ }
}
```

### 도구 호출

```kotlin
val weatherTool = Tool.builder("get_weather", "위치의 현재 날씨 가져오기")
    .param("location", type = "string", description = "도시명", required = true)
    // (선택) 자동 실행 핸들러 — 없으면 GenerationChunk.ToolCall로 앱에 전달됨
    .executor { args -> """{"temp": 24, "sky": "맑음"}""" }
    .build()

lifecycleScope.launch {
    val chat = client.newChat(ChatConfig(tools = listOf(weatherTool)))
    chat.send("서울 날씨 어때?").collect { chunk ->
        when (chunk) {
            is GenerationChunk.ToolCall -> { /* 앱이 직접 처리하는 경우 */ }
            is GenerationChunk.Token -> print(chunk.text)
            else -> {}
        }
    }
    chat.close()
}
```

### 생성 중지

```kotlin
// 사용자가 취소 버튼을 탭
client.stop()          // 단발 생성
// 또는 대화 세션: chat.stop()
```

## 백엔드 설정

기본은 CPU입니다. 빌더에서 백엔드를 지정합니다:

```kotlin
val client = LMBridgeClient.Builder(context)
    .setBackend(LMBridge.Backend.GPU)  // 옵션: CPU, GPU, NPU
    .build()
```

GPU(OpenCL)/NPU 네이티브 라이브러리는 라이브러리 매니페스트에 `required="false"`로 선언되어
있어, 미지원 기기에서도 설치가 가능합니다. (AUTO 자동 폴백은 로드맵 M3 예정 — [docs/05-roadmap.md](docs/05-roadmap.md))

## 모델 파일

`.litertlm` 모델 파일을 `app/src/main/assets/`에 배치하세요. 기본 파일명은 `gemma-4-E2B-it.litertlm`입니다.

사용자 정의 모델 경로 사용:

```kotlin
val client = LMBridgeClient.Builder(context)
    .setModelPath("/sdcard/models/my-model.litertlm")
    .build()
```

## 모델 다운로드

SDK는 진행률 추적으로 HuggingFace에서 모델을 가져오는 다운로드 API를 제공합니다.

### 모델 카탈로그

사전 정의된 모델 정보는 `ModelCatalog`에서 사용할 수 있습니다:

```kotlin
import com.isroot.lmbridge.models.ModelCatalog

// 사용 가능한 모델:
val gemma4E2B = ModelCatalog.GEMMA_4_E2B_IT   // 2.5GB
val gemma4E4B = ModelCatalog.GEMMA_4_E4B_IT   // 3.6GB
val gemma3nE2B = ModelCatalog.GEMMA_3N_E2B_IT // 3.6GB
val gemma3nE4B = ModelCatalog.GEMMA_3N_E4B_IT // 4.9GB
val gemma3_1B = ModelCatalog.GEMMA3_1B_IT     // 584MB
val qwen2_5 = ModelCatalog.QWEN2_5_1_5B_INSTRUCT  // 1.6GB
val deepseekR1 = ModelCatalog.DEEPSEEK_R1_DISTILL_QWEN_1_5B  // 1.8GB
```

### 모델 다운로드

```kotlin
val client = LMBridgeClient.Builder(context).build()
val downloadManager = client.models

lifecycleScope.launch {
    // 모델이 이미 다운로드되었는지 확인
    if (!downloadManager.isModelDownloaded(ModelCatalog.GEMMA_4_E2B_IT)) {
        // 진행률과 함께 다운로드
        downloadManager.downloadModel(ModelCatalog.GEMMA_4_E2B_IT)
            .collect { status ->
                when (status) {
                    is ModelDownloadManager.DownloadStatus.NotStarted -> println("다운로드 시작...")
                    is ModelDownloadManager.DownloadStatus.Downloading ->
                        println("진행률: ${status.progressPercent}% (${status.receivedBytes / 1024 / 1024}MB / ${status.totalBytes / 1024 / 1024}MB)")
                    is ModelDownloadManager.DownloadStatus.Verifying -> println("무결성 검증 중...")
                    is ModelDownloadManager.DownloadStatus.Completed -> println("완료: ${status.filePath}")
                    is ModelDownloadManager.DownloadStatus.Failed -> println("실패: ${status.message}")
                }
            }
    }

    val modelPath = downloadManager.getModelPath(ModelCatalog.GEMMA_4_E2B_IT)
    println("모델 경로: $modelPath")
}
```

### 다운로드된 모델 사용

```kotlin
lifecycleScope.launch {
    // 카탈로그 모델을 지정하면 initialize()가 로컬 다운로드본을 자동으로 찾아 로드
    val inferenceClient = LMBridgeClient.Builder(context)
        .setModel(ModelCatalog.GEMMA_4_E2B_IT)
        .build()
    inferenceClient.initialize()   // suspend — 미다운로드 시 ModelNotFound 예외

    // 또는 경로를 직접 지정
    val modelPath = inferenceClient.models.getModelPath(ModelCatalog.GEMMA_4_E2B_IT)
}
```

### 사용자 정의 모델 다운로드

카탈로그에 없는 모델도 다운로드할 수 있습니다:

```kotlin
val customModel = ModelDownloadManager.ModelInfo(
    modelId = "your-org/your-model",
    modelFile = "model.litertlm",
    commitHash = "abc123...",
    sizeInBytes = 1000000000
)

downloadManager.downloadModel(customModel).collect { /* ... */ }
```

### 다운로드된 모델 삭제

```kotlin
downloadManager.deleteModel(ModelCatalog.GEMMA_4_E2B_IT)
```

## 오류 처리

```kotlin
lifecycleScope.launch {
    try {
        client.initialize()
    } catch (e: Exception) {
        Log.e("LMBridge", "초기화 실패: ${e.message}")
    }
}
```

## 의존성

- `androidx.core:core-ktx:1.15.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
- `com.google.ai.edge.litertlm:litertlm-android:0.11.0`

## 라이선스

Apache License 2.0
