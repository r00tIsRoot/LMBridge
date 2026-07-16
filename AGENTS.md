# AGENTS.md - LMBridge

On-device LLM inference Android SDK using Google LiteRT-LM.

> 정본 설계 문서는 `docs/`에 있습니다(`docs/README.md` 인덱스). 이 파일은 요약입니다.

## Architecture & Structure
- **Project Root**: `/Users/ljw/projects/LMBridge`
- **`lmbridge/`**: 유일한 라이브러리 모듈(`com.android.library`, namespace `com.isroot.lmbridge`).
- 4-레이어: Facade(`LMBridgeClient`) → Domain(`models/`) → Engine(`inference/`, 어댑터 경계)
  → Resource(`download/`).
- **litertlm 타입은 `inference/LiteRtEngineAdapter.kt` 한 파일에만** import된다(버전 독립성).
- `shared/`는 사용하지 않는 stale 빌드 산출물(무시). settings.gradle에 포함되지 않음.

## Tech Stack & Constraints
- **Kotlin**: 2.2.0 / JVM 17
- **Gradle**: 8.10.2+ (AGP 8.8.x)
- **Android API**: 26+ (Oreo), compileSdk 36
- **Core Engine**: `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
- **Model Format**: `.litertlm` files.

## Key Components
- `LMBridgeClient` / `Builder`: 진입점(Facade). 상태머신 + 멱등 `initialize()`.
- `models/`: 공개 도메인 타입 — `GenerationChunk`, `LMBridgeError`, `Chat`/`ChatConfig`,
  `Tool`, `MultimodalInput`.
- `inference/`: `InferenceEngine`/`EngineSession` 인터페이스 + `LiteRtEngineAdapter`(litertlm 구현).
- `download/`: `ModelDownloadManager`(재개·SHA-256), `ModelStore`(경로 소유), `ModelCatalog`.

## Development & Verification
- **Unit tests** (`src/test`, 기기 불필요): `./gradlew :lmbridge:testDebugUnitTest`
- **Instrumented tests** (`src/androidTest`, 기기/모델 필요): `./gradlew :lmbridge:connectedAndroidTest`
- **Compile check**: `./gradlew :lmbridge:compileDebugKotlin`
- **어댑터 경계 검증**: `grep -rl com.google.ai.edge.litertlm lmbridge/src/main` →
  `LiteRtEngineAdapter.kt`(+InferenceEngine.kt 주석)만 나와야 함.
