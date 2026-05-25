package io.github.taejeongh98.redislab.domain.content.service;

import io.github.taejeongh98.redislab.domain.content.dto.ContentRequestDto;
import io.github.taejeongh98.redislab.domain.content.dto.ContentResponseDto;
import io.github.taejeongh98.redislab.domain.content.entity.Content;
import io.github.taejeongh98.redislab.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private final ContentRepository contentRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

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

    public ContentResponseDto addContent(ContentRequestDto request) {
        Content content = Content.from(request);
        Content savedContent = contentRepository.save(content);
        return ContentResponseDto.from(savedContent);
    }
}
