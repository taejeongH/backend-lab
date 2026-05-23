package io.github.taejeongh98.redislab.domain.redis.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis/string")
class RedisController {

    private final StringRedisTemplate redisTemplate;

    RedisController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<String> write(@RequestParam String key, @RequestParam String value) {
        redisTemplate.opsForValue().set(key, value);
        return ResponseEntity.status(HttpStatus.OK).body(key);
    }

    @GetMapping
    public ResponseEntity<String> get(@RequestParam String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.OK).body(value);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String key) {
        Boolean deleted = redisTemplate.delete(key);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
