# redis-lab

Redis를 단순 캐시 저장소로만 사용하지 않고, **인메모리 데이터 구조 서버**로 이해하기 위한 실험 모듈입니다.

이 모듈에서는 영화, 책, 음악, 웹툰 같은 개인 취향 콘텐츠를 기록하는 `Content Archive` 도메인을 바탕으로 Redis의 Cache Aside, TTL, 캐시 무효화, 중복 요청 방어, Sorted Set, Rate Limit, 장애 fallback, Pub/Sub, Stream, Lua Script를 작은 이슈 단위로 실험합니다.

각 실험은 Redis가 해결할 수 있는 문제뿐 아니라, Redis만으로 해결하기 어려운 한계까지 함께 정리하는 것을 목표로 합니다.

---

## Goals

- Redis의 기본 key-value 동작과 TTL을 이해합니다.
- Cache Aside 패턴을 구현하고 stale cache 문제를 확인합니다.
- 캐시 무효화 시점과 DB 트랜잭션 commit 시점을 비교합니다.
- SET NX EX를 활용해 중복 요청 방어를 실험합니다.
- Redis 기반 방어와 DB unique constraint의 역할 차이를 비교합니다.
- Sorted Set으로 인기 콘텐츠 랭킹과 최근 본 콘텐츠 목록을 구현합니다.
- INCR/EXPIRE와 Sorted Set을 활용해 Rate Limit을 구현합니다.
- Redis 장애 시 cache fallback 전략을 실험합니다.
- Pub/Sub과 Stream의 차이를 실험합니다.
- Lua Script로 여러 Redis 명령을 원자적으로 처리하는 방식을 실험합니다.

---

## Domain

이 실험에서는 영화, 책, 음악, 웹툰 등 개인의 취향 콘텐츠를 기록하는 `Content Archive` 도메인을 사용합니다.

콘텐츠 상세 조회, 찜하기, 조회수 랭킹, 최근 본 콘텐츠, 검색 요청 제한, 새 콘텐츠 등록 알림, 콘텐츠 이벤트 로그 같은 기능을 통해 Redis의 다양한 사용 방식을 실험합니다.

---

## Main Entity

### Content

```text
Content
- id
- type: MOVIE | BOOK | MUSIC | WEBTOON
- title
- creator
- description
- releaseYear
- viewCount
- createdAt
- updatedAt
```

### Favorite

```text
Favorite
- id
- userId
- contentId
- createdAt

unique(userId, contentId)
```

---

## Redis Key Naming Rule

```text
content:{contentId}
content:view:ranking
user:{userId}:recent-contents
favorite:req:{idempotencyKey}
rate:user:{userId}:content-search:{minute}
rate:user:{userId}:content-search
channel:content:new
stream:content-events
```

---

## Issue List

| Issue | Topic | Question | Priority | Status |
|---|---|---|---|---|
| redis-000 | Basic & TTL | Redis key와 TTL은 어떻게 동작하는가 | 필수 | Todo |
| redis-001 | Spring Redis | Spring Boot에서 Redis를 어떻게 다루는가 | 필수 | Todo |
| redis-002 | Cache Aside | 콘텐츠 상세 조회에서 DB 접근을 줄일 수 있는가 | 필수 | Todo |
| redis-003 | Cache Invalidation | 콘텐츠 수정/삭제 시 캐시는 언제 지워야 안전한가 | 필수 | Todo |
| redis-004 | TTL Strategy | TTL은 stale cache 문제를 어떻게 완화하는가 | 필수 | Todo |
| redis-005 | Idempotency | SET NX EX로 찜하기 중복 요청을 막을 수 있는가 | 필수 | Todo |
| redis-006 | Redis vs DB Constraint | Redis와 DB unique constraint는 역할이 어떻게 다른가 | 필수 | Todo |
| redis-007 | Ranking | Sorted Set으로 인기 콘텐츠 랭킹을 만들 수 있는가 | 권장 | Todo |
| redis-008 | Recent View | Sorted Set으로 최근 본 콘텐츠 목록을 만들 수 있는가 | 권장 | Todo |
| redis-009 | Fixed Window Rate Limit | INCR/EXPIRE로 검색 요청 제한을 만들 수 있는가 | 권장 | Todo |
| redis-010 | Sliding Window Rate Limit | Sorted Set과 Lua로 더 정교한 요청 제한을 만들 수 있는가 | 권장 | Todo |
| redis-011 | Fallback | Redis 장애 시 서비스는 어떻게 동작해야 하는가 | 권장 | Todo |
| redis-012 | Persistence | Redis 재시작 후 데이터는 어떻게 되는가 | 선택 | Todo |
| redis-013 | Pub/Sub | Redis Pub/Sub은 새 콘텐츠 알림에 적합한가 | 선택 | Todo |
| redis-014 | Stream | Redis Stream은 콘텐츠 이벤트 로그처럼 쓸 수 있는가 | 선택 | Todo |
| redis-015 | Lua Script | Lua script로 여러 Redis 명령을 원자적으로 처리할 수 있는가 | 선택 | Todo |

