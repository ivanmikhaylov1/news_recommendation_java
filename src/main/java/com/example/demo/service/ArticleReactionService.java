package com.example.demo.service;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.ArticleReaction;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.UserReaction;
import com.example.demo.repository.ArticleReactionRepository;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.UserReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleReactionService {

  private final ArticleReactionRepository articleReactionRepository;
  private final UserReactionRepository userReactionRepository;
  private final ArticlesRepository articlesRepository;

  public ArticleReaction retrieveArticleReactions(Long articleId) {
    return articleReactionRepository.findByArticleId(articleId);
  }

  public List<Article> fetchTopRatedArticles() {
    return articlesRepository.findTop10();
  }

  public void handleUserReaction(User user, Article article, UserReaction.ReactionType reactionType) {
    Optional<UserReaction> existingReaction = userReactionRepository
        .findByUserIdAndArticleId(user.getId(), article.getId());

    UserReaction reaction = existingReaction.orElse(UserReaction.builder()
        .user(user)
        .article(article)
        .createdAt(LocalDateTime.now())
        .build());

    reaction.setReactionType(reactionType);
    userReactionRepository.save(reaction);

    calculateReactionStatistics(article);
  }

  private void calculateReactionStatistics(Article article) {
    ArticleReaction articleReaction = retrieveArticleReactions(article.getId());
    if (articleReaction == null) {
      articleReaction = ArticleReaction.builder()
          .article(article)
          .likesCount(0)
          .dislikesCount(0)
          .build();
    }

    long likesCount = userReactionRepository.countByArticleIdAndReactionType(article.getId(), UserReaction.ReactionType.LIKE);
    long dislikesCount = userReactionRepository.countByArticleIdAndReactionType(article.getId(), UserReaction.ReactionType.DISLIKE);

    articleReaction.setLikesCount((int) likesCount);
    articleReaction.setDislikesCount((int) dislikesCount);
    articleReaction.setRating(calculateRating(likesCount, dislikesCount));

    articleReactionRepository.save(articleReaction);
  }

  private float calculateRating(long likes, long dislikes) {
    if (likes + dislikes == 0) return 0f;
    return (float) likes / (likes + dislikes);
  }
}
