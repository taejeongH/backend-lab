# [redis-000] Basic TTL

## 실험 질문
Spring Boot 애플리케이션에서 Redis에 데이터를 저장하고 조회할 수 있는가?

## 배경
Redis를 실제 백엔드 애플리케이션에서 사용하려면 redis-cli가 아니라 애플리케이션 코드에서 읽고 쓰는 흐름을 알아야 한다.

초기 실험에서는 직렬화 이슈를 줄이기 위해 StringRedisTemplate부터 사용한다.

## 구현할 것
- redis-lab Spring Boot 모듈 생성
- Redis 연결 설정
- StringRedisTemplate 사용
- 간단한 Redis 테스트 API 구현

## Spring Boot, Redis 연결 설정

### Gradle 의존성 설정
```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

### application.properties 설정
```yml
spring.data.redis.host = localhost
spring.data.redis.port = 6379
```

## 실험 1. StringRedisTemplate 사용

### RedisConfig.java
```java
@Configuration
class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Bean
    public StringRedisTemplate redisTemplate() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }
}
```
- 직렬화 이슈를 줄이기 위해 `StringRedisTemplate` 사용

## 실험 2. 간단한 Redis 테스트 API 구현

### StringRedisTemplate 의존성 주입

```java
private final StringRedisTemplate redisTemplate;

RedisController(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
}
```

- Redis에 문자열 데이터를 저장하고 조회하기 위해 `StringRedisTemplate`을 주입받는다.
- 이번 실험에서는 서비스 계층을 분리하지 않고, 테스트 목적의 API를 컨트롤러에서 바로 구현한다.

---

### API 구현

```java
/* SET */
@PostMapping
public ResponseEntity<String> write(@RequestParam String key, @RequestParam String value) {
    redisTemplate.opsForValue().set(key, value);
    return ResponseEntity.ok(key);
}

/* GET */
@GetMapping
public ResponseEntity<String> get(@RequestParam String key) {
    String value = redisTemplate.opsForValue().get(key);

    if (value == null) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(value);
}

