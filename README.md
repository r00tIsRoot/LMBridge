# LMBridge

Google LiteRT-LM (온디바이스 LLM 추론) Android SDK

## 개요

LMBridge는 Google's LiteRT-LM 엔진을 사용하여 온디바이스 LLM 추론을 제공하는 Android 라이브러리입니다. 텍스트 생성, 멀티모달 (이미지 + 텍스트) 추론, 도구 호출을 지원합니다.

## 요구사항

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 2.2.0
- Gradle 8.10.2+

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
    implementation("com.isroot:lmbridge:0.0.7")
}
```

또는 `lmbridge/build/outputs/aar/lmbridge-release.aar` 에서 AAR 파일을 직접 참조할 수도 있습니다.

## 빠른 시작

```kotlin
// 1. 클라이언트 생성
val client = LMBridgeClient.Builder(context)
    .setModelPath("/path/to/model.litertlm")  // 선택사항, 설정하지 않으면 asset 사용
    .build()

// 2. 초기화 (비동기)
lifecycleScope.launch {
    client.initialize()
    
    // 3. 텍스트 생성 (스트리밍)
    client.generate("안녕하세요").collect { result ->
        when (result) {
            is GenerationResult.Token -> print(result.text)
            is GenerationResult.Done -> println("\n[완료]")
            is GenerationResult.Error -> println("[오류: ${result.message}]")
        }
    }
    
    // 4. 정리
    client.release()
}
```

## API 참조

### LMBridgeClient

| 메서드 | 설명 |
|--------|------|
| `initialize()` | LLM 엔진 초기화 |
| `generate(prompt)` | 텍스트만 생성 |
| `generateWithImages(prompt, images)` | 멀티모달 (텍스트 + 이미지) |
| `generateWithTools(prompt, tools)` | 도구 호출 |
| `generateWithInput(input)` | MultimodalInput 사용 |
| `stopGeneration()` | 진행 중인 생성 취소 |
| `release()` | 리소스 해제 |
| `getDownloadManager()` | 모델 다운로드 관리자 가져오기 |

### GenerationResult

```kotlin
sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()  // 스트리밍 토큰
    data object Done : GenerationResult()                     // 생성 완료
    data class Error(val message: String) : GenerationResult() // 오류 발생
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
    data class Failed(val message: String) : DownloadStatus()
    data class Completed(val filePath: String) : DownloadStatus()
}
```

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
    client.generate("봄에 관한 하이쿠를 작성해줘:").collect { result ->
        when (result) {
            is GenerationResult.Token -> appendToTextView(result.text)
            is GenerationResult.Done -> showCompletionMessage()
            is GenerationResult.Error -> showError(result.message)
        }
    }
}
```

### 멀티모달 (이미지 + 텍스트)

```kotlin
lifecycleScope.launch {
    val images = listOf(cameraBitmap, galleryBitmap)
    client.generateWithImages("이 이미지를 설명해줘:", images).collect { result ->
        when (result) {
            is GenerationResult.Token -> updateResponse(result.text)
            is GenerationResult.Done -> finish()
            is GenerationResult.Error -> error(result.message)
        }
    }
}
```

### 도구 호출

```kotlin
val weatherTool = ToolProvider.builder()
    .addTools(
        Tool.create(
            name = "get_weather",
            description = "위치의 현재 날씨 가져오기",
            params = listOf(
                Tool.Param(name = "location", type = "string", required = true)
            )
        )
    )
    .build()

lifecycleScope.launch {
    client.generateWithTools("서울 날씨 어때?", listOf(weatherTool))
        .collect { result ->
            // 응답 처리
        }
}
```

### 생성 중지

```kotlin
// 사용자가 취소 버튼을 탭
client.stopGeneration()
```

## 백엔드 설정

SDK는 기본적으로 NPU 백엔드를 사용합니다. `ModelInferenceManager`에서 수정할 수 있습니다:

```kotlin
val engineConfig = EngineConfig(
    modelPath = finalModelPath,
    backend = Backend.NPU(),  // 옵션: NPU(), CPU(), GPU()
)
```

NPU의 경우 네이티브 라이브러리 디렉토리가 자동으로 감지됩니다. 다른 백엔드의 경우 특별한 설정이 필요하지 않습니다.

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
val downloadManager = client.getDownloadManager()

lifecycleScope.launch {
    // 모델이 이미 다운로드되었는지 확인
    if (!downloadManager.isModelDownloaded(ModelCatalog.GEMMA_4_E2B_IT)) {
        // 진행률과 함께 다운로드
        downloadManager.downloadModel(ModelCatalog.GEMMA_4_E2B_IT)
            .collect { status ->
                when (status) {
                    is ModelDownloadManager.DownloadStatus.NotStarted -> {
                        println("다운로드 시작...")
                    }
                    is ModelDownloadManager.DownloadStatus.Downloading -> {
                        println("진행률: ${status.progressPercent}%")
                        println("다운로드: ${status.receivedBytes / 1024 / 1024}MB / ${status.totalBytes / 1024 / 1024}MB")
                    }
                    is ModelDownloadManager.DownloadStatus.Completed -> {
                        println("다운로드 완료: ${status.filePath}")
                    }
                    is ModelDownloadManager.DownloadStatus.Failed -> {
                        println("실패: ${status.message}")
                    }
                }
            }
    }
    
    // 다운로드된 모델 경로 가져오기
    val modelPath = downloadManager.getModelPath(ModelCatalog.GEMMA_4_E2B_IT)
    println("모델 경로: $modelPath")
}
```

### 다운로드된 모델 사용

```kotlin
lifecycleScope.launch {
    val downloadManager = client.getDownloadManager()
    val modelPath = downloadManager.getModelPath(ModelCatalog.GEMMA_4_E2B_IT)
    
    if (modelPath != null) {
        // 다운로드된 모델로 클라이언트 생성
        val inferenceClient = LMBridgeClient.Builder(context)
            .setModelPath(modelPath)
            .build()
        
        inferenceClient.initialize().collect { /* ... */ }
    }
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
- `com.google.ai.edge.litertlm:litertlm-android:0.10.0`

## 라이선스

Apache License 2.0
