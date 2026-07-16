# 격차 분석 (Gap Analysis) · 현재 코드 vs 목표 설계

> **상태 업데이트 (2026-07)**: 아래 결함/위배 대부분이 **구현으로 해소됨**.
> - ✅ 해결: G1, G4, G5, G6, G7(비활성), G10, G2, G3, G9, G11, G12 및 H1(버전 0.11.0 통일 문서화), H4(docs 신설), H5(부분: JVM 단위테스트 도입)
> - ⏳ 남음: G8(AUTO 폴백, M3), G13(WorkManager/OkHttp, M3), H2(버전 넘버 확정), H3(shared/ 물리 삭제 — 문서만 정정), :demo 앱
> 아래 항목은 **당시 진단 기록**으로 보존한다(파일:라인은 리팩터링 전 기준).

작성 기준: `lmbridge/src/main` (2026-07). 각 항목에 **근거(파일:라인)**, **영향**,
**권고**를 명시한다. 우선순위: 🔴 결함(버그) · 🟠 원칙 위배 · 🟡 미완/부채.

---

## 🔴 결함 (실제로 잘못 동작)

### G1. `stopGeneration()`이 no-op
- 근거: `LMBridgeClient.kt:98` → `inferenceManager.stopGeneration()` 호출 시 인자
  없음 → `ModelInferenceManager.kt:221` `fun stopGeneration(conversation: Conversation? = null)`
  → 본문 `conversation?.cancelProcess()`. **null이므로 아무 것도 취소되지 않는다.**
- 영향: 사용자가 "중지"를 눌러도 생성이 계속된다.
- 권고: 활성 세션의 `Conversation`을 보관하고 그 세션을 취소(04 §4.2).

### G2. 다운로드 이어받기(resume)가 동작하지 않음
- 근거: `ModelDownloadManager.kt:102~138`. `connection.connect()`와 `responseCode`
  판독 **이후에** `setRequestProperty("Range", ...)`(라인 132) 호출 → connect 이후
  헤더 설정은 무시. 또한 `supportsResume`가 `HTTP_PARTIAL` 응답을 요구하는데(라인 129)
  Range를 보낸 적이 없어 206이 올 수 없다.
- 영향: 대용량(수 GB) 다운로드가 중단되면 매번 처음부터. 데이터/시간 낭비.
- 권고: connect **전에** Range 설정, `.part` 임시파일 + 원자적 rename(03 §3.1~3.2).

### G3. 무결성 검증이 크기 비교뿐 (SHA-256 미구현)
- 근거: `ModelDownloadManager.kt:176~180` — `actualSize != totalBytes`만 확인.
  `ModelInfo`에 `sha256` 필드 없음. README/메모는 SHA-256 검증을 **주장**.
- 영향: 손상·변조·부분 파일이 "완료"로 통과해 엔진 로드 실패로 이어질 수 있다.
- 권고: `ModelInfo.sha256` 추가 + 스트리밍 해시 검증(03 §3.3).

### G4. 초기화 비멱등 → 엔진 누수
- 근거: `ModelInferenceManager.kt:41~63` `initialize()`가 무조건 새 `Engine` 생성.
  중복 호출 시 이전 `engine`이 close 없이 덮어써진다.
- 영향: 네이티브 메모리 누수, 잠재적 크래시.
- 권고: 상태 가드(IDLE/READY) 후 멱등 처리(01 §4).

---

## 🟠 설계 원칙 위배

### G5. 어댑터 경계 붕괴 — litertlm 타입이 공개 API에 노출
- 근거: `LMBridgeClient.kt`
  - `newConversation(...): com.google.ai.edge.litertlm.Conversation` (라인 29)
  - `generateWithConversation(conversation: Conversation, ...)` (라인 36)
  - `newConversation(tools: List<ToolProvider>)`, `generateWithTools(tools: List<ToolProvider>)`
- 영향: litertlm 버전 업/엔진 교체가 **소비 앱을 소스·바이너리 비호환으로 깨뜨린다.**
  "버전 독립성"이라는 핵심 설계 목표가 무너져 있다.
- 권고: `Chat` 세션 타입 + LMBridge `Tool`로 대체(02 §3~4, 04 §1).

### G6. 토큰 텍스트를 `message.toString()`으로 생성
- 근거: `ModelInferenceManager.kt:119,148,178,205` 모든 콜백이
  `GenerationResult.Token(message.toString())`.
