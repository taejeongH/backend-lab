# [redis-000] Basic TTL

## 실험 질문
Redis key는 어떻게 저장되고, TTL을 설정하면 언제 사라지는가?

## 배경
Redis는 메모리 기반 저장소이므로 데이터를 무한히 쌓는 방식으로 사용하면 안 된다.

콘텐츠 상세 캐시, 중복 요청 방어 key, 검색 rate limit key처럼 임시 성격의 데이터에는 TTL 설계가 중요하다.

## 구현할 것
- Docker Compose로 Redis 실행
- redis-cli 접속
- SET, GET, DEL 명령 실험
- EXPIRE, TTL 명령 실험
- TTL이 있는 key와 없는 key 비교

## Redis 실행

### docker-compose.yml
```
version: '3.7'
services:
    redis:
        image: redis:7.4-alpine
        command: redis-server --port 6379
        container_name : redis-lab
        ports:
          - 6379:6379
        volumes:
          - redis_volume_data:/data

volumes:
  redis_volume_data:
```

### Redis 컨테이너 실행
```
$ docker compose -f docker-compose.yml up -d
```

### redis-cli 접속
```
docker exec -it redis-lab redis-cli
```
## 실험 1. SET, GET, DEL 명령어 실행

### 실행 명령

```redis
SET test hello
GET test
```

### 실행 결과
```redis
OK
"hello"
```

### 해석
- `SET`은 특정 key에 value를 저장하는 명령어
- Redis에서 key는 보통 문자열로 사용
- `SET`으로 저장한 value는 기본적으로 Redis String value로 저장됨


## 실험 2. 존재하지 않는 key를 GET하면 어떻게 되는가

### 실행 명령
```redis
GET test123
```

### 실행 결과
```redis
(nil)
```

### 해석
- `(nil)`은 해당 key가 존재하지 않는다는 의미
- Java/Spring에서 Redis 값을 조회할 때 key가 존재하지 않는 경우 `null` 반환

## 실험 3. DEL은 무엇을 반환하는가

### 실행 명령

```redis
SET test hello
SET test2 world

DEL test test2
```

### 실행 결과

```redis
OK
OK
(integer) 2
```

### 해석
- `DEL`은 실제로 삭제된 key의 개수를 반환
- `test`, `test2` 두 key가 존재했고 둘 다 삭제되었기 때문에 `2`를 반환
- 이미 삭제된 key를 삭제하면 `0` 반환

## 실험 4. TTL이 없는 key는 어떻게 보이는가

### 실행 명령
```redis
SET permanent-key value
TTL permanent-key
```

### 실행 결과
```redis
OK
(integer) -1
```

### 해석
- `TTL`은 key의 남은 유효 시간을 초 단위로 반환
- key에 유효 시간이 설정되어 있지 않다면 `-1` 반환

## 실험 5. EXPIRE로 TTL 설정하기

### 실행 명령
```redis
SET tmp-key value
EXPIRE tmp-key 10
TTL tmp-key
```

### 실행 결과
```redis
OK
(integer) 1
(integer) 10
```

### 해석
- `EXPIRE key seconds`는 특정 key에 만료시간 설정
- `EXPIRE`의 반환값은 TTL 설정 성공 여부 (0 or 1)

## 실험 6. TTL이 지난 key는 어떻게 되는가
### 실행 명령
```redis
SET expire-key value
EXPIRE expire-key 3
TTL expire-key
```

### 실행 결과
```redis
OK
(integer) 1
(integer) 3
```

3초 후


```redis
GET expire-key
TTL expire-key
```

### 실행 결과
```redis
(nil)
(integer) -2
```

### 해석
- TTL이 지난 key는 삭제됨
- 만료되어 사라진 key에 `TTL`을 실행하면 `-2` 반환 (key가 존재하지 않음)

## 실험 7.없는 key에 EXPIRE를 실행하면 어떻게 되는가

### 실행 명령
```redis
EXPIRE no-key 10
```

### 실행 결과
```redis
(integer) 0
```

### 해석
- `EXPIRE`는 존재하는 key에만 TTL 설정 가능


## 깨뜨려본 상황
### 1. TTL없이 key를 계속 생성하면 어떤 문제가 생길 수 있는가?
Redis는 메모리 기반의 데이터베이스이다.

메모리가 부족해지면 Redis의 성능이 떨어질 수 있고, maxmemory 정책에 따라 기존 key가 삭제될 수 있다.

특히 캐시와 같은 일시적으로 사용되는 데이터의 경우 TTL 설정을 반드시 고려해야 한다.
 ### 2. rate limit key에 TTL이 없다면
rate limit은 보통 일정 시간 동안 요청 횟수를 제한하는 방식이다.

예를 들어 1분 동안 10번만 요청할 수 있게 하려면 다음과 같은 key를 사용할 수 있다.

```redis
INCR rate-limit:user:1
EXPIRE rate-limit:user:1 60
```

이때 TTL이 없다면 `rate-limit:user:1` key가 계속 남아 있게 된다.

그러면 시간이 지나도 요청 횟수가 초기화되지 않아 사용자가 계속 제한 상태에 걸릴 수 있다.

즉, rate limit key에는 제한 시간과 동일한 TTL이 필요하다.

 ### 3. idempotency key에 TTL이 없다면

 중복 요청 방어를 위해 다음과 같은 key를 사용할 수 있다.

```redis
SET payment:request:abc123 processing
EXPIRE payment:request:abc123 300
```

이 key는 같은 요청이 짧은 시간 안에 반복되는 것을 막기 위한 임시 key다.

TTL이 없다면 요청 처리 후에도 key가 계속 남아, 나중에 정상적인 재시도까지 중복 요청으로 오판할 수 있다.

따라서 idempotency key도 데이터 성격에 맞는 TTL이 필요하다.

 ## 이번 실험에서 알게 된 점

Redis의 TTL은 단순히 key를 자동 삭제하는 기능이 아니라, 임시 데이터의 생명주기를 관리하는 핵심 기능이다.

TTL이 없는 key는 계속 남아 있으므로 Redis를 캐시나 임시 저장소로 사용할 때는 key의 성격에 따라 TTL을 명확히 설계해야 한다.

특히 rate limit, idempotency key, 인증 코드처럼 시간이 지나면 의미가 없어지는 데이터는 TTL이 없으면 오히려 잘못된 서비스 동작을 만들 수 있다.