---

## Milestones

### 1. Redis Basic

Redis 기본 동작과 Spring Boot 연동을 확인합니다.

- redis-000
- redis-001

### 2. Cache & Consistency

콘텐츠 상세 조회 캐시, 캐시 무효화, TTL 전략을 실험합니다.

- redis-002
- redis-003
- redis-004

### 3. Idempotency

콘텐츠 찜하기 요청을 기준으로 중복 요청 방어와 최종 정합성 보장 방식을 비교합니다.

- redis-005
- redis-006

### 4. Redis Data Structures

Sorted Set을 활용해 인기 콘텐츠 랭킹과 최근 본 콘텐츠 목록을 구현합니다.

- redis-007
- redis-008

### 5. Rate Limit & Operation

Redis를 활용한 요청 제한과 장애 상황에서의 fallback 전략을 실험합니다.

- redis-009
- redis-010
- redis-011

### 6. Messaging & Programmability

Redis의 Pub/Sub, Stream, Lua Script를 실험합니다.

- redis-012
- redis-013
- redis-014
- redis-015

---

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

---

## Done Definition

각 Issue는 코드 구현만으로 닫지 않습니다.

다음 조건을 모두 만족해야 완료로 봅니다.

- [ ] 코드 구현
- [ ] 로그 또는 테스트로 동작 확인
- [ ] 실패하거나 깨지는 상황 1개 이상 재현
- [ ] 실험 결과 문서 작성
- [ ] Redis를 사용할 때의 장점과 한계 정리

---

## Docs

각 실험 결과는 `docs/redis-lab` 하위에 정리합니다.

| File | Content |
|---|---|
| docs/redis-lab/000-basic-ttl.md | Redis key, TTL 기본 동작 |
| docs/redis-lab/001-spring-redis.md | Spring Boot Redis 연동 |
| docs/redis-lab/002-cache-aside.md | Content 상세 조회 Cache Aside 실험 |
| docs/redis-lab/003-cache-invalidation.md | Content 수정/삭제 시 캐시 무효화 전략 |
| docs/redis-lab/004-ttl-strategy.md | TTL 전략 |
| docs/redis-lab/005-idempotency-set-nx-ex.md | SET NX EX 중복 요청 방어 |
| docs/redis-lab/006-redis-vs-db-constraint.md | Redis와 DB unique constraint 역할 비교 |
| docs/redis-lab/007-ranking-sorted-set.md | Sorted Set 인기 콘텐츠 랭킹 |
| docs/redis-lab/008-recent-view-sorted-set.md | 사용자별 최근 본 콘텐츠 목록 |
| docs/redis-lab/009-fixed-window-rate-limit.md | Fixed Window Rate Limit |
| docs/redis-lab/010-sliding-window-rate-limit.md | Sliding Window Rate Limit |
| docs/redis-lab/011-redis-fallback.md | Redis 장애 fallback |
| docs/redis-lab/012-redis-persistence.md | Redis persistence 실험 |
| docs/redis-lab/013-pubsub.md | Redis Pub/Sub 실험 |
| docs/redis-lab/014-stream.md | Redis Stream 실험 |
| docs/redis-lab/015-lua-script.md | Lua Script 실험 |

