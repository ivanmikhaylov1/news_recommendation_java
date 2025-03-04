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

  @Query("SELECT ar.article FROM ArticleReaction ar ORDER BY ar.rating DESC LIMIT 10")
  List<Article> findTop10();
}