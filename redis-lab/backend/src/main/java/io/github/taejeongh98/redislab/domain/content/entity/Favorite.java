package io.github.taejeongh98.redislab.domain.content.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "favorite",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"content_id", "user_id"})
        }
)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;
    int userId;
    int contentId;
    LocalDate createdAt;

    public Favorite(int userId, int contentId) {
        this.userId = userId;
        this.contentId = contentId;
        this.createdAt = LocalDate.now();
    }
}
