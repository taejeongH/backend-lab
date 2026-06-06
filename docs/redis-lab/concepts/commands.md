# Redis 명령어

## SET

### Syntax
> SET key value [NX | XX] [GET] [EX seconds | PX milliseconds | EXAT unix-time-seconds | PXAT unix-time-milliseconds | KEEPTTL]
- 키에 string 값을 저장한다.
- 키에 이미 값이 있는 경우 덮어쓴다.
- 키와 연관된 이전의 TTL값은 SET 연산이 성공하면 삭제된다.

### Options
- `EX seconds` : 지정된 만료 시간을 초 단위(양의 정수)로 설정
- `PX milliseconds` : 지정된 만료 시간을 밀리초(양의 정수) 단위로 설정
- `EXAT timestamp-seconds` : 키가 만료되는 지정된 유닉스 시간을 초 단위(양의 정수)로 설정
- `PXAT timestamp-milliseconds` : 키가 만료되는 지정된 유닉스 시간을 밀리초(양의 정수) 단위로 설정
- `NX` : 키가 이미 존재하지 않는 경우에만 값 설정
- `XX` : 키가 이미 존재하는 경우에만 값 설정
- `KEEPTTL` : 키와 연관된 생존 시간을 유지
- `GET` : 키에 저장된 이전 문자열을 반환하거나, 키가 존재하지 않으면 `(nil)`을 반환. 키에 저장된 값이 문자열이 아닌 경우 오류가 반환되고 `SET`이 중단됨

### Code Example in Java
```java
boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));
```
- 만약 key가 존재한다면, `false`를 반환
- 만약 key가 존재하지 않는다면, 1의 값을 넣고 `true`를 반환

## ZADD

### Syntax

> ZADD key [NX | XX] [GT | LT] [CH] [INCR] score member [score member ...]

- Sorted Set에 `member`와 `score`를 저장한다.
- `key`가 존재하지 않으면 새로운 Sorted Set을 생성한다.
- `member`가 이미 존재하는 경우 기존 score를 새로운 score로 갱신한다.
- 같은 `member`는 중복으로 저장되지 않는다.
- score를 기준으로 정렬된다.
- 시간 복잡도 : `O(log(N))`

### Options

- `XX` : 이미 존재하는 member만 업데이트
- `NX` : 존재하지 않는 member만 새로 추가
- `LT` : member가 이미 존재하고, 새로운 score가 기존 score보다 작은 경우에만 업데이트
- `GT` : member가 이미 존재하고, 새로운 score가 기존 score보다 큰 경우에만 업데이트
- `CH` : 반환값에 새로 추가된 member뿐 아니라 score가 변경된 기존 member까지 포함
- `INCR` : score를 새 값으로 덮어쓰지 않고 기존 score에 더함. `ZINCRBY`처럼 동작하며, 이 옵션을 사용할 때는 `score member` 쌍을 하나만 사용할 수 있음

### Code Example in Java
```java
stringRedisTemplate.opsForZSet().add(key, member, score);
```

## ZINCRBY

### Syntax

> ZINCRBY key increment member

- Sorted Set에 저장된 `member`의 score를 `increment`만큼 증가시킨다.
- `member`가 존재하지 않으면 기존 score를 `0`으로 보고 `increment` 값을 score로 저장한다.
- `key`가 존재하지 않으면 새로운 Sorted Set을 생성하고 `member`를 추가한다.
- `increment`에는 음수 값을 사용할 수 있으며, 이 경우 score가 감소한다.
- 명령 실행 후 변경된 member의 새로운 score를 반환한다.
- 시간 복잡도 : `O(log(N))`

### Options

- 별도 옵션은 없음
- `key` : score를 증가시킬 Sorted Set의 key
- `increment` : member의 score에 더할 값
- `member` : score를 증가시킬 대상 member

### Code Example in Java
```java
stringRedisTemplate.opsForZSet().incrementScore(key, member, count);
```

## ZRANGE

### Syntax

> ZRANGE key start stop [BYSCORE | BYLEX] [REV] [LIMIT offset count] [WITHSCORES]

- Sorted Set에서 지정한 범위의 member를 반환한다.
- 기본적으로 score가 낮은 순서대로 조회한다.
- `start`와 `stop`은 기본적으로 0부터 시작하는 index를 의미한다.
- `stop`은 포함 범위이다.
- score가 같은 member는 사전순으로 정렬된다.
- 음수 index를 사용할 수 있으며, `-1`은 마지막 요소를 의미한다.
- 시간 복잡도 : `O(log(N)+M)`

### Options

- `BYSCORE` : index가 아니라 score 범위를 기준으로 member를 조회
- `BYLEX` : 사전순 범위를 기준으로 member를 조회
- `REV` : 정렬 순서를 반대로 조회
- `LIMIT offset count` : 조회 결과에서 `offset`만큼 건너뛴 뒤 `count`개만 반환
- `WITHSCORES` : member와 함께 score도 반환

### Code Example in Java
```java
//score가 높은 순으로 조회
Set<String> contentIdSet = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9); 
```