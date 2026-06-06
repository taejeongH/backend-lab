# [redis-007] ranking

## 실험 질문

Redis Sorted Set을 사용하면 인기 콘텐츠 랭킹을 쉽게 만들 수 있는가?

## 배경

인기 영화, 인기 책, 인기 음악처럼 랭킹은 특정 점수 기준으로 정렬된 데이터를 자주 조회해야 한다.

DB에서 매번 정렬 조회를 수행할 수도 있지만, Redis Sorted Set을 사용하면 score 기반 정렬 데이터를 빠르게 관리할 수 있다.

## 구현할 것

- POST /contents/{id}/view
- GET /contents/ranking
- `content:view:ranking` Sorted Set 생성
- 조회 시 ZINCRBY로 score 증가
- 랭킹 조회 시 상위 10개 반환

## 실험 1. ZINCRBY로 조회수 랭킹 score 증가

### 구현

```java
// 해당 member(contentId)의 score를 1 증가
stringRedisTemplate.opsForZSet()
        .incrementScore(RANK_KEY, String.valueOf(contentId), 1);
```

### 실행 방법

같은 콘텐츠에 대해 조회수 증가 요청을 여러 번 보낸다.

```http
POST /contents/1/view
Idempotency-Key: 101
```

```http
POST /contents/1/view
Idempotency-Key: 102
```

Redis에서 Sorted Set 데이터를 확인한다.

```bash
ZRANGE content:view:ranking 0 -1 WITHSCORES
```

### 실행 결과

```text
1
2
```

콘텐츠 id `1`이 member로 저장되고, 조회 요청 횟수만큼 score가 증가했다.

### 해석

- Redis Sorted Set에는 `contentId`가 member로 저장된다.
- 조회 요청이 발생할 때마다 해당 member의 score가 1씩 증가한다.
- member가 존재하지 않아도 `ZINCRBY`를 사용하면 자동으로 member가 추가된다.
- 조회수처럼 계속 증가하는 값은 `ZADD`로 score를 덮어쓰기보다 `ZINCRBY`로 증가시키는 방식이 더 자연스럽다.

## 실험 2. ZRANGE REV로 상위 랭킹 조회

### 구현

```java
// score가 높은 순서대로 10개 조회
Set<String> contentIdSet = stringRedisTemplate.opsForZSet()
        .reverseRange(RANK_KEY, 0, 9);
```

### 실행 방법

여러 콘텐츠의 조회수를 다르게 증가시킨 뒤 랭킹 조회 API를 호출한다.

```http
GET /contents/ranking
```

Redis에서도 직접 확인한다.

```bash
ZRANGE content:view:ranking 0 9 REV WITHSCORES
```

### 실행 결과

```text
[
    {
        "id": 10,
        "type": "BOOK",
        "title": "불편한 편의점",
        "creator": "김호연",
        "description": "편의점을 배경으로 사람들의 사연을 따뜻하게 풀어낸 소설",
        "releaseYear": "2021",
        "viewCount": 2
    },
    {
        "id": 9,
        "type": "BOOK",
        "title": "어린 왕자",
        "creator": "생텍쥐페리",
        "description": "순수함과 관계의 의미를 담은 우화적 소설",
        "releaseYear": "1943",
        "viewCount": 5
    },
    {
        "id": 8,
        "type": "BOOK",
        "title": "아몬드",
        "creator": "손원평",
        "description": "감정을 잘 느끼지 못하는 소년의 성장 이야기",
        "releaseYear": "2017",
        "viewCount": 3
    },
    ...
]
```

### 해석

- `range()`를 사용하면 score가 낮은 순서로 조회된다.
- 인기 콘텐츠 랭킹은 조회수가 높은 콘텐츠가 먼저 나와야 하므로 `reverseRange()`를 사용해야 한다.
- `0, 9`는 0번 인덱스부터 9번 인덱스까지 조회하므로 최대 10개를 가져온다.
- Redis에 저장된 member가 10개보다 적으면 존재하는 개수만 반환된다.
- Redis에서 조회한 값은 contentId이므로 응답에 제목, 타입, 설명을 포함하려면 DB 조회를 거쳐야 한다.

