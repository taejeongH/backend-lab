package io.github.taejeongh98.redislab.domain.content.controller;

import io.github.taejeongh98.redislab.domain.content.dto.ContentRequestDto;
import io.github.taejeongh98.redislab.domain.content.dto.ContentResponseDto;
import io.github.taejeongh98.redislab.domain.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contents")
@RequiredArgsConstructor
class ContentController {

    private final ContentService contentService;

    @PostMapping
    public ResponseEntity<ContentResponseDto> addContents(@RequestBody ContentRequestDto request) {
        ContentResponseDto result = contentService.addContent(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentResponseDto> getContent(@PathVariable int contentId) {
        ContentResponseDto result = contentService.getContent(contentId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{contentId}")
    public ResponseEntity<ContentResponseDto> updateContent(@PathVariable int contentId, @RequestBody ContentRequestDto request) {
        ContentResponseDto result = contentService.updateContent(contentId, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> deleteContent(@PathVariable int contentId){
        contentService.deleteContent(contentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{contentId}/view")
    public ResponseEntity<Boolean> viewContent(@PathVariable int contentId, @RequestHeader("Idempotency-Key") int idempotencyKey) {
        boolean success = contentService.viewContent(contentId, idempotencyKey);
        return ResponseEntity.ok(success);
    }
}
