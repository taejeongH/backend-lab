# [redis-005] compare-redis-and-db-contraint

## 실험 질문

콘텐츠 찜하기 중복을 막을 때 Redis와 DB unique constraint는 각각 어떤 역할을 하는가?

## 배경

Redis SET NX EX는 빠르게 중복 요청을 차단할 수 있지만, 최종 데이터 정합성은 DB에 저장되는 데이터 기준으로 보장되어야 한다.

Favorite 테이블에 `unique(userId, contentId)`를 두고 Redis 기반 사전 차단과 DB 기반 최종 방어의 역할 차이를 비교한다.

## 구현할 것

- favorites 테이블 생성
- `unique(user_id, content_id)` 제약 조건 추가
- Redis 없이 찜하기 중복 요청 실험
- DB unique constraint로 중복 저장이 막히는지 확인
- Redis SET NX EX를 추가한 뒤 애플리케이션 앞단에서 중복 요청 차단

## 실험 1. Redis 없이 DB unique constraint만으로 중복 저장을 막는지 확인

### 구현 코드

```java
@Transactional
public void favoriteContent(int contentId, int userId) {
    contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    Favorite favorite = new Favorite(contentId, userId);
    favoriteRepository.save(favorite);
}
```

### 실행 방법

같은 사용자와 같은 콘텐츠로 찜하기 요청을 두 번 보낸다.

```http
POST /contents/1/favorite
User-Id: 1
```

동일한 요청을 다시 보낸다.

```http
POST /contents/1/favorite
User-Id: 1
```

### 실행 결과

- 첫 번째 요청은 정상 저장되었다.
- 두 번째 요청은 DB unique constraint에 의해 저장되지 않았다.
- `favorites` 테이블에는 같은 `user_id`, `content_id` 조합의 데이터가 하나만 남았다.

### 해석

Redis가 없어도 DB unique constraint가 있으면 최종 중복 저장은 막을 수 있다.

다만 중복 요청이 들어올 때마다 애플리케이션은 DB 저장을 시도하고, DB에서 제약 조건 위반 예외가 발생한다.
즉, 정합성은 DB가 보장하지만 중복 요청을 미리 차단하지는 않는다.

## 실험 2. Redis를 추가해 애플리케이션 앞단에서 중복 요청을 차단하는지 확인

### 구현 코드

```java
@Transactional
public boolean favoriteContent(int contentId, int userId) {
    Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    String key = "favorite:req:" + contentId + ":" + userId;
    Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));


    if (!Boolean.TRUE.equals(success)) {
        return false;
    }

    try {
        Favorite favorite = new Favorite(contentId, userId);
        favoriteRepository.save(favorite);
        return true;
    } catch (DataIntegrityViolationException e) {
        return false;
    }
}
```

### 실행 방법

같은 사용자와 같은 콘텐츠로 찜하기 요청을 두 번 보낸다.

```http
POST /contents/1/favorite
User-Id: 1
```

동일한 요청을 다시 보낸다.

```http
POST /contents/1/favorite
User-Id: 1
```

### 실행 결과

```text
첫 번째 요청: true
두 번째 요청: false
```

Redis에는 다음 key가 저장된다.

```text
favorite:req:1:1
```

### 해석

첫 번째 요청에서는 Redis에 key가 없기 때문에 `SET NX EX`가 성공했다.
이후 DB에 찜하기 데이터가 저장되었다.

두 번째 요청에서는 이미 같은 Redis key가 존재하기 때문에 `SET NX`가 실패했다.
따라서 DB 저장을 시도하기 전에 중복 요청으로 판단하고 종료되었다.

Redis를 사용하면 중복 요청을 DB까지 보내지 않고 애플리케이션 앞단에서 빠르게 차단할 수 있다.

## 깨뜨려본 상황

### 1. Redis TTL이 만료된 후 같은 요청이 다시 들어오는 상황

Redis key가 만료되면 같은 `userId`, `contentId` 요청도 Redis에서는 새로운 요청처럼 처리된다.

하지만 DB에는 이미 찜하기 데이터가 존재하므로 unique constraint에 의해 중복 저장이 막힌다.

즉, Redis TTL이 만료되어도 DB 제약 조건이 최종 방어선으로 동작한다.

### 2. Redis가 꺼진 상태에서 같은 찜하기 요청이 들어오는 상황

Redis를 사용할 수 없는 상태에서는 Redis 기반 사전 차단이 동작하지 않는다.

이 경우 애플리케이션이 Redis 오류를 어떻게 처리하느냐에 따라 결과가 달라진다.

- Redis 오류를 그대로 던지면 요청은 DB까지 가지 못하고 실패한다.
- Redis 오류 시 DB 저장을 계속 시도하도록 처리하면 DB unique constraint가 최종 중복 저장을 막는다.

따라서 Redis 장애 상황에서도 찜하기 기능을 유지하려면 Redis 오류를 보조 기능 실패로 보고 DB 저장까지 진행하는 fallback 처리가 필요하다.

## 이번 실험에서 알게 된 점

- Redis를 사용하더라도 최종 데이터 정합성은 DB에서 보장해야 한다.
- Redis `SET NX EX`는 중복 요청을 애플리케이션 앞단에서 빠르게 차단하는 보조 수단으로 사용할 수 있다.
- Redis를 사용하면 불필요한 DB 접근과 unique constraint 예외 발생을 줄일 수 있다.
- 하지만 Redis key는 TTL 만료, Redis 장애, DB 처리 실패 상황의 영향을 받기 때문에 최종 방어선이 될 수 없다.
- 찜하기처럼 한 사용자당 한 번만 가능해야 하는 기능은 DB `unique(user_id, content_id)` 제약 조건으로 최종 중복 저장을 막아야 한다.
- Redis key에는 TTL을 설정하는 것이 좋다. DB 처리 실패 후 Redis key만 남는 상황과 불필요한 메모리 사용을 줄일 수 있기 때문이다.
- Redis와 DB unique constraint는 대체 관계가 아니라 보완 관계이다.