### 랭킹 응답 구성

```java
public List<ContentResponseDto> getRanking(String contentType) {
    String key = RANK_KEY + contentType;

    //score가 높은 순대로 조회
    Set<String> contentIdSet = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9);

    if (contentIdSet == null || contentIdSet.isEmpty()) {
        return List.of();
    }

    List<Integer> contentIds = contentIdSet.stream()
            .map(Integer::valueOf)
            .toList();

    //score가 높은 순으로 저장된 contentIds를 DB를 통해 조회
    List<Content> contents = contentRepository.findAllById(contentIds);

    Map<Integer, Content> contentMap = contents.stream()
            .collect(Collectors.toMap(Content::getId, Function.identity()));

    return contentIds.stream()
            .map(contentMap::get)
            .filter(Objects::nonNull)
            .map(ContentResponseDto::from)
            .toList();
}
```

Redis Sorted Set에는 콘텐츠 전체 객체를 저장하지 않고 `contentId`만 저장했다.

객체 전체를 member로 저장하면 title, description 같은 값이 변경될 때 같은 콘텐츠가 다른 member로 인식될 수 있다. 따라서 member에는 변하지 않는 식별자인 `contentId`를 저장하고, 실제 응답 데이터는 DB에서 조회해 조립하는 방식이 더 안전하다.

또한 `findAllById()`는 Redis 랭킹 순서를 그대로 보장하지 않을 수 있다. 그래서 DB 조회 결과를 `Map`으로 바꾼 뒤 Redis에서 가져온 `contentId` 순서대로 응답을 다시 만들어야 한다.

## 실험 3. Type별 랭킹 조회

### 구현

전체 랭킹과 타입별 랭킹을 서로 다른 Redis key로 분리했다.

```text
content:view:ranking
content:view:ranking:{type}
```

조회수 증가 시 전체 랭킹과 타입별 랭킹 score를 함께 증가시킨다.

```java
//해당 member(contentId)의 score를 1늘림
stringRedisTemplate.opsForZSet().incrementScore(RANK_KEY, String.valueOf(contentId), 1);

//type별 랭킹
stringRedisTemplate.opsForZSet().incrementScore(RANK_KEY + content.getType(), String.valueOf(contentId), 1);
```

예를 들어 `type`이 `MOVIE`인 콘텐츠를 조회하면 다음 두 key에 모두 반영된다.

```text
content:view:ranking
content:view:ranking:MOVIE
```

### 실행 방법

MOVIE, BOOK, MUSIC 타입의 콘텐츠를 각각 조회한 뒤 타입별 랭킹 API를 호출한다.

```http
GET /contents/ranking?type=MOVIE
```

Redis에서도 타입별 key를 확인한다.

```bash
ZRANGE content:view:ranking:MOVIE 0 9 REV WITHSCORES
```

### 실행 결과

```text
[
    {
        "id": 5,
        "type": "MOVIE",
        "title": "다크 나이트",
        "creator": "크리스토퍼 놀란",
        "description": "배트맨과 조커의 대립을 그린 히어로 영화",
        "releaseYear": "2008",
        "viewCount": 3
    },
    {
        "id": 4,
        "type": "MOVIE",
        "title": "라라랜드",
        "creator": "데이미언 셔젤",
        "description": "꿈과 사랑 사이에서 갈등하는 두 사람의 이야기",
        "releaseYear": "2016",
        "viewCount": 2
    },
    {
        "id": 3,
        "type": "MOVIE",
        "title": "올드보이",
        "creator": "박찬욱",
        "description": "15년간 감금된 남자의 복수를 다룬 스릴러 영화",
        "releaseYear": "2003",
        "viewCount": 1
    },
    ...
]
```

