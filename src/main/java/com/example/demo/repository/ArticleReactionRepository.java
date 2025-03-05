package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.ArticleReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleReactionRepository extends JpaRepository<ArticleReaction, Long> {
  ArticleReaction findByArticleId(Long articleId);

  @Query(value = "SELECT a.* FROM articles a LEFT JOIN article_reactions ar ON a.article_id = ar.article_id ORDER BY ar.likes_count DESC LIMIT 10", nativeQuery = true)
  List<Article> findTop10();
}