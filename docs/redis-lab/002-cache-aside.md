# [redis-002] Cache Aside

## 실험 질문
반복 조회되는 콘텐츠 상세 API에서 Redis Cache Aside 패턴을 적용하면 DB 접근을 줄일 수 있는가?

## 배경

영화, 책, 음악, 웹툰 같은 콘텐츠 상세 정보는 여러 사용자가 반복해서 조회할 수 있다.

매번 DB를 조회하면 불필요한 DB 접근이 발생할 수 있으므로 Redis 캐시를 적용해 반복 조회 시 DB 부하를 줄일 수 있는지 확인한다.

## 구현할 것

- Content 엔티티 생성
- POST /contents
- GET /contents/{id}
- `content:{contentId}` key로 상세 조회 결과 캐싱
- 캐시 miss 시 DB 조회 후 Redis 저장
- TTL 5분 적용
- cache hit/miss 로그 출력

## 실험 1. Cache Aside 패턴 적용 후 조회 흐름 확인

### Cache Aside 구현
```java
public ContentResponseDto getContent(int contentId) {
    String key = "content:" + contentId;
    String cachedValue = stringRedisTemplate.opsForValue().get(key);

    if (cachedValue != null) {
        log.info("[CACHE HIT] key = " + key);
        try {
            return objectMapper.readValue(cachedValue, ContentResponseDto.class);
        } catch(Exception e) {
            throw new RuntimeException("Redis 캐시 역직렬화 실패", e);
        }
    }


    //캐시 MISS시 DB에서 조회하여 레디스에 저장
    log.info("[CACHE MISS] key = " + key);
    Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    ContentResponseDto response = ContentResponseDto.from(content);

    try {
        String jsonValue = objectMapper.writeValueAsString(response);
        stringRedisTemplate.opsForValue().set(key, jsonValue, Duration.ofMinutes(5));
    } catch (Exception e) {
        throw new RuntimeException("Redis 캐시 직렬화 실패", e);
    }
    return response;
}
```
1. 콘텐츠 상세 조회 요청
2. Redis에서 content:{contentId} 조회
3. 캐시가 있으면 Redis 값을 역직렬화해서 반환
4. 캐시가 없으면 DB에서 조회
5. DB 조회 결과를 JSON 문자열로 직렬화
6. Redis에 TTL과 함께 저장
7. 응답 반환

### 실행 결과
```text
[CACHE MISS] key=content:{contentId} //첫번째 조회
[CACHE HIT] key=content:{contentId} //두번째 조회
```

### 해석
- 첫 번째 조회에서는 Redis에 데이터가 없기 때문에 cache miss가 발생했다.
- cache miss 이후 DB에서 콘텐츠를 조회하고, 조회 결과를 Redis에 저장했다.
- 두 번째 조회에서는 Redis에 저장된 캐시 데이터를 사용했기 때문에 DB 조회 없이 응답할 수 있었다.
- 이를 통해 반복 조회되는 데이터에 Cache Aside 패턴을 적용하면 DB 접근을 줄일 수 있음을 확인했다.

## 실험 2. TTL 만료 후 다시 DB 조회가 발생하는지 확인

### 실행 방법

```bash
TTL content:{contentId}
```

### 실행 결과

- Redis에 저장된 `content:{contentId}` key에 TTL이 설정되어 있는 것을 확인했다.
- TTL이 만료된 뒤 같은 콘텐츠를 다시 조회하면 Redis key가 사라져 cache miss가 발생했다.
- 이후 DB에서 다시 콘텐츠를 조회하고 Redis에 새로 캐싱되었다.

### 해석

- TTL을 설정하면 캐시 데이터가 Redis에 계속 남아 있지 않고 일정 시간이 지나면 자동으로 삭제된다.
- 오래된 캐시가 무한히 유지되는 것을 막을 수 있다.
- 다만 TTL이 만료되기 전까지는 이전 캐시 값이 조회될 수 있으므로, 데이터 변경이 잦은 경우에는 TTL만으로는 부족할 수 있다.

## 깨뜨려본 상황

### 1. DB의 콘텐츠 제목이나 설명을 직접 변경했을 때 Redis에 남은 이전 값이 조회되는지 확인한다

- 콘텐츠 상세 조회 후 Redis에 `content:{contentId}` 캐시가 저장된 상태에서 DB 값을 직접 수정했다.
- 다시 상세 조회를 호출했을 때 DB의 최신 값이 아니라 Redis에 남아 있던 이전 값이 조회되었다.
- 이를 통해 Cache Aside 패턴에서는 DB와 Redis가 자동으로 동기화되지 않는다는 점을 확인했다.
- 즉, DB 원본 데이터가 변경되어도 Redis 캐시가 갱신되지 않으면 stale cache가 발생할 수 있다.
- 실제 수정 API를 구현한다면 DB 수정 후 `content:{contentId}` 캐시를 삭제하는 처리가 필요하다.


## 이번 실험에서 알게 된 점

- Cache Aside 패턴은 캐시에 데이터가 없을 때만 DB를 조회하고, 조회 결과를 Redis에 저장하는 방식이다.
- 첫 조회는 cache miss로 DB를 조회하지만, 이후 동일 조회는 cache hit로 Redis에서 응답할 수 있다.
- 캐시는 DB 원본 데이터가 아니라 조회 성능을 높이기 위한 임시 데이터이다.
- DB 데이터가 변경되어도 Redis 캐시는 자동으로 갱신되지 않기 때문에 stale cache가 발생할 수 있다.
- 따라서 Cache Aside 패턴은 자주 변경되지 않고 반복 조회가 많은 데이터에 적합하다.
- stale cache를 줄이기 위해서는 TTL 설정이나 데이터 수정 시 캐시 삭제 전략이 필요하다.