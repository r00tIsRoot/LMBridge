# 04 · 추론 엔진 (Inference Engine)

엔진 어댑터, 백엔드 폴백, 대화 세션, 취소, 멀티모달, 청킹을 다룬다.
현재 구현: `inference/ModelInferenceManager.kt`, `LMBridge.kt`(백엔드 변환).

## 1. 어댑터 경계: `InferenceEngine`

litertlm 의존을 가두는 인터페이스. 상위 레이어는 이 인터페이스만 본다.

```kotlin
internal interface InferenceEngine {
    suspend fun initialize(config: EngineInit)
    fun newSession(config: ChatConfig): EngineSession
    fun close()
}

internal interface EngineSession {
    fun send(parts: List<Part>): Flow<GenerationChunk>
    fun cancel()
    fun close()
}

// litertlm 구현 — 이 파일에서만 litertlm.* import 허용
internal class LiteRtEngineAdapter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,   // 단일 스레드 (01-architecture §3)
) : InferenceEngine { /* Engine/Conversation/Contents 매핑 */ }
```

현재 `ModelInferenceManager`가 Facade·Domain·Engine 역할을 뒤섞고 litertlm 타입을
반환(`createConversation(): Conversation`)한다 → **어댑터 뒤로 격리**.

## 2. 초기화

```kotlin
data class EngineInit(
    val modelPath: String,
    val backend: Backend,        // AUTO 포함
    val maxTokens: Int = 1024,
    val enableSpeculativeDecoding: Boolean = true,
)
```

- 모델 경로 결정은 **ModelStore가 담당**(03 §2). 엔진은 확정된 경로만 받는다.
  - 현재 `initialize()`가 경로 탐색 + 에셋 추출 + 엔진 생성을 한 함수에서 처리 → 분리.
- `ExperimentalFlags.enableSpeculativeDecoding`(MTP) 설정은 어댑터 내부에 유지하되
  플래그로 노출(현재 하드코딩 `true`).
- **멱등성**: 이미 초기화된 엔진이 있으면 재생성하지 않는다(현재 누수 위험).

## 3. 백엔드 & 폴백: `BackendResolver`

```kotlin
enum class Backend { AUTO, CPU, GPU, NPU }
```

- **현재**: `LMBridge.Backend`에 `AUTO`가 없고, `convertToLiteRtBackend`는 1:1 변환만.
  GPU 초기화 실패 시 폴백이 없어 기기에 따라 그냥 실패한다.
- **목표**: `AUTO`는 `NPU?→GPU?→CPU` 순으로 시도하고, 각 단계 실패(`BackendUnavailable`,
  네이티브 로드 실패, OOM 등)를 잡아 다음 후보로 폴백. 최종 실패 시
  `LMBridgeError.BackendUnavailable`.
- 폴백 결정 결과(선택된 백엔드)를 로그/`GenerationStats`로 노출해 디버깅을 돕는다.
- OpenCL 네이티브 라이브러리는 매니페스트에 `required=false`로 선언되어 있어(01 §7),
  미지원 기기에서도 설치는 되고 런타임 폴백이 안전하다.

## 4. 대화 세션 & 취소

### 4.1 세션 매핑
- LMBridge `Chat`/`ChatConfig` → 어댑터가 litertlm `Conversation`/`ConversationConfig`로 매핑.
- `ChatConfig.tools: List<Tool>` → litertlm `ToolProvider`로 어댑터 내부 변환(공개 노출 X).

### 4.2 취소 — **버그 수정 필수**
현재 `LMBridgeClient.stopGeneration()`은
`inferenceManager.stopGeneration(conversation = null)`을 호출하고, 내부는
`conversation?.cancelProcess()`이므로 **null이라 아무 일도 하지 않는다**
(`LMBridgeClient.kt:98`, `ModelInferenceManager.kt:221`). 즉 **취소가 동작하지 않는다.**

**목표**:
- 각 활성 스트림/세션이 자신의 `Conversation` 참조를 보관.
- `client.stop()`/`chat.stop()`은 해당 세션의 `cancelProcess()`를 호출.
- `Flow` 소비자 취소(코루틴 취소)도 `awaitClose { conversation.cancelProcess() }`로
  연결(이 부분은 현재도 존재하므로 유지). 두 경로 모두 실제 취소로 이어져야 한다.

## 5. 멀티모달

- 목표: `MultimodalInput.parts` 순서를 그대로 litertlm `Contents`로 매핑
  (`Text→Content.Text`, `Image→Content.ImageBytes`, `Audio→Content.AudioBytes`).
  현재 `MultimodalContent.convertToContent()`가 이미 이 매핑을 갖고 있으니 재사용.
- **현재 문제**: `generateWithInput`이 audio > image 우선순위로 한 modality만 선택
  (`LMBridgeClient.kt:66~88`) → 혼합 입력 불가. `parts` 전체를 넘기도록 교체.
- 이미지 인코딩은 PNG 무손실(현재 `toPngBytes`). 대용량 이미지 다운스케일 옵션 고려(성능).

## 6. 청킹(긴 프롬프트) — **재설계**

**현재 문제**(`processChunkedGenerate`, `splitByTokenLimit`):
- 하나의 프롬프트를 토큰 추정치로 쪼개 **여러 개의 개별 메시지로 전송**하고, 그 사이에
  `"Processing N chunks..."`, `"--- Chunk i/N ---"` 문자열을 **토큰 스트림에 삽입**한다.
- 결과적으로 (1) 소비 앱 출력이 마커로 오염되고, (2) 한 프롬프트를 여러 메시지로
  나눠 보내 모델의 문맥 해석이 왜곡될 수 있다.

**목표**:
- 청킹은 **prefill 최적화의 내부 구현**이어야 하며 **출력에 절대 새어나오면 안 된다.**
- 우선순위 낮음: MVP에서는 청킹을 **끄고**(단일 메시지 전송) 명확한 스트림을 보장.
  긴 컨텍스트가 실제 요구가 되면, litertlm의 세션 prefill 능력에 맞춰 마커 없는
  내부 청킹을 재도입한다.
- `estimateTokenCount`(한글/2, 영문/4 휴리스틱)는 내부 유틸로 유지 가능하나 공개 API 아님.

## 7. 도구 호출 (Tool calling)

- LMBridge `Tool` 선언 → 어댑터가 litertlm `ToolProvider`로 변환.
- 모델의 도구 호출은 `GenerationChunk.ToolCall(name, argsJson)`로 **구조화하여 노출**
  (현재는 일반 토큰으로 흘러 소비 앱이 파싱해야 함).

## 8. 스레딩/자원 (재확인)

- 엔진/세션의 모든 호출은 `engineDispatcher`(단일 스레드)에서 직렬화(01 §3).
- `callbackFlow` + `awaitClose`로 콜백↔Flow 브리지, 취소 시 `cancelProcess()` + 세션 정리.
- `release()`는 활성 세션을 모두 닫은 뒤 `engine.close()`.

## 9. 검증(테스트)
- `splitByTokenLimit` 등 순수 로직은 JVM 단위 테스트(현재 존재).
- 어댑터/세션은 `InferenceEngine` 인터페이스에 대한 **가짜 구현(fake)**으로 Facade 로직
  (상태 머신, 취소, 멀티모달 분기)을 계측기 없이 테스트 가능하게 한다.
- 실제 추론은 계측 테스트(`androidTest`)에서 `GEMMA3_1B_IT`로 확인.
