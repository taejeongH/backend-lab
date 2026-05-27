# [redis-003] Cache Invalidation

## 실험 질문

콘텐츠 수정/삭제 시 캐시는 DB 수정 전, 수정 후, commit 이후 중 언제 지우는 것이 안전한가?

## 배경

캐시를 적용하면 조회 성능은 좋아질 수 있지만, 원본 DB 데이터가 변경되었을 때 캐시가 함께 갱신되지 않으면 이전 데이터가 조회될 수 있다.

콘텐츠 제목, 설명, 제작자 정보 등이 수정되었을 때 Redis 캐시를 어떻게 무효화해야 하는지 실험한다.

## 구현할 것

- PUT /contents/{id}
- DELETE /contents/{id}
- 수정/삭제 시 `content:{contentId}` 캐시 삭제
- 캐시 삭제를 하지 않는 버전과 하는 버전 비교
- `TransactionSynchronization`의 `afterCommit` 방식 실험

## 실험 1. 캐시 삭제를 하지 않았을 때 stale cache가 발생하는가

### 실험 내용

1. 콘텐츠 상세 조회로 `content:{contentId}` 캐시를 저장한다.
2. 콘텐츠 수정/삭제 요청을 보낸다.
3. 캐시를 삭제하지 않은 상태에서 같은 콘텐츠를 다시 조회한다.

### 실험 결과

- DB에는 수정된 데이터가 반영되었지만 Redis에는 이전 데이터가 남아 있었다.
- 이후 조회 요청은 DB가 아니라 Redis 값을 반환했기 때문에 수정 전 데이터가 조회되었다.
- TTL이 만료되기 전까지 stale cache가 유지될 수 있음을 확인했다.


## 실험 2. 트랜잭션 내부에서 캐시를 삭제하면 어떤 문제가 생길 수 있는가
### 구현 
```java
@Transactional
public ContentResponseDto updateContent(int contentId, ContentRequestDto request) {
    ...
    
    //트랜잭션 내부에서 캐시 삭제
    stringRedisTemplate.delete("content:" + contentId);

    ...
```

### 문제 상황 1. Redis 캐시 삭제 완료 후 롤백되는 경우
#### 상황
```text
1. 캐시 삭제
2. (예외 발생) DB 트랜잭션 롤백
```
- Redis 삭제는 DB 트랜잭션에 포함되지 않기 때문에 rollback되지 않는다.
- DB에는 기존 데이터가 그대로 남고, Redis 캐시만 삭제된다.
- 이 경우 다음 조회에서 DB의 기존 데이터를 다시 캐시에 저장하게 되므로 데이터 정합성 문제는 크지 않다.
- 다만 불필요한 캐시 삭제와 cache miss가 발생한다.

### 문제 상황 2. Redis 캐시 삭제 완료 후 조회되는 경우
#### 상황
```text
1. 캐시 삭제
2. (아직 DB 커밋 전) 다른 조회 요청 발생
3. (캐시 miss) DB에서 이전 데이터 조회 -> 이전 데이터를 Redis에 다시 저장
4. 원래 트랜잭션 커밋
```
- 캐시는 삭제되었지만, DB commit 전에 다른 조회가 들어오면 이전 데이터가 다시 캐시에 저장될 수 있다.
- 이후 DB는 최신 데이터로 commit되지만 Redis에는 이전 데이터가 남는다.
- 이 경우 stale cache가 다시 만들어질 수 있으므로 문제가 된다.

## 실험 3. commit 이후 캐시를 삭제하면 어떤 차이가 있는가

### 구현
```java
@Transactional
public ContentResponseDto updateContent(int contentId, ContentRequestDto request) {
    ...

    //트랜잭션이 종료된 이후(커밋 후) 레디스 캐시 삭제
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            stringRedisTemplate.delete("content:"+contentId);
        }
    });

    ...
}
```

### 문제 상황 1. Redis에 장애가 발생하는 경우
```text
1. 데이터 변경 후 커밋 완료
2. Redis 서버 장애 혹은 네트워크 오류 발생
```
- DB에는 최신 데이터가 반영되었지만 Redis에는 이전 캐시가 남을 수 있다.
- `afterCommit`은 DB rollback 문제는 줄여주지만, Redis 삭제 실패까지 해결해주지는 못한다.
- 이 경우 TTL, retry, Outbox Pattern 같은 보완책이 필요하다.


## 깨뜨려본 상황

### 1. 콘텐츠 삭제 후 최근 본 목록/랭킹 캐시에 id가 남는 경우
콘텐츠 상세 캐시인 `content:{contentId}`를 삭제하더라도, 해당 콘텐츠 id가 다른 캐시에 남아 있을 수 있다.

예를 들어 최근 본 목록이나 랭킹 캐시에 콘텐츠 id만 저장되어 있다면, 콘텐츠 삭제 후에도 목록에는 삭제된 콘텐츠 id가 남을 수 있다.

```text
recent:contents:user:{userId}
ranking:contents
```

이 경우 상세 조회에서는 존재하지 않는 콘텐츠로 처리되더라도, 목록 조회에서는 삭제된 콘텐츠 id가 계속 노출될 수 있다.

따라서 콘텐츠 삭제 시에는 단일 상세 캐시뿐 아니라, 해당 콘텐츠를 참조하는 연관 캐시도 함께 고려해야 한다.


## 이번 실험에서 알게 된 점
Redis 캐시와 Database의 데이터 일관성을 유지하려면, 캐시를 언제 삭제할지 신중하게 결정해야 한다.

캐시 삭제를 DB 트랜잭션 내부에서 수행하더라도 Redis 작업은 DB 트랜잭션에 포함되지 않는다. 따라서 DB 트랜잭션이 rollback되더라도 이미 수행된 Redis 삭제는 되돌아가지 않는다. 또한 commit 전에 다른 조회 요청이 들어오면 이전 DB 데이터가 다시 캐시에 저장될 수 있어 stale cache가 발생할 수 있다.

캐시 삭제를 트랜잭션 commit 이후에 수행하면 DB 변경이 성공한 뒤 캐시를 삭제할 수 있다는 장점이 있다. 하지만 commit 이후 Redis 장애나 네트워크 오류가 발생하면 캐시 삭제에 실패할 수 있고, 이 경우에도 이전 캐시가 남아 데이터 일관성이 깨질 수 있다.

결국 `afterCommit` 방식은 DB rollback 상황에서는 더 안전하지만, Redis 삭제 실패까지 완전히 해결해주지는 못한다. 따라서 캐시 TTL을 설정해 오래된 데이터가 영구히 남지 않도록 하고, 더 높은 신뢰성이 필요하다면 retry 로직이나 Outbox Pattern 같은 방식을 함께 고려해야 한다.