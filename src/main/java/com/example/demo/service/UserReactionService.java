package com.example.demo.service;

import com.example.demo.domain.model.UserReaction;
import com.example.demo.repository.UserReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserReactionService {
  private final UserReactionRepository userReactionRepository;

  public Optional<UserReaction> findUserReaction(Long userId, Long articleId) {
    return userReactionRepository.findByUserReaction(userId, articleId);
  }

  public long countReactionsByType(Long articleId, UserReaction.ReactionType reactionType) {
    return userReactionRepository.countReactionsByType(articleId, reactionType);
  }

  public UserReaction saveUserReaction(UserReaction userReaction) {
    return userReactionRepository.save(userReaction);
  }

  public void deleteUserReaction(UserReaction userReaction) {
    userReactionRepository.delete(userReaction);
  }
}
