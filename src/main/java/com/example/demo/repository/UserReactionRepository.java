package com.example.demo.repository;

import com.example.demo.domain.model.UserReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReactionRepository extends JpaRepository<UserReaction, Long> {
  Optional<UserReaction> findByUserId(Long userId, Long articleId);

  long countRating(Long articleId, UserReaction.ReactionType reactionType);
}