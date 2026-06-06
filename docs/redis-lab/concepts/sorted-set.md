## Sorted Set

### 개념

Redis Sorted Set은 중복되지 않는 `member`와 각 member에 연결된 `score`를 함께 저장하는 자료구조다.

일반 Set처럼 member는 중복될 수 없지만, 각 member가 score를 가지고 있다는 점이 다르다. Redis는 이 score를 기준으로 member를 정렬해서 저장한다.

### 특징

- member는 중복 저장되지 않는다.
- 각 member는 하나의 score를 가진다.
- score는 정렬 기준으로 사용된다.
- score가 낮은 순서 또는 높은 순서로 범위 조회할 수 있다.
- score가 같은 경우 member의 사전순으로 정렬된다.
- member의 score는 언제든지 변경할 수 있다.
- score가 변경되면 Sorted Set 안에서 member의 위치도 함께 바뀐다.

### 주요 명령어

- `ZADD` : Sorted Set에 member와 score를 추가하거나 기존 member의 score를 갱신
- `ZINCRBY` : 기존 score에 값을 더해 score 증가 또는 감소
- `ZRANGE` : score가 낮은 순서로 범위 조회
- `ZRANGE REV` : score가 높은 순서로 범위 조회
- `ZRANK` : score 오름차순 기준 member의 순위 조회
- `ZREVRANK` : score 내림차순 기준 member의 순위 조회
- `ZSCORE` : 특정 member의 score 조회
- `ZCARD` : Sorted Set에 저장된 member 개수 조회
- `ZREM` : Sorted Set에서 특정 member 제거

### 랭킹에 적합한 이유

Sorted Set은 score 기준 정렬을 기본으로 제공하기 때문에 랭킹 구현에 적합하다.

예를 들어 콘텐츠 조회수를 score로 사용하고, 콘텐츠 id를 member로 저장하면 조회수 기반 인기 콘텐츠 랭킹을 쉽게 만들 수 있다.

```text
key    = content:view:ranking
member = contentId
score  = viewCount
```

조회수가 증가할 때는 `ZINCRBY`로 score를 증가시키고, 랭킹을 조회할 때는 `ZRANGE REV`로 score가 높은 순서의 member를 가져오면 된다.

```redis
ZINCRBY content:view:ranking 1 3
```

위 명령어는 `content:view:ranking` Sorted Set에서 member `3`의 score를 1 증가시킨다.

```redis
ZRANGE content:view:ranking 0 9 REV WITHSCORES
```

위 명령어는 score가 높은 순서대로 상위 10개 member와 score를 조회한다.

### 주의할 점

- Sorted Set의 member에는 객체 전체보다 변하지 않는 식별자를 저장하는 것이 좋다.
- 객체를 JSON 문자열로 저장하면 제목이나 설명이 바뀌었을 때 다른 member로 인식될 수 있다.
- Redis 랭킹 데이터는 Redis key가 삭제되면 사라진다.
- DB의 viewCount와 Redis의 score는 서로 다른 저장소에 있기 때문에 실패 상황에서 값이 달라질 수 있다.

### 정리

Sorted Set은 member와 score를 함께 저장하고 score 기준 정렬 조회를 제공하는 Redis 자료구조다.

조회수, 점수, 우선순위처럼 숫자 기준으로 정렬해야 하는 데이터에 적합하며, 인기 콘텐츠 랭킹이나 게임 리더보드처럼 상위 N개를 자주 조회하는 기능에 사용할 수 있다.
