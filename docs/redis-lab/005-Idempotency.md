# [redis-005] idempotency 

## 실험 질문

Redis SET NX EX를 사용하면 동일한 조회수 증가 요청이 짧은 시간에 중복 처리되는 것을 막을 수 있는가?

## 배경

사용자가 콘텐츠 상세 페이지를 여러 번 새로고침하거나, 네트워크 재시도로 같은 요청이 반복될 수 있다.

이때 같은 콘텐츠 조회수 증가 요청이 중복 처리되면 `view_count`가 실제보다 많이 증가하거나 불필요한 DB 요청이 발생할 수 있다.

## 구현할 것

- POST /contents/{id}/view
- 요청 헤더로 `Idempotency-Key` 받기
- `view:req:{contentId}:{idempotencyKey}` key를 Redis에 SET NX EX로 저장
- 저장 성공 시 `view_count` 증가
- 저장 실패 시 중복 요청으로 판단하고 조회수 증가 생략


## 실험 1. 같은 Idempotency-Key로 두 번 요청하면 조회수가 한 번만 증가하는지 확인

### Idempotency 구현

```java
@Transactional
public boolean viewContent(int contentId, int idempotencyKey) {
    Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    String key = "view:req:" + contentId + ":" + idempotencyKey;

    boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));

    if (!success) return false;

    int cnt = content.getViewCount();
    content.setViewCount(++cnt);

    return true;
}
```

1. 콘텐츠 조회수 증가 요청
2. 요청 헤더에서 `Idempotency-Key` 확인
3. Redis에 `view:req:{contentId}:{idempotencyKey}` key 저장 시도
4. Redis 저장에 성공하면 최초 요청으로 판단
5. DB에서 콘텐츠 조회 후 `view_count` 증가
6. Redis 저장에 실패하면 중복 요청으로 판단
7. 중복 요청인 경우 `view_count` 증가 없이 `false` 반환

### 실행 방법

같은 콘텐츠에 대해 같은 `Idempotency-Key`로 두 번 요청한다.

```http
POST /contents/1/view
Idempotency-Key: 100
```

다시 같은 요청을 보낸다.

```http
POST /contents/1/view
Idempotency-Key: 100
```

### 실행 결과

```text
첫 번째 요청: true
두 번째 요청: false
```

Redis에는 다음 key가 저장된다.

```text
view:req:1:100
```

### 해석

- 첫 번째 요청에서는 Redis에 `view:req:1:100` key가 없기 때문에 저장에 성공했다.
- Redis 저장에 성공한 요청만 최초 요청으로 판단하고 `view_count`를 증가시켰다.
- 두 번째 요청에서는 이미 같은 Redis key가 존재하기 때문에 `SET NX`가 실패했다.
- 실패한 요청은 중복 요청으로 판단되어 `view_count`가 증가하지 않았다.
- 이를 통해 Redis `SET NX EX`를 사용하면 같은 요청을 짧은 시간 동안 한 번만 처리할 수 있음을 확인했다.

## 실험 2. TTL 만료 후 같은 Idempotency-Key로 다시 요청할 수 있는지 확인

### 실행 방법

같은 콘텐츠와 같은 `Idempotency-Key`로 요청한 뒤 Redis key의 TTL을 확인한다.

```bash
TTL view:req:1:100
```

TTL이 만료된 뒤 같은 요청을 다시 보낸다.

```http
POST /contents/1/view
Idempotency-Key: 100
```

### 실행 결과

- Redis에 저장된 `view:req:1:100` key에 TTL이 설정되어 있는 것을 확인했다.
- TTL이 만료되기 전에는 같은 요청을 다시 보내도 `false`가 반환되었다.
- TTL이 만료된 뒤에는 Redis key가 삭제되었다.
- 이후 같은 `Idempotency-Key`로 다시 요청하면 Redis 저장이 다시 성공하고 `view_count`가 증가했다.

### 해석

- `EX` 옵션을 사용하면 중복 요청 방어 key가 Redis에 영구히 남지 않는다.
- TTL이 유지되는 동안에는 같은 요청을 중복으로 처리하지 않는다.
- TTL이 만료되면 Redis key가 사라지기 때문에 같은 요청도 다시 처리될 수 있다.
- 따라서 TTL은 중복 요청을 완전히 막는 장치가 아니라, 특정 시간 동안만 중복 처리를 제한하는 장치이다.
- TTL이 너무 짧으면 새로고침이나 재시도 요청이 다시 조회수 증가로 이어질 수 있다.
- 반대로 TTL이 너무 길면 실제로 다시 처리해도 되는 요청까지 오래 막을 수 있다.

## 깨뜨려본 상황

### 1. Redis 저장은 성공했지만 DB 처리 중 예외가 발생하는 상황

Redis key를 저장한 직후 예외가 발생하도록 코드를 수정했다.

```java
@Transactional
public boolean viewContent(int contentId, int idempotencyKey) {
    Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    String key = "view:req:" + contentId + ":" + idempotencyKey;

    boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));

    //Redis 저장 완료 후 예외가 발생하는 경우 실험
    return false;

//        if (!success) return false;
//
//        int cnt = content.getViewCount();
//        content.setViewCount(++cnt);
//
//        return true;
}
}
```

- Redis에는 `view:req:{contentId}:{idempotencyKey}` key가 저장된다.
- 하지만 예외가 발생하면 DB 트랜잭션은 롤백된다.
- 따라서 `view_count`는 증가하지 않는다.
- 문제는 이후 같은 `Idempotency-Key`로 재시도했을 때 발생한다.
- Redis에는 이미 요청 key가 남아 있기 때문에 재시도 요청은 중복 요청으로 판단된다.
- 결과적으로 실제 DB 변경은 일어나지 않았는데, 이후 재시도 요청도 무시될 수 있다.

이를 통해 Redis 저장과 DB 업데이트가 하나의 트랜잭션으로 묶이지 않는다는 점을 확인했다.

## 이번 실험에서 알게 된 점

- Redis `SET NX EX`를 사용하면 동일한 요청을 일정 시간 동안 한 번만 처리할 수 있다.
- `NX`는 key가 없을 때만 저장하므로 중복 요청을 구분할 수 있고, `EX`는 key에 만료 시간을 설정해 중복 방어 기록이 계속 남는 것을 막는다.
- `view:req:{contentId}:{idempotencyKey}` 형식으로 key를 구성하면 콘텐츠 단위로 조회수 증가 요청의 중복 여부를 판단할 수 있다.
- TTL이 만료되면 같은 key로 다시 요청할 수 있으므로, 이 방식은 완전한 중복 방어가 아니라 시간 제한이 있는 중복 방어이다.
- Redis 저장과 DB 업데이트는 하나의 트랜잭션으로 묶이지 않기 때문에 Redis 저장 후 DB 처리에 실패하면 실제 조회수는 증가하지 않았는데도 재시도 요청이 무시될 수 있다.
- 따라서 조회수처럼 약간의 오차를 허용할 수 있는 값에는 적합하지만, 결제나 주문처럼 정확한 처리가 필요한 작업에는 DB 제약 조건이나 별도 idempotency 저장소가 필요하다.

