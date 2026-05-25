package io.github.taejeongh98.redislab.domain.content.service;

import io.github.taejeongh98.redislab.domain.content.dto.ContentRequestDto;
import io.github.taejeongh98.redislab.domain.content.dto.ContentResponseDto;
import io.github.taejeongh98.redislab.domain.content.entity.Content;
import io.github.taejeongh98.redislab.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentResponseDto getContent(int contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
        return ContentResponseDto.from(content);
    }

    public ContentResponseDto addContent(ContentRequestDto request) {
        Content content = Content.from(request);
        Content savedContent = contentRepository.save(content);
        return ContentResponseDto.from(savedContent);
    }
}
