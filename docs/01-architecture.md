# 01 · 아키텍처 (Architecture)

## 1. 레이어 구조

LMBridge는 4개 레이어로 나뉜다. 위 레이어는 아래 레이어에만 의존하고,
**litertlm 의존은 Engine 레이어 안에만** 갇힌다.

```
┌─────────────────────────────────────────────────────────────┐
│  Facade      LMBridgeClient / Builder                         │  ← 소비 앱이 보는 유일한 표면
│              (LMBridge 전역 설정, Logger)                      │
├─────────────────────────────────────────────────────────────┤
│  Domain      GenerationChunk / GenerationResult               │  ← LMBridge 자체 타입
│              MultimodalInput / Message / Tool / LMBridgeError  │     (litertlm 타입 없음)
├─────────────────────────────────────────────────────────────┤
│  Engine      InferenceEngine (interface)                      │  ← 어댑터 경계
│              └ LiteRtEngineAdapter (litertlm 구현)            │     litertlm 타입은 여기서 끝
│                BackendResolver (AUTO→GPU/CPU/NPU 폴백)         │
├─────────────────────────────────────────────────────────────┤
│  Resource    ModelCatalog / ModelDownloadManager / ModelStore │  ← 파일·네트워크
└─────────────────────────────────────────────────────────────┘
        │
        ▼
   com.google.ai.edge.litertlm  (Engine 레이어에서만 import)
```

### 의존성 규칙 (강제)
- `litertlm.*` import는 **Engine 레이어 파일에서만** 허용한다.
  (검증: CI에서 `grep -rl 'com.google.ai.edge.litertlm' lmbridge/src/main` 결과가
  `inference/` 어댑터 파일들로만 한정되는지 확인)
- Domain 타입은 안드로이드 프레임워크 타입 중 `Bitmap`(멀티모달) 정도만 참조.
- Facade는 Domain·Engine 인터페이스에만 의존, 구현체는 내부에서 생성.

## 2. 목표 모듈 구조

```
LMBridge/
├── lmbridge/          (com.android.library, namespace com.isroot.lmbridge)  ← SDK 본체
│   └── src/main/java/com/isroot/lmbridge/
│       ├── LMBridge.kt              (전역 설정: 로그레벨, Logger)
│       ├── LMBridgeClient.kt        (Facade + Builder)
│       ├── model/                   (Domain 타입: GenerationChunk, Message, Tool, LMBridgeError, MultimodalInput)
│       ├── inference/               (Engine: InferenceEngine, LiteRtEngineAdapter, BackendResolver)
│       └── download/                (Resource: ModelCatalog, ModelDownloadManager, ModelStore)
├── demo/              (com.android.application) ← Compose 데모 앱 (신설 예정)
└── docs/              ← 정본 설계 문서
```

### 정리 대상
- **`shared/` KMP 모듈 제거** — `settings.gradle.kts`에 포함되지 않고 소스도 없이
  stale 빌드 산출물만 남아 혼란을 준다(`AGENTS.md`의 KMP 기술도 사실과 불일치).
  KMP가 실제 목표가 아니라면 삭제하고, 목표라면 `settings.gradle.kts`에 정식 포함 후
  common 소스를 채운다. **현 단계 권고: 제거.**
- `models/` → `model/`로 명명 통일(문서·코드 일관).

## 3. 스레딩 모델

LiteRT-LM `Engine`/`Conversation`은 스레드-세이프하지 않다고 가정한다.

- 엔진 생성·초기화·모든 `sendMessage`/`cancel`/`close`는 **단일 전용 디스패처**에서 실행.
  - `Dispatchers.Default.limitedParallelism(1)` 또는 전용 `newSingleThreadContext`로
    `engineDispatcher`를 만들어 `LiteRtEngineAdapter`가 소유.
- 공개 API의 `suspend`/`Flow`는 호출 스레드에 무관하게 동작하고, 내부에서
  `withContext(engineDispatcher)`로 진입.
- **현재 코드 문제**: `initialize()`는 `Dispatchers.IO`, 각 generate는 `callbackFlow`가
  임의 스레드에서 콜백을 받아 엔진 접근이 직렬화되지 않는다. 동시 `generate` 호출 시
  세션/엔진 상태가 손상될 수 있다. → 단일 디스패처 직렬화로 교체.

## 4. 생명주기 & 상태 머신

`LMBridgeClient`는 명시적 상태를 갖고 잘못된 전이를 막는다.

```
        build()
          │
          ▼
     ┌─────────┐  initialize()  ┌──────────┐  generate()   ┌───────────┐
     │  IDLE   │ ─────────────▶ │  READY   │ ─────────────▶│ GENERATING│
     └─────────┘                └──────────┘ ◀───────────── └───────────┘
          ▲                          │        done/stop/error     │
          │        release()         │                            │
          └──────────────────────────┴────────────────────────────┘
```

- `initialize()`는 **멱등**: 이미 READY면 무시(또는 재설정 옵션). 현재는 중복 호출 시
  엔진을 새로 만들어 이전 엔진이 누수된다 → 가드 추가.
- `release()` 후 재사용 금지(호출 시 명확한 `LMBridgeError.NotInitialized`).
- 동시 generate 정책: **직렬화 큐** 또는 **명시적 거부**(권고: 1개 활성, 추가 요청 거부).

## 5. 데이터 흐름 (텍스트 스트리밍 예)

```
앱: client.generate("안녕")           Facade
      └▶ InferenceEngine.generate(prompt): Flow<GenerationChunk>   Engine 인터페이스
            └▶ LiteRtEngineAdapter (engineDispatcher에서)
                  ├ conversation.sendMessageAsync(Contents.of(prompt), callback)
                  ├ onMessage(m) → emit GenerationChunk.Token(m.text())   ← toString() 아님
                  ├ onDone()     → emit GenerationChunk.Done; close
                  └ onError(t)   → emit GenerationChunk.Error(map(t)); close
            (Flow 취소/앱 스코프 취소 → awaitClose → conversation.cancelProcess())
```

## 6. 오류 전파

- Engine 레이어가 던지는 litertlm 예외/throwable을 **Domain `LMBridgeError`로 매핑**한
  뒤에만 상위로 전달(02-public-api §오류 모델).
- 스트림 내부 오류는 `GenerationChunk.Error`로, 초기화/설정 오류는 `suspend` 함수의
  예외(`LMBridgeError`)로 전달하는 이원 정책을 명시한다.

## 7. 네이티브 라이브러리

- GPU(OpenCL)·샘플러 `.so`는 `AndroidManifest.xml`에 `<uses-native-library
  required="false">`로 선언됨(현존). `required=false`이므로 미지원 기기에서도 앱 설치는
  가능하고, 런타임에 BackendResolver가 폴백한다.
- `.litertlm` 에셋은 압축 금지(`androidResources { noCompress += "litertlm" }`, 현존).
