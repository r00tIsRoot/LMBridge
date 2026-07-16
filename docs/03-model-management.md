# 03 · 모델 관리 (Model Management)

모델 카탈로그 · 다운로드 · 무결성 · 저장소를 다룬다.
현재 구현: `download/ModelDownloadManager.kt`, `models/ModelCatalog.kt`.

## 1. 카탈로그: `ModelCatalog`

사전 정의된 `.litertlm` 모델 메타데이터. 현존 유지(값 검증만 필요).

| 상수 | 크기 | 비고 |
|------|------|------|
| `GEMMA_4_E2B_IT` | ~2.5GB | 멀티모달 |
| `GEMMA_4_E4B_IT` | ~3.6GB | 멀티모달 |
| `GEMMA_3N_E2B_IT` | ~3.6GB | int4 |
| `GEMMA_3N_E4B_IT` | ~4.9GB | int4 |
| `GEMMA3_1B_IT` | ~584MB | 경량, 데모 기본 권장 |
| `QWEN2_5_1_5B_INSTRUCT` | ~1.6GB | |
| `DEEPSEEK_R1_DISTILL_QWEN_1_5B` | ~1.8GB | |

### 개선
- `ModelInfo`에 **`sha256` 필드 추가**(무결성 검증용, 현재 없음). HuggingFace의
  LFS `oid sha256`을 커밋 시점에 기록.
- `ModelInfo`에 **모달리티/최소 RAM/권장 백엔드** 메타 추가(선택 UI/사전 검증용).
- 공개 타입 이름을 `ModelRef`(또는 `ModelInfo` 유지)로 하되 **litertlm 비의존**은 이미 만족.

```kotlin
data class ModelInfo(
    val modelId: String,        // "litert-community/Gemma3-1B-IT"
    val modelFile: String,      // "gemma3-1b-it-int4.litertlm"
    val commitHash: String,
    val sizeInBytes: Long = 0L,
    val sha256: String? = null, // ← 추가: 무결성 검증
)
```

## 2. 저장소: `ModelStore` (신설)

**현재 문제 — 저장 위치 이원화**:
- 추론 초기화는 에셋을 `context.filesDir`로 추출(`ModelInferenceManager.extractAssetIfNeeded`).
- 다운로드는 `context.getExternalFilesDir(null)/<dirName>/`에 저장.
- 두 경로가 무관해 "다운로드한 모델"과 "로드하는 모델"이 자동 연결되지 않는다.

**해결 — 단일 `ModelStore`가 경로 소유**:

```kotlin
class ModelStore(context: Context) {
    fun dirFor(model: ModelInfo): File          // 일관된 규칙(현재 toDirName 재사용)
    fun fileFor(model: ModelInfo): File
    fun isPresent(model: ModelInfo): Boolean
    fun pathIfPresent(model: ModelInfo): String?
    fun availableSpace(): Long
    fun delete(model: ModelInfo): Boolean
    fun tempFileFor(model: ModelInfo): File     // 원자적 쓰기용 .part
}
```

- 다운로드·초기화·삭제가 모두 `ModelStore`를 거쳐 **경로 규칙을 공유**.
- 저장 위치는 `getExternalFilesDir` 우선, null이면 `filesDir` 폴백(현재 null 미처리).
- `Builder.model(...)`로 지정한 카탈로그 모델은 `initialize()`가
  `store.pathIfPresent()`로 로컬을 찾고, 없으면 다운로드 후 그 경로를 엔진에 전달.

## 3. 다운로드: `ModelDownloadManager`

현재 `Flow<DownloadStatus>` + 원시 `HttpURLConnection`. 상태 타입은 유지, 내부는 재작성.

```kotlin
sealed class DownloadStatus {
    data object NotStarted
    data class Downloading(val totalBytes: Long, val receivedBytes: Long, val progressPercent: Int)
    data class Verifying                       // ← 추가: 무결성 검증 단계
    data class Failed(val error: LMBridgeError) // ← String 대신 타입화
    data class Completed(val filePath: String)
}
```

### 3.1 이어받기(resume) — **버그 수정 필수**
현재 로직(`ModelDownloadManager.kt:104~138`)은 순서가 잘못됐다:
1. `connection.connect()` → `responseCode`를 먼저 읽고,
2. 그 **다음에** `connection.setRequestProperty("Range", ...)`를 호출한다.
   - `connect()` 이후의 헤더 설정은 **무시**되며, `HTTP_PARTIAL` 응답은 Range를
     보낸 적이 없으므로 나올 수 없다 → **재개가 실제로 동작하지 않는다.**

**목표 절차**:
1. 로컬 `.part` 파일의 기존 바이트 수 `have` 계산.
2. `have > 0`이면 **connect 전에** `Range: bytes=$have-` 설정.
3. 응답이 `206 Partial`이면 이어쓰기(append), `200 OK`면 처음부터(truncate).
4. `416`(범위 불충족)이면 `.part` 삭제 후 재시도.

### 3.2 원자적 쓰기
- 다운로드는 항상 `model.part`(temp)로 받고, **검증 성공 후에만** 최종 파일명으로 rename.
- 현재는 최종 파일에 바로 append하여, 실패 시 손상된 파일이 최종 경로에 남는다.

### 3.3 무결성 검증 — **SHA-256**
- 현재는 **파일 크기 일치만** 확인(`actualSize != totalBytes`). 문서가 주장하는
  SHA-256 검증은 **미구현**.
- 목표: 다운로드 완료 후 `.part`의 SHA-256을 스트리밍 계산하여 `ModelInfo.sha256`과 비교.
  불일치 시 `.part` 삭제 + `DownloadStatus.Failed(IntegrityCheckFailed)`.
- `sha256`이 null인 커스텀 모델은 크기 검증으로 폴백(정책 로그 남김).

### 3.4 백그라운드/정책
- **네트워크 스택**: 현재 `HttpURLConnection`. 재시도/헤더/타임아웃 제어가 쉬운
  **OkHttp**로 교체 권고(메모도 OkHttp 언급). 단, 의존성 최소화 원칙과 저울질.
- **WorkManager**: 대용량(수 GB) 다운로드는 프로세스 종료에도 살아남아야 하므로
  `CoroutineWorker` + `setForeground`(진행 알림)로 감싼 **백그라운드 경로**를 제공.
  `Flow` API는 포그라운드/데모용으로 유지하고, `enqueue()` 방식의 백그라운드 API를 추가.
- **`DownloadPolicy`**: `ANY` / `WIFI_ONLY` / `UNMETERED_ONLY`. WorkManager `Constraints`로 구현.

### 3.5 취소/정리
- 다운로드 `Flow` 취소 시 스트림/커넥션을 확실히 닫는다(현재 `catch`만 있고 취소
  경로에서 스트림 close 보장 약함 — `use`/`try-finally`로 강화).
- `deleteModel`은 `ModelStore.delete`로 위임.

## 4. 커스텀 모델
카탈로그 외 모델은 `ModelInfo`를 직접 구성해 `downloadModel(...)`. `sha256` 지정을 권장,
미지정 시 크기 검증 폴백.

## 5. 검증(테스트)
- 순수 로직(재개 상태 계산, `.part`→최종 rename 결정, 크기/해시 비교)을 **네트워크와
  분리**해 JVM 단위 테스트 가능하게 리팩터링(현재 다운로드 테스트 부재).
- 계측 테스트: 소형 파일(또는 `GEMMA3_1B_IT`)로 재개·무결성 실패 주입 시나리오.
