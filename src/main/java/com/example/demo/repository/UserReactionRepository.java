package com.example.demo.repository;

import com.example.demo.domain.model.UserReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReactionRepository extends JpaRepository<UserReaction, Long> {
  @Query("SELECT ur FROM UserReaction ur WHERE ur.user.id = :userId AND ur.article.id = :articleId")
  Optional<UserReaction> findByUserReaction(@Param("userId") Long userId, @Param("articleId") Long articleId);

  @Query("SELECT COUNT(ur) FROM UserReaction ur WHERE ur.article.id = :articleId AND ur.reactionType = :reactionType")
  long countReactionsByType(@Param("articleId") Long articleId, @Param("reactionType") UserReaction.ReactionType reactionType);
}