MOVIE 타입 콘텐츠만 타입별 랭킹에 포함되었다.

전체 랭킹을 조회하면 타입과 관계없이 모든 콘텐츠가 조회되고, 타입별 랭킹을 조회하면 해당 타입의 콘텐츠만 조회되었다.

### 해석

- 전체 랭킹과 타입별 랭킹은 같은 Sorted Set에서 필터링하는 방식보다 key를 분리하는 방식이 단순하다.
- 조회수 증가 시 두 key의 score를 함께 증가시키면 전체 랭킹과 타입별 랭킹을 동시에 관리할 수 있다.

## 깨뜨려본 상황

### 1. Redis 랭킹 key를 삭제하면 랭킹 데이터가 사라지는 상황

Redis에서 랭킹 key를 삭제했다.

```bash
DEL content:view:ranking
```

삭제 후 랭킹을 다시 조회하면 빈 결과가 반환된다.

```bash
ZRANGE content:view:ranking 0 9 REV WITHSCORES
```

DB의 `viewCount`는 그대로 남아 있지만 Redis Sorted Set에 저장된 랭킹 데이터는 사라졌다.

### 해석

- Redis 랭킹 데이터는 Redis key에 저장되어 있으므로 key를 삭제하면 즉시 유실된다.
- DB의 `viewCount`와 Redis의 ranking score는 별개의 데이터이다.
- Redis 데이터를 삭제해도 DB의 조회수는 삭제되지 않는다.
- 반대로 DB에 조회수가 남아 있어도 Redis 랭킹은 자동으로 복구되지 않는다.
- Redis 랭킹을 캐시처럼 사용할 경우 DB의 `viewCount`를 기준으로 다시 적재하는 복구 로직이 필요하다.

### 2. DB viewCount와 Redis score가 달라지는 상황

조회수 증가 로직에서는 DB의 `viewCount` 증가와 Redis score 증가가 함께 수행된다.

하지만 Redis와 DB는 하나의 트랜잭션으로 묶이지 않는다.

DB 업데이트가 성공한 뒤 Redis 반영에 실패하면 DB의 `viewCount`는 증가했지만 Redis 랭킹 score는 증가하지 않을 수 있다.

반대로 Redis score 증가 후 DB 처리 중 예외가 발생하면 Redis score는 증가했지만 DB의 `viewCount`는 롤백될 수 있다.

### 해석

- DB와 Redis는 서로 다른 저장소이므로 한쪽만 반영되는 상황이 생길 수 있다.
- 조회수처럼 약간의 오차를 허용할 수 있는 값은 Redis 기준 랭킹으로 처리할 수 있다.
- 정확한 조회수 원본이 필요하다면 DB를 기준으로 두고 Redis는 랭킹 조회용 캐시로 사용하는 것이 안전하다.
- Redis 랭킹을 원본처럼 사용할 경우 주기적인 DB 반영이나 복구 전략이 필요하다.

## 이번 실험에서 알게 된 점

- Redis Sorted Set은 member와 score를 함께 저장하고 score 기준 정렬 조회를 제공하기 때문에 랭킹 구현에 적합하다.
- 조회수처럼 계속 증가하는 점수는 `ZADD`보다 `ZINCRBY`를 사용하는 것이 자연스럽다.
- 랭킹 member에는 객체 전체보다 변하지 않는 식별자인 `contentId`를 저장하는 것이 안전하다.
- Redis에서 가져온 contentId 순서와 DB 조회 결과 순서가 다를 수 있으므로 응답을 만들 때 Redis 순서를 기준으로 다시 조립해야 한다.
- 전체 랭킹과 타입별 랭킹은 key를 분리하면 단순하게 관리할 수 있다.
- Redis 랭킹 데이터는 삭제되면 사라지므로 DB 기준 복구가 필요한지 판단해야 한다.
- DB의 `viewCount`와 Redis의 score는 실패 상황에 따라 달라질 수 있으므로 두 값의 역할을 명확히 나눠야 한다.
