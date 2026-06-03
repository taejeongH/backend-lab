# Redis 명령어

## SET 명령어

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
