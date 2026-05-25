package io.github.taejeongh98.redislab.domain.content.dto;

import io.github.taejeongh98.redislab.domain.content.entity.Content;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ContentResponseDto {
    private int id;
    private String type;
    private String title;
    private String creator;
    private String description;
    private String releaseYear;
    private int viewCount;

    public static ContentResponseDto from(Content content) {
        return new ContentResponseDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getCreator(),
                content.getDescription(),
                content.getReleaseYear(),
                content.getViewCount()
        );
    }
}