/* DEL */
@DeleteMapping
public ResponseEntity<Void> delete(@RequestParam String key) {
    Boolean deleted = redisTemplate.delete(key);

    if (!Boolean.TRUE.equals(deleted)) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.noContent().build();
}
```

---

## 실험 2-1. SET 실행

### 요청

```http
POST /redis/string?key=testKey&value=testValue
```

### 처리 흐름

```java
redisTemplate.opsForValue().set(key, value);
```

- 요청 파라미터로 전달받은 `key`, `value`를 Redis에 문자열로 저장한다.
- `StringRedisTemplate`을 사용하므로 key와 value는 문자열 기반으로 저장된다.

### API 응답

```http
200 OK
```

```text
testKey
```

### redis-cli로 저장 결과 확인

```bash
GET testKey
```

### 결과

```bash
"testValue"
```

### 해석

- Spring Boot API를 통해 Redis에 문자열 데이터를 저장할 수 있다.
- 애플리케이션에서 저장한 값은 `redis-cli`에서도 동일하게 조회된다.
- 현재 `set(key, value)` 방식은 TTL을 지정하지 않기 때문에 key가 자동으로 만료되지 않는다.

---

## 실험 2-2. GET 실행

### 요청

```http
GET /redis/string?key=testKey
```

### 처리 흐름

```java
String value = redisTemplate.opsForValue().get(key);
```

- 요청으로 전달받은 `key`를 기준으로 Redis에서 값을 조회한다.
- key가 존재하면 저장된 value가 반환된다.
- key가 존재하지 않으면 `null`이 반환된다.

### API 응답

```http
200 OK
```

```text
testValue
```

### redis-cli에서 값 변경 후 API 조회

먼저 redis-cli에서 값을 변경한다.

```bash
SET testKey testValue2
```

이후 API로 다시 조회한다.

```http
GET /redis/string?key=testKey
```

### 결과

```text
testValue2
```

### 존재하지 않는 key 조회

```http
GET /redis/string?key=unknownKey
```

### API 응답

```http
404 Not Found
```

### 해석

- Spring Boot API를 통해 Redis에 저장된 문자열 데이터를 조회할 수 있다.
- Redis에 존재하지 않는 key를 조회하면 `StringRedisTemplate`은 `null`을 반환한다.
- 애플리케이션에서는 `null` 여부를 기준으로 `404 Not Found`를 응답할 수 있다.
- Redis는 외부 저장소이므로, `redis-cli`에서 값을 변경하면 API 조회 결과에도 바로 반영된다.

---

## 실험 2-3. DEL 실행

### 요청

```http
DELETE /redis/string?key=testKey
```

### 처리 흐름

```java
Boolean deleted = redisTemplate.delete(key);
```

- 요청으로 전달받은 `key`를 Redis에서 삭제한다.
- 삭제에 성공하면 `true`가 반환된다.
- 삭제할 key가 존재하지 않으면 `false`가 반환된다.

### API 응답

```http
204 No Content
```

### redis-cli로 삭제 결과 확인

```bash
GET testKey
```

### 결과

```bash
(nil)
```

### 존재하지 않는 key 삭제 요청

```http
DELETE /redis/string?key=unknownKey
```

### API 응답

```http
404 Not Found
```

### 해석

- Spring Boot API를 통해 Redis에 저장된 key를 삭제할 수 있다.
- 삭제된 key를 다시 조회하면 Redis에서는 `(nil)`이 반환된다.
- 애플리케이션에서는 삭제 성공 여부에 따라 `204 No Content` 또는 `404 Not Found`로 응답할 수 있다.

---

## 깨뜨려본 상황

### 1. Redis 서버가 꺼져 있을 때 API를 호출하면 어떤 예외가 발생하는가

Redis 서버를 종료한 뒤 API를 호출했다.

```http
GET /redis/string?key=testKey
```

### 결과

Redis 서버가 꺼져 있어도 API가 즉시 응답을 반환하지는 않았다.

서버는 약 1분 동안 Redis에 재연결을 시도했고, 그동안 클라이언트에는 응답이 내려오지 않았다.  
1분이 지나도 Redis와 연결되지 않으면 최종적으로 `500 Internal Server Error`가 반환되었다.

### 발생 예외

```text
io.lettuce.core.RedisConnectionException: Connection closed prematurely
```

### API 응답

```http
500 Internal Server Error
```

### 해석

- `StringRedisTemplate`은 Redis 명령을 실행하기 위해 Redis connection이 필요하다.
- Redis 서버가 꺼져 있으면 연결을 얻지 못하고 재연결을 시도한다.
- 이 재연결 시도 중에는 API 응답이 지연된다.
- 약 1분 동안 연결이 복구되지 않으면 최종적으로 예외가 발생하고 `500 Internal Server Error`가 반환된다.
- 즉, 현재 구조에서는 Redis 장애가 단순히 Redis 기능 실패로만 끝나는 것이 아니라, API 응답 지연과 서버 에러로 이어진다.

---

### 2. Redis 연결 실패가 전체 API 실패로 이어지는지 확인한다

### 확인 방법

1. Redis 서버를 종료한다.
2. `POST`, `GET`, `DELETE` API를 호출한다.
3. API 응답과 애플리케이션 로그를 확인한다.

### 결과

- Redis에 접근해야 하는 API는 정상 처리되지 않는다.
- Redis 연결 실패로 인해 API 요청이 실패한다.
- 현재 구조에서는 Redis 장애가 해당 API의 실패로 바로 이어진다.

### 해석

- 현재 API는 Redis를 필수 저장소처럼 사용하고 있다.
- 따라서 Redis 연결이 실패하면 데이터 저장, 조회, 삭제가 모두 실패한다.
- 캐시처럼 Redis를 사용할 경우에는 Redis 장애 시 DB 조회로 우회하는 fallback 전략을 고려할 수 있다.
- 하지만 이번 실험 API는 Redis 자체 동작을 확인하는 목적이므로 별도의 fallback은 적용하지 않았다.

## 이번 실험에서 알게 된 점

- Spring Boot API로 저장, 조회, 삭제한 데이터는 실제 Redis에 반영되며, `redis-cli`에서도 동일하게 확인할 수 있다.
- Redis 연결 실패 시 API가 즉시 실패하지 않고 약 1분간 응답이 지연된 뒤 `500 Internal Server Error`가 반환되는 것을 확인했다.
- Redis를 사용하는 API는 Redis 장애 상황까지 고려해야 하며, 필요에 따라 timeout 설정이나 fallback 전략을 설계해야 한다.