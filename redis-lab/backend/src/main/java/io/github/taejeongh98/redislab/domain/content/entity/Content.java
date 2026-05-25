package io.github.taejeongh98.redislab.domain.content.entity;

import io.github.taejeongh98.redislab.domain.content.dto.ContentRequestDto;
import io.github.taejeongh98.redislab.domain.content.dto.ContentResponseDto;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@Setter
@Getter
@NoArgsConstructor
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String type;
    private String title;
    private String creator;
    private String description;
    private String releaseYear;
    private int viewCount;

    public Content(String type, String title, String creator, String description, String releaseYear, int viewCount) {
        this.type = type;
        this.title = title;
        this.creator = creator;
        this.description = description;
        this.releaseYear = releaseYear;
        this.viewCount = viewCount;
    }

    public static Content from(ContentRequestDto content) {
        return new Content(
                content.getType(),
                content.getTitle(),
                content.getCreator(),
                content.getDescription(),
                content.getReleaseYear(),
                content.getViewCount()
        );
    }
}
