# 00 · 개요 (Overview)

## 1. 목적

안드로이드 앱 개발자가 **온디바이스 LLM을 몇 줄로** 붙일 수 있게 한다.
개발자가 직접 다루면 번거롭고 실수하기 쉬운 부분을 SDK가 흡수한다:

- 모델 **다운로드**(대용량, 재개, 무결성, 백그라운드)
- LiteRT-LM **엔진 초기화**(백엔드 선택/폴백, 네이티브 라이브러리 로드)
- **추론**(텍스트/멀티모달/도구호출)과 **스트리밍**(토큰 단위)
- **생명주기/자원 관리**(스레드 안전, 취소, 해제)

참고: [LiteRT-LM Android 공식 가이드](https://developers.google.com/edge/litert-lm/android)

## 2. 범위 (Scope)

### 포함 (In)
- LiteRT-LM(`com.google.ai.edge.litertlm`) 위의 **얇지만 견고한** 래퍼
- HuggingFace(`litert-community` 등)에서의 `.litertlm` 모델 다운로드/관리
- CPU / GPU(OpenCL) / NPU 백엔드 선택과 **AUTO 폴백**
- Kotlin `Flow` 기반 스트리밍, 상태 유지 대화(conversation) 세션
- 멀티모달 입력(텍스트·이미지·오디오), 도구 호출(tool calling)

### 제외 (Out)
- UI 컴포넌트(Compose/Coil/CameraX 등) — **소비 앱에 강제하지 않는다**
- 서버/클라우드 추론, 모델 파인튜닝/양자화
- litertlm이 지원하지 않는 모델 포맷

## 3. 핵심 설계 원칙

1. **어댑터 경계(Adapter boundary)** — litertlm 타입(`Engine`, `Conversation`,
   `Content`, `ToolProvider` 등)을 **공개 API로 노출하지 않는다.** 엔진 교체/버전
   업그레이드가 소비 앱을 깨지 않도록 LMBridge 자체 타입으로 감싼다.
   → 현재 이 원칙이 **깨져 있음**(02-public-api, GAP-ANALYSIS 참조). 최우선 복원 대상.
2. **UI 무의존** — 라이브러리는 안드로이드 프레임워크 최소 의존만 갖는다(Context, Bitmap).
3. **단일 스레드 엔진 접근** — LiteRT-LM 엔진은 스레드-세이프하지 않다고 가정하고,
   모든 엔진 호출을 **전용 단일 디스패처**로 직렬화한다.
4. **견고성 우선** — 다운로드 재개·무결성 검증·취소·자원 해제가 "동작한다고 문서에
   적혀 있는" 수준이 아니라 **실제로 동작**해야 한다.
5. **관용적 Kotlin** — `suspend` + `Flow`, 봉인 클래스(sealed) 결과 타입, Builder DSL.
6. **점진적 개방** — 기본은 초간단, 필요 시 세부 설정(백엔드, 토큰 수, 다운로드 정책)을
   열어준다.

## 4. 현재 상태 요약 (2026-07 기준)

| 항목 | 목표 설계(메모) | 실제 구현 | 판정 |
|------|----------------|-----------|------|
| 모듈 구성 | `:lmbridge` + `:demo` + `docs/` | `:lmbridge`만, 고아 `shared/` 잔재 | ⚠️ 불일치 |
| litertlm 버전 | 0.14.0 | build 0.11.0 / README 0.10.0 | ⚠️ 불일치 |
| 공개 API 경계 | litertlm 타입 비노출 | `Conversation`/`ToolProvider` 노출 | ❌ 위배 |
| 백엔드 | CPU/GPU/NPU + AUTO 폴백 | CPU/GPU/NPU (AUTO·폴백 없음) | ⚠️ 미완 |
| 스트리밍 | `Flow<GenerationChunk>` | `Flow<GenerationResult>` (유사) | ✅ 유사 |
| 취소 | 진행 중 생성 중단 | `stopGeneration()` **no-op 버그** | ❌ 결함 |
| 다운로드 재개 | 이어받기 | Range 헤더 배치 오류로 **미동작** | ❌ 결함 |
| 무결성 | SHA-256 검증 | 파일 크기만 확인 | ⚠️ 미완 |
| 백그라운드 다운로드 | WorkManager/OkHttp | 원시 `HttpURLConnection` | ⚠️ 미완 |
| 모델 저장소 | 통합 `ModelStore` | init=filesDir / download=externalFilesDir 이원화 | ⚠️ 불일치 |
| 오류 모델 | 타입화된 예외 | `Error(message:String)` 단일 | ⚠️ 미완 |
| 문서 | `docs/` 정본 | 부재(본 문서로 신설) | ✅ 해결 중 |

> 상세 근거와 파일·라인 위치는 [GAP-ANALYSIS.md](GAP-ANALYSIS.md).

## 5. 타깃 환경

- Android **API 26+**(Oreo), compileSdk **36**
- Kotlin **2.2.0**, JVM target **17**
- AGP 8.8.x (루트 `build.gradle.kts` 기준; 메모의 AGP 9.1은 목표치)
- 코어: `com.google.ai.edge.litertlm:litertlm-android` — **버전 단일화 필요**
- GPU(OpenCL) 사용을 위해 `AndroidManifest.xml`에
  `libOpenCL.so`, `libvndksupport.so`, LiteRt 샘플러 `.so`를
  `<uses-native-library required="false">`로 선언(이미 존재).

## 6. 성공 기준 (Definition of Done, MVP)

- 개발자가 `Builder → initialize() → generate().collect{}`만으로 텍스트 추론 성공
- 카탈로그 모델을 **재개·무결성 검증**과 함께 다운로드 성공
- 생성 중 `stop()` 호출 시 **실제로** 스트림이 즉시 종료
- 공개 API 시그니처에 litertlm 패키지 타입이 **하나도** 등장하지 않음
- 데모 앱에서 위 시나리오를 눈으로 확인 가능
