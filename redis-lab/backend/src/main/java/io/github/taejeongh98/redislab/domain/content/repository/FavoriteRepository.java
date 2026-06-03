package io.github.taejeongh98.redislab.domain.content.repository;

import io.github.taejeongh98.redislab.domain.content.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

}
