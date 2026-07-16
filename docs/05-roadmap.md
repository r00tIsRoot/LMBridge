# 05 · 로드맵 (Roadmap)

[GAP-ANALYSIS.md](GAP-ANALYSIS.md)의 개선 항목을 마일스톤으로 배치한다.
각 마일스톤은 **독립적으로 릴리스 가능**하고, 완료 기준(Exit)을 만족해야 다음으로 넘어간다.

---

## M0 — 정본화 & 위생 (현재)
**목표**: 문서/버전/모듈을 일치시켜 개선의 기준선을 만든다.

- [x] `docs/` 정본 문서 세트 신설(본 문서들)
- [ ] litertlm 버전 단일화 (H1) — `libs.versions.toml` 도입 검토
- [ ] SDK 버저닝 규칙(SemVer) 확정, 태그/publish/README 동기화 (H2)
- [ ] 고아 `shared/` 모듈 제거 + `AGENTS.md` 정정 (H3)
- [ ] `README.md`/`AGENTS.md`를 `docs/` 참조로 축약

**Exit**: 버전·모듈·문서가 서로 모순되지 않는다.

---

## M1 — 견고성 & 경계 (핵심 결함 제거) ✅ 구현 완료
**목표**: "동작한다"고 적힌 것이 실제로 동작하게. 어댑터 경계 복원.

- [x] **G1** 취소 실동작 — 세션이 `Conversation` 참조를 보관하고 `stop()`이 `cancelProcess()` 호출
- [x] **G5** 어댑터 경계 — `InferenceEngine`/`LiteRtEngineAdapter`, 공개 API에서 litertlm 제거(`Chat`/`Tool`/`GenerationChunk`)
- [x] **G4** `initialize()` 멱등 + 상태 머신(IDLE/READY/RELEASED)
- [x] **G6** 토큰 텍스트 정식 추출(`message.contents`에서 `Content.Text` 추출)
- [x] **G7** 청킹 마커 출력 오염 제거(청킹 비활성, 단일 메시지)
- [x] **G10** `LMBridgeError` sealed 계층 도입
- [x] `@Deprecated`로 기존 시그니처 한 사이클 유지(litertlm 노출 메서드는 제거)

**Exit 충족**: 공개 시그니처에 litertlm 타입 0개(검증 명령: AGENTS.md).
`stop()`이 실제 `cancelProcess()`로 연결. 중복 초기화는 멱등.

---

## M2 — 모델 관리 & 데모 (대부분 구현 완료)
**목표**: 대용량 모델을 신뢰성 있게 받고, 눈으로 검증.

- [x] **G2** 다운로드 재개(Range를 connect 이전 설정 / `.part` / 원자적 rename)
- [x] **G3** SHA-256 무결성 검증(`ModelInfo.sha256`, `ModelStore.sha256Of`, `Verifying` 상태)
- [x] **G9/G12** `ModelStore` 도입(경로 단일화, external→internal 폴백)
- [x] **G11** 멀티모달 혼합 입력(`parts` 순서 보존, `Document` 파트 추가)
- [ ] **:demo** Compose 데모 앱 추가(다운로드→초기화→스트리밍→중지 시연) — 미착수
- [ ] `GEMMA3_1B_IT` 실기기 계측 검증(재개/무결성 실패 주입) — 미착수

**남은 것**: 데모 앱과 실기기 검증. 순수 로직(URL/디렉터리)은 JVM 단위 테스트로 검증됨.

---

## M3 — 백엔드 & 성능
**목표**: 기기 편차를 흡수하고 성능 옵션 제공.

- [ ] **G8** `Backend.AUTO` + `BackendResolver`(NPU→GPU→CPU 폴백)
- [ ] **G13** WorkManager 백그라운드 다운로드 + `DownloadPolicy`(WiFi/Unmetered)
- [ ] 네트워크 스택 OkHttp 전환 검토
- [ ] `GenerationStats`(선택 백엔드, 토큰/초 등) 노출
- [ ] 도구 호출 구조화(`GenerationChunk.ToolCall`)

**Exit**: GPU 미지원 기기에서 자동으로 CPU 폴백. WiFi 전용 백그라운드 다운로드 성공.

---

## M4 — 안정화 & 배포
**목표**: 공개 배포 품질.

- [ ] `InferenceEngine` fake 기반 JVM 단위 테스트 확대 (H5)
- [ ] 다운로드 순수 로직 단위 테스트(재개/검증)
- [ ] API 문서(KDoc) + 사용 가이드 정리
- [ ] ProGuard/consumer-rules 검증(공개 API 보존)
- [ ] Maven publish 파이프라인 안정화(GitHub Pages 저장소)

**Exit**: 문서화된 공개 API가 SemVer로 배포되고, 핵심 로직이 CI에서 테스트된다.

---

## 호환/버저닝 정책

- **SemVer** 준수. 어댑터 경계 복원(M1)은 공개 API를 바꾸므로 **메이저 범프** 대상.
- 제거 예정 API는 최소 한 마이너 사이클 `@Deprecated(ReplaceWith)` 유지 후 제거.
- litertlm 버전 업그레이드는 어댑터 내부에 갇히므로 **소비 앱 API에 영향 없음**(목표 상태).

## 리스크

| 리스크 | 완화 |
|--------|------|
| litertlm API가 버전 간 변동 | 어댑터 격리(M1)로 파급 최소화 |
| 대용량 모델 테스트 비용/시간 | `GEMMA3_1B_IT`(584MB)를 CI/데모 기본으로 |
| GPU/NPU 기기 편차 | AUTO 폴백 + `required=false` 네이티브 선언 |
| KMP 방향성 미확정(`shared/`) | M0에서 제거로 결정, 필요 시 별도 스파이크 |
