package io.github.taejeongh98.redislab.domain.content.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ContentRequestDto {
    private String type;
    private String title;
    private String creator;
    private String description;
    private String releaseYear;
    private int viewCount;
}
