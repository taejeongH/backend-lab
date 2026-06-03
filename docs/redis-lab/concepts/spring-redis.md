# Spring Redis

## Redis Cleint 종류
Redis Client의 종류에는 Lettuce, Redisson, Jedis가 있는데, Jedis는 비동기 처리를 지원하지 않으며 Thread-safe 하지 않아 잘 쓰지 않는 추세이기 때문에 Spring Data Redis, Lettuce, Redisson 3가지를 비교
### 1. spring data redis
- Spring Data Redis는 Redis와 상호작용하기 위한 고수준의 추상화 제공
- Redis 클라이언트로 Lettuce를 사용하더라도 Spring Data Redis의 추상화된 API를 사용하여 보다 간단하게 Redis와 통신 가능

#### spring data redis 설정
```java
@Configuration
public class RedisConfiguration {
    @Value("${spring.redis.cluster.nodes}")
    private String clusterNodes;
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(Arrays.asList(clusterNodes.split(", ")));
        clusterConfig.setMaxRedirects(maxRedirects);

        return new LettuceConnectionFactory(clusterConfig);
    }
 
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
}
```

#### spring data redis 사용
```java
@RequiredArgsConstructor
@Service
public class RedisUtils {
    /**
     *  Redis 사용위한 util 클래스
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 지정된 키와 값으로 데이터를 Redis에 저장
     */
    public void setData(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 지정된 키에 해당하는 데이터를 Redis에서 조회
     */
    public String getData(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * 지정된 키에 해당하는 데이터를 Redis에서 삭제
     */
    public void deleteData(String key) {
        redisTemplate.delete(key);
    }
}
```

#### 장점
- **Spring 통합** : Spring 애플리케이션에서 쉽게 통합할 수 있으며, Spring의 다양한 기능과 잘 통합됨
- **고수준 API** : Redis에 대한 고수준 추상화를 제공하여 사용하기 쉬움
- **Repository 지원** : Spring Data의 Repository 패턴을 사용하여 데이터 접근을 간편화

#### 단점
- **추가적인 레이어** : Spring Data Redis는 추가적인 추상화 레이어를 제공하여 성능에 영향을 미칠 수 있음
- **유연성 부족** : Lettuce나 Redisson에 비해 유연성이 떨어질 수 있음

### 2. Lettuce
- Lettuce는 Redis와 저수준 통신을 가능하게 함
- Lettuce를 직접 사용할 때는 `RedisClusterClient`, `StatefulRedisClusterConnection`, `RedisClusterCommands` 등을 명시적으로 설정하고 호출
#### Lettuce 설정
```java
@Configuration
public class LettuceConfiguration {
    @Value("${spring.redis.cluster.nodes}")
    private String clusterNodes;
    
    @Bean
    public RedisClusterClient redisClusterClient(List<RedisURI> redisNodes) {
        return RedisClusterClient.create(clusterNodes);
    }

    @Bean
    public StatefulRedisClusterConnection<String, String> statefulRedisClusterConnection(RedisClusterClient redisClusterClient) {
        return redisClusterClient.connect();
    }
}
```
#### Lettuce 사용 코드
```java
@Service
public class LettuceService {

    @Autowired
    private StatefulRedisClusterConnection<String, String> connection;

    public void execute() {
        
        RedisClusterCommands<String, String> commands = connection.sync();
        commands.set("key", "value");

        String value = commands.get("key");

        System.out.println(value);
    }
}
```
#### 장점
- **비동기/동기 API 지원** : Lettuce는 비동기, 동기 및 반응형 API를 모두 지원하여 다양한 요구에 맞게 사용 가능
- **성능** : 높은 성능과 낮은 지연 시간을 제공하며, Netty 기반의 비동기 네트워크 라이브러리 사용
- **클러스터 지원** : Redis 클러스터와 고가용성 설정(마스터-슬레이브)을 지원
- **Thread-safe**: 연결이 스레드에 안전하며, 다중 스레드 환경에서 사용 가능

#### 단점
- **사용자 경험** : 다른 라이브러리보다 사용이 더 복잡할 수 있으며, 추가적인 설정 필요
- **추상화 레벨** : Spring Data Redis에 비해 추상화 레벨이 낮아 사용 시 더 많은 작업이 필요할 수 있음

### 3. Redisson
#### Redisson 설정
```java
@Configuration
public class RedissonConfig {
    @Value("${spring.redis.cluster.nodes}")
    private String clusterNodes;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress(clusterNodes.split(","));
        return Redisson.create(config);
    }
}
```
- Redisson은 자체적으로 Redis 서버와의 연결을 관리하며, 설정이 간편하고 직관적
- spring-data-redis처럼 RedisConnectionFactory 같은 연결 로직을 작성하지 않아도 됨

