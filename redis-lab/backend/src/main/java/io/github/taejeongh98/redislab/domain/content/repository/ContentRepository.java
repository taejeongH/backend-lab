package io.github.taejeongh98.redislab.domain.content.repository;

import io.github.taejeongh98.redislab.domain.content.entity.Content;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentRepository extends JpaRepository<Content, Integer> {

    Optional<Content> findById(int contentId);
}
