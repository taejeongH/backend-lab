package io.github.taejeongh98.redislab.domain.content.service;

import io.github.taejeongh98.redislab.domain.content.dto.ContentRequestDto;
import io.github.taejeongh98.redislab.domain.content.dto.ContentResponseDto;
import io.github.taejeongh98.redislab.domain.content.entity.Content;
import io.github.taejeongh98.redislab.domain.content.entity.Favorite;
import io.github.taejeongh98.redislab.domain.content.repository.ContentRepository;
import io.github.taejeongh98.redislab.domain.content.repository.FavoriteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private final ContentRepository contentRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final FavoriteRepository favoriteRepository;
    private static String RANK_KEY = "content:view:ranking";

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
            //Duration TTL = Duration.ofSeconds(20);
            Duration TTL = Duration.ofMinutes(5);
            stringRedisTemplate.opsForValue().set(key, jsonValue, TTL);
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

    @Transactional
    public ContentResponseDto updateContent(int contentId, ContentRequestDto request) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

        //트랜잭션 내부에서 캐시 삭제
        //stringRedisTemplate.delete("content:" + contentId);

        //여기 까지 성공한 후 롤백된다면 캐시는 삭제되지만, DB에는 여전히 이전데이터가 남아있음
        content.update(request);


        //트랜잭션이 종료된 이후(커밋 후) 레디스 캐시 삭제
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                stringRedisTemplate.delete("content:"+contentId);
//            }
//        });

        return ContentResponseDto.from(content);
    }

    @Transactional
    public void deleteContent(int contentId) {
        //트랜잭션 내부에서 캐시 삭제
        //stringRedisTemplate.delete("content:" + contentId);
        contentRepository.deleteById(contentId);

        //트랜잭션이 종료된 이후(커밋 후) 레디스 캐시 삭제
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                stringRedisTemplate.delete("content:"+contentId);
//            }
//        });
    }

    @Transactional
    public boolean viewContent(int contentId, int idempotencyKey) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
        String key = "view:req:" + contentId + ":" + idempotencyKey;

        boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));

        //Redis 저장 완료 후 예외가 발생하는 경우 실험
        //return false;

        if (!success) return false;

        int cnt = content.getViewCount();
        content.setViewCount(++cnt);

        //해당 member(contentId)의 score를 1늘림
        stringRedisTemplate.opsForZSet().incrementScore(RANK_KEY, String.valueOf(contentId), 1);

        //type별 랭킹
        stringRedisTemplate.opsForZSet().incrementScore(RANK_KEY + content.getType(), String.valueOf(contentId), 1);

        return true;
    }

    @Transactional
    public boolean favoriteContent(int contentId, int userId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

        String key = "favorite:req:" + contentId + ":" + userId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));


        if (!Boolean.TRUE.equals(success)) {
            return false;
        }

        try {
            Favorite favorite = new Favorite(contentId, userId);
            favoriteRepository.save(favorite);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    public List<ContentResponseDto> getRanking(String contentType) {
        String key = RANK_KEY + contentType;

        //score가 높은 순대로 조회
        Set<String> contentIdSet = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9);

        if (contentIdSet == null || contentIdSet.isEmpty()) {
            return List.of();
        }

        List<Integer> contentIds = contentIdSet.stream()
                .map(Integer::valueOf)
                .toList();

        //score가 높은 순으로 저장된 contentIds를 DB를 통해 조회
        List<Content> contents = contentRepository.findAllById(contentIds);

        Map<Integer, Content> contentMap = contents.stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));

        return contentIds.stream()
                .map(contentMap::get)
                .filter(Objects::nonNull)
                .map(ContentResponseDto::from)
                .toList();
    }
}