#### redisson 사용 코드
```java
@RequiredArgsConstructor
public class RedissonClientService {
    private final RedissonClient redissonClient;

    /**
     * 주어진 키에 해당하는 데이터를 Redis에서 조회
     */
    public ActionData get(String key) {
        RBucket<ActionData> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    /**
     * 주어진 키와 값으로 데이터를 Redis에 저장
     */
    public void set(String key, ActionData value) {
        RBucket<ActionData> bucket = redissonClient.getBucket(key);
        bucket.set(value);
    }
}
```
#### 장점
- **풍부한 기능** : 분산 객체, 서비스 및 Redis 기반의 데이터 구조를 지원하여 사용자가 쉽게 사용할 수 있음 (ex. 분산 락, 분산 컬렉션, 분산 캐시 등 제공)
- **RBucket 등 고수준 추상화** : RBucket, RMap, RList 등의 고수준 API를 제공하여 개발자가 쉽게 사용 가능
- **성능 및 안정성** : 고성능을 제공하며, 네트워크 및 Redis 서버 장애를 처리하는 다양한 기능 제공
- **편리한 설정** : 사용이 간편하며, 설정이 직관적

#### 단점
- **추가 의존성** : 다른 Redis 클라이언트에 비해 기능이 많아 의존성 크기가 클 수 있음
- **일부 기능의 복잡성** : 모든 기능을 사용하지 않으면 오버헤드가 될 수 있음

## RedisTemplate과 StringRedisTemplate의 차이

### RedisTemplate
- 제네릭 타입 지정 가능 (`RedisTemplate<String, Object>` 등)
- 다양한 직렬화 전략 적용 가능 (예: JSON 직렬화)
- 복잡한 객체 저장/조회에 적합
### StringRedisTemplate
- Key, Value 모두 String 직렬화에 특화
- 단순 문자열 데이터 처리에 적합
- `opsForValue()`, `opsForHash()` 등 API 사용법은 동일

## 객체 저장 시 직렬화가 필요한 이유
- Redis에 저장되는 데이터는 문자열 또는 바이트 형태로 저장되기 때문에 Java 객체를 이해하지 못함
- 따라서 Java 객체를 Redis에 저장하려면 객체를 문자열이나 바이트 형태로 변환하는 과정 필요

### 직렬화/역직렬화
- 직렬화 : Java 객체를 Redis에 저장 가능한 문자열 또는 바이트 형태로 변환하는 과정
- 역직렬화 : Redis에서 조회한 문자열 또는 바이트 데이터를 다시 Java 객체로 변환하는 과정

### 직렬화 종류

#### 1. StringRedisSerializer

문자열을 그대로 Redis에 저장하는 방식

```java
redisTemplate.setKeySerializer(new StringRedisSerializer());
redisTemplate.setValueSerializer(new StringRedisSerializer());
```

- 사람이 읽기 쉬움
- 단순 문자열 저장에 적합
- 객체 저장에는 적합하지 않음

#### 2. JdkSerializationRedisSerializer

Java 기본 직렬화 방식을 사용

- 별도 설정 없이 객체 저장 가능
- Redis에서 사람이 읽기 어려운 바이너리 형태로 저장됨
- Java 직렬화에 의존하므로 다른 언어나 시스템과의 호환성이 떨어짐

#### 3. GenericJackson2JsonRedisSerializer

객체를 JSON 형태로 직렬화

```java
redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
```

- 객체를 JSON 형태로 저장 가능
- Redis에서 값을 확인하기 비교적 쉬움
- 다른 언어나 시스템과 연동하기 쉬움
- 객체 타입 정보와 역직렬화 방식을 함께 고려해야 함


## Redis 연결 실패를 애플리케이션에서 어떻게 다뤄야 하는지

### 1. Timeout 설정

- Redis 연결이 실패했을 때 너무 오래 기다리지 않도록 timeout을 설정
- 예를 들어 Redis 응답 대기 시간을 짧게 설정하면, 장애 상황에서 사용자가 1분 가까이 기다리는 상황을 줄일 수 있음

```properties
spring.data.redis.timeout=3s
spring.data.redis.connect-timeout=1s
```

### 2. 예외 처리
- Redis 연결 실패는 `RedisConnectionException` 또는 Spring에서 감싼 `RedisConnectionFailureException` 형태로 발생 가능
- 따라서, Redis 접근 로직에서는 연결 실패 예외를 고려해야 한다.

```java
try {
    return redisTemplate.opsForValue().get(key);
} catch (RedisConnectionFailureException e) {
    // Redis 장애 상황 처리
}
```

### 3. Fallback 전략
- Redis를 캐시 용도로 사용한다면 Redis 장애 시 DB에서 직접 조회하는 fallback 전략 사용 가능
```text
Redis 조회 실패 -> DB 조회 -> 응답 변환
```
- 반대로 Redis를 필수 저장소로 사용한다면 fallback이 어렵기 때문에, 장애 시 명확한 에러 응답을 반환하거나 서비스 사용을 제한

### 4. Redis의 역할에 따라 장애 대응 방식 결정
- 캐시: Redis 장애 시 DB 조회로 우회 가능
- 세션 저장소: Redis 장애 시 로그인 상태 유지에 영향 발생
- 분산 락: Redis 장애 시 동시성 제어 실패 가능
- Rate Limit: Redis 장애 시 요청 제한 정책이 정상 동작하지 않을 수 있음
