# 02 · 공개 API (Public API)

> 목표 API를 정의한다. 현재 코드와의 차이는 각 절의 "현재" 표기와
> [GAP-ANALYSIS.md](GAP-ANALYSIS.md)를 참조.

## 1. 설계 규칙

- **litertlm 타입 금지**: 공개 시그니처(파라미터/반환/프로퍼티)에
  `com.google.ai.edge.litertlm.*` 타입이 등장하지 않는다.
  - 위반 현황: `LMBridgeClient.newConversation(): Conversation`,
    `generateWithConversation(conversation: Conversation, ...)`,
    `newConversation(tools: List<ToolProvider>)`,
    `generateWithTools(tools: List<ToolProvider>)`
    → `Conversation`은 LMBridge `Chat` 세션 타입으로, `ToolProvider`는 LMBridge `Tool`로 대체.
- **최소 표면**: 진입점은 `LMBridgeClient` 하나. 세부는 Builder와 설정 객체로.
- **결과는 sealed**: 스트리밍은 `Flow<GenerationChunk>`, 실패는 타입화된 오류.

## 2. 진입점: `LMBridgeClient`

```kotlin
class LMBridgeClient private constructor(...) {

    // 생명주기
    suspend fun initialize()                 // 멱등, off-main, 실패 시 LMBridgeError
    fun release()                            // 자원 해제

    // 단발 생성 (내부적으로 임시 세션)
    fun generate(input: MultimodalInput): Flow<GenerationChunk>
    fun generate(prompt: String): Flow<GenerationChunk>          // 편의 오버로드

    // 상태 유지 대화
    fun newChat(config: ChatConfig = ChatConfig()): Chat

    // 취소
    fun stop()                               // 활성 생성 취소 (현재 no-op 버그 수정 대상)

    // 리소스
    val models: ModelDownloadManager

    class Builder(context: Context) {
        fun model(model: ModelRef): Builder            // 카탈로그 또는 커스텀
        fun modelPath(path: String): Builder           // 로컬 파일 직접 지정
        fun backend(backend: Backend): Builder         // 기본 AUTO
        fun maxTokens(n: Int): Builder                 // 기본 1024
        fun downloadPolicy(policy: DownloadPolicy): Builder
        fun build(): LMBridgeClient
    }
}
```

### 편의: 미보유 모델 자동 다운로드
`Builder.model(ModelCatalog.GEMMA3_1B_IT)`로 지정 시, `initialize()`가 로컬 부재를
감지하면 정책에 따라 다운로드→검증→로드까지 수행(진행률은 `models.observe()`로 관찰).

## 3. 대화 세션: `Chat`

litertlm `Conversation`을 감싼 LMBridge 타입. 세션 상태(히스토리)를 유지.

```kotlin
interface Chat {
    fun send(input: MultimodalInput): Flow<GenerationChunk>
    fun send(prompt: String): Flow<GenerationChunk>
    fun stop()
    fun reset()          // 히스토리 초기화
    fun close()          // 세션 자원 해제
}

data class ChatConfig(
    val systemInstruction: String = "You are a helpful AI assistant.",
    val tools: List<Tool> = emptyList(),
)
```

## 4. 입력: `MultimodalInput` / `Tool`

```kotlin
sealed interface Part {
    data class Text(val text: String) : Part
    data class Image(val bitmap: Bitmap) : Part
    data class Audio(val bytes: ByteArray) : Part
    data class Document(val path: String) : Part   // generateWithFiles 대체
}

class MultimodalInput private constructor(val parts: List<Part>) {
    class Builder {
        fun text(s: String): Builder
        fun image(b: Bitmap): Builder
        fun audio(b: ByteArray): Builder
        fun document(path: String): Builder
        fun build(): MultimodalInput
    }
    companion object {
        fun text(s: String): MultimodalInput
        fun textAndImages(s: String, images: List<Bitmap>): MultimodalInput
    }
}
```

- **현재 문제**: `generateWithInput`이 audio > image 우선순위로 **한 modality만** 처리해
  텍스트+이미지+오디오 혼합을 못 한다. 목표 API는 `parts` 순서를 그대로 litertlm
  `Contents`로 매핑한다.
- **Tool**은 litertlm `ToolProvider`를 노출하지 않고 LMBridge에서 선언:

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val params: List<Param>,
) { data class Param(val name: String, val type: String, val required: Boolean) }
```

## 5. 출력: `GenerationChunk`

현재 `GenerationResult`(Token/Done/Error)를 확장·개명한다.

```kotlin
sealed interface GenerationChunk {
    data class Token(val text: String) : GenerationChunk
    data class ToolCall(val name: String, val argsJson: String) : GenerationChunk  // 도구호출 노출
    data class Done(val stats: GenerationStats? = null) : GenerationChunk
    data class Error(val error: LMBridgeError) : GenerationChunk
}
```

- **현재 문제**: 토큰 텍스트를 `message.toString()`으로 만든다(`ModelInferenceManager`
  콜백들). `Message`의 실제 텍스트 추출 API로 교체해야 한다(디버그 문자열 오염 방지).
- **현재 문제**: `processChunkedGenerate`가 출력 스트림에 `"Processing N chunks..."`,
  `"--- Chunk i/N ---"` 같은 **UI 마커를 토큰으로 흘려보낸다.** 이는 소비 앱 화면을
  오염시킨다. 청킹은 내부 구현이어야 하며 출력에 새어나오면 안 된다(04-inference-engine §청킹).

## 6. 오류 모델: `LMBridgeError`

현재는 `Error(message: String)` 단일. 타입화하여 소비 앱이 분기·복구할 수 있게 한다.

```kotlin
sealed class LMBridgeError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotInitialized       : LMBridgeError("Engine not initialized")
    class ModelNotFound        (path: String) : LMBridgeError("Model not found: $path")
    class BackendUnavailable   (backend: Backend) : LMBridgeError("Backend unavailable: $backend")
    class OutOfMemory          (cause: Throwable?) : LMBridgeError("Out of memory", cause)
    class DownloadFailed       (message: String, cause: Throwable? = null) : LMBridgeError(message, cause)
    class IntegrityCheckFailed (expected: String, actual: String) : LMBridgeError("Integrity mismatch")
    class InferenceFailed      (message: String, cause: Throwable? = null) : LMBridgeError(message, cause)
    class Cancelled            : LMBridgeError("Generation cancelled")
}
```

- 설정/초기화 실패 → `suspend` 함수가 `LMBridgeError` **throw**.
- 스트림 도중 실패 → `GenerationChunk.Error(LMBridgeError)` **emit** 후 종료.

## 7. 전역 설정: `LMBridge`

현존 유지(로그 레벨). API는 안정적이므로 그대로 둔다.

```kotlin
LMBridge.setLogLevel(3)                    // 0 OFF, 1 ERROR, 2 WARN, 3 DEBUG, 4 VERBOSE
LMBridge.logLevel = LMBridge.LogLevel.DEBUG
```

## 8. 하위호환/마이그레이션

`generate(): Flow<GenerationResult>` 등 기존 시그니처는 **`@Deprecated`로 한 사이클
유지**하고 신규 API로 위임한 뒤 다음 메이저에서 제거한다(05-roadmap §호환 정책).
