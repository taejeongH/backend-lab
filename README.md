# backend-lab

백엔드 개발 과정에서 자주 마주치는 성능, 정합성, 중복 처리, 장애 대응 문제를 작은 실험 단위로 검증하는 레포지토리입니다.

단순히 기술을 사용해보는 데서 끝내지 않고, 각 기술이 어떤 문제를 해결하는지와 어떤 한계를 가지는지 함께 정리하는 것을 목표로 합니다.

## Goals

- 기술을 기능 구현 중심이 아니라 문제 해결 중심으로 학습합니다.
- 각 실험은 작은 단위로 구현하고, 동작 결과와 한계를 문서로 남깁니다.
- Redis, Kafka, Database, Monitoring 등 백엔드에서 자주 사용되는 기술을 직접 실험합니다.
- 면접이나 실무 상황에서 설명 가능한 수준으로 개념과 판단 근거를 정리합니다.

## Lab List

| Lab | Topic | Status |
|---|---|---|
| redis-lab | Content Archive 기반 Redis 캐시, 중복 요청 방어, 랭킹, Rate Limit, 장애 fallback, Pub/Sub, Stream, Lua Script 실험 | 진행 중 |
| kafka-lab | Kafka 메시징, consumer, offset, 재처리, 중복 처리 실험 | 예정 |
|...  |


## Repository Structure

```text
backend-lab
 ├─ README.md
 ├─ docker-compose.yml
 ├─ redis-lab
 │   ├─ README.md
 │   └─ src
 ├─ kafka-lab
 └─ docs
     └─ redis-lab
```

## Experiment Rule

각 실험은 GitHub Issue 단위로 진행합니다.

Issue는 다음 흐름을 따릅니다.

```text
1. 실험 질문 정의
2. 배경 정리
3. 최소 기능 구현
4. 정상 동작 확인
5. 깨지는 상황 재현
6. 한계와 판단 기준 문서화
```

## Issue Template

```md
## 실험 질문

## 배경

## 구현할 것

## 관찰할 것

## 깨뜨려볼 상황

## 정리할 것

## 완료 조건
```

## Done Definition

Issue는 코드 구현만으로 닫지 않습니다.

다음 조건을 모두 만족해야 완료로 봅니다.

- [ ] 코드 구현
- [ ] 로그 또는 테스트로 동작 확인
- [ ] 실패하거나 깨지는 상황 1개 이상 재현
- [ ] 실험 결과 문서 작성
- [ ] 해당 기술을 사용할 때의 장점과 한계 정리

## Commit Convention

```text
feat(redis): implement cache aside
test(redis): add duplicate request test
docs(redis): summarize cache invalidation
refactor(redis): separate cache service
```

## Branch Convention

```text
feature/redis-002-cache-aside
feature/redis-005-idempotency-set-nx
docs/redis-003-cache-invalidation
```