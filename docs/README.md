# LMBridge 설계 문서 (정본)

LMBridge는 Google **LiteRT-LM**(`com.google.ai.edge.litertlm`)을 래핑하여, 안드로이드에서
온디바이스 LLM을 **쉽고 견고하게** 붙일 수 있도록 하는 Android SDK/라이브러리입니다.
모델 **다운로드 → 초기화 → (멀티모달) 추론 → 스트리밍 응답**까지의 전체 파이프라인을
관용적 Kotlin API로 추상화합니다.

> 이 `docs/` 디렉터리가 **정본(single source of truth)**입니다.
> 저장소 루트의 `README.md`(사용자용 사용법)와 `AGENTS.md`(에이전트용 요약)는
> 이 문서를 요약/참조하는 파생 문서입니다. 내용이 충돌하면 **이 문서가 우선**합니다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [00-overview.md](00-overview.md) | 프로젝트 목표, 범위, 현재 상태 요약, 설계 원칙 |
| [01-architecture.md](01-architecture.md) | 4-레이어 아키텍처, 모듈 구조, 스레딩/생명주기 모델 |
| [02-public-api.md](02-public-api.md) | 공개 API 표면(목표), 타입 경계, 오류 모델 |
| [03-model-management.md](03-model-management.md) | 카탈로그·다운로드·무결성·저장소(ModelStore) 설계 |
| [04-inference-engine.md](04-inference-engine.md) | 엔진 어댑터, 백엔드 폴백, 대화 세션, 취소/멀티모달 |
| [05-roadmap.md](05-roadmap.md) | 마일스톤(M0~M4)과 릴리스 기준 |
| [GAP-ANALYSIS.md](GAP-ANALYSIS.md) | **현재 코드 vs 목표 설계의 격차 및 실제 버그 목록** (개선 착수점) |

## 어디부터 읽을까

- **왜 손대야 하나 / 무엇이 깨졌나** → [GAP-ANALYSIS.md](GAP-ANALYSIS.md)
- **무엇을 만드나 / 원칙** → [00-overview.md](00-overview.md)
- **어떻게 만드나** → [01-architecture.md](01-architecture.md) → 03/04
- **언제 무엇을 낸다** → [05-roadmap.md](05-roadmap.md)

## 한 줄 요약 (현재 판정)

기능(텍스트/이미지/오디오/도구/다운로드)의 **골격은 동작 가능한 수준**으로 존재하나,
(1) 공개 API가 litertlm 타입에 결합되어 **버전 독립성 목표가 깨져 있고**,
(2) 취소·다운로드 이어받기·무결성 검증 등 **견고성 기능에 실제 결함**이 있으며,
(3) 문서/버전/모듈 구성이 **서로 불일치**합니다.
→ 본 문서는 이 격차를 닫기 위한 **목표 설계와 단계적 개선 경로**를 정의합니다.