- 영향: `Message`의 디버그 문자열이 사용자 출력으로 샐 수 있다(정확한 텍스트 추출 아님).
- 권고: `Message`의 정식 텍스트 접근 API로 교체(02 §5).

### G7. 청킹 마커가 출력 스트림 오염
- 근거: `ModelInferenceManager.kt:302,304` — `"Processing N chunks..."`,
  `"--- Chunk i/N ---"`를 토큰으로 `trySend`.
- 영향: 소비 앱 화면에 내부 진행 마커가 그대로 표시된다.
- 권고: 청킹은 내부 구현으로 격리, MVP에선 비활성(04 §6).

---

## 🟡 미완 / 기술 부채

### G8. `Backend.AUTO` 및 GPU→CPU 폴백 부재
- 근거: `LMBridge.kt:15~19` 열거형에 AUTO 없음, `convertToLiteRtBackend` 1:1 변환.
- 권고: `BackendResolver` 도입(04 §3).

### G9. 저장 위치 이원화 (init=filesDir, download=externalFilesDir)
- 근거: `ModelInferenceManager.kt:65~75`(filesDir 에셋 추출) vs
  `ModelDownloadManager.kt:120`(externalFilesDir). 서로 연결 안 됨.
- 권고: `ModelStore`로 경로 규칙 단일화(03 §2).

### G10. 오류 모델이 문자열 단일
- 근거: `GenerationResult.Error(message: String)`, `DownloadStatus.Failed(message: String)`.
- 권고: `LMBridgeError` sealed 계층(02 §6).

### G11. 멀티모달 혼합 입력 불가
- 근거: `LMBridgeClient.kt:66~88` audio > image 우선순위로 한 modality만 처리.
- 권고: `parts` 순서 보존 매핑(04 §5).

### G12. `getExternalFilesDir(null)` null 미처리
- 근거: `ModelDownloadManager.kt:120,197,209,238` — null 가능성 무시 또는 0 반환.
- 권고: `filesDir` 폴백(03 §2).

### G13. 원시 HttpURLConnection / WorkManager·정책 부재
- 근거: `ModelDownloadManager` 전체가 `HttpURLConnection` 기반, 백그라운드/WiFi 정책 없음.
- 권고: OkHttp 교체 검토 + `CoroutineWorker` 백그라운드 경로 + `DownloadPolicy`(03 §3.4).

---

## 프로젝트 위생 (Hygiene) 불일치

### H1. litertlm 버전 3중 불일치
- `lmbridge/build.gradle.kts` → `0.11.0`, `README.md` → `0.10.0`, 메모 → `0.14.0`.
- 권고: 단일 버전 확정 후 전 문서 동기화. 가능하면 `gradle/libs.versions.toml` 도입.

### H2. SDK 버전 표기 혼란
- publish version `0.0.47`(build.gradle) vs git tag `v1.0.1` vs README `0.0.11`.
- 권고: 단일 버저닝 규칙(SemVer) + 태그/publish/README 동기화.

### H3. 고아 KMP `shared/` 모듈
- `settings.gradle.kts`는 `:lmbridge`만 include. `shared/`엔 소스 없이 stale 빌드
  산출물만. `AGENTS.md`는 "KMP shared module for common logic"라 기술(사실과 불일치).
- 권고: 제거(권장) 또는 정식 편입 후 소스 작성. `AGENTS.md` 정정.

### H4. `demo`/`docs` 부재
- 메모는 `:demo` 앱과 `docs/`(00~05)를 정본이라 지칭하나 저장소에 없었음.
- 권고: `docs/`는 본 세트로 신설(완료). `:demo`는 M2에서 추가(05-roadmap).

### H5. 테스트가 대부분 계측(device 필요)
- 근거: `androidTest/LMBridgeClientTest.kt`는 실제 엔진/모델 필요. 순수 로직 단위 테스트는
  `splitByTokenLimit`뿐.
- 권고: `InferenceEngine` fake + 다운로드 순수 로직 분리로 JVM 테스트 확대(04 §9, 03 §5).

---

## 개선 착수 순서 (요약)

1. **G1, G5** — 취소 버그 + 어댑터 경계(가장 파급 큰 결함/원칙).
2. **G2, G3, G4** — 다운로드 재개·무결성·초기화 멱등(견고성).
3. **G8~G12** — 폴백/저장소/오류모델/멀티모달(기능 완성도).
4. **H1~H5** — 버전·모듈·문서·테스트 위생.

상세 일정은 [05-roadmap.md](05-roadmap.md).
