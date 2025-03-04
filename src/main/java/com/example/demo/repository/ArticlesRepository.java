package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticlesRepository extends JpaRepository<Article, Long> {
  @Query(value = "SELECT a.* FROM articles a LEFT JOIN user_reactions ur ON a.article_id = ur.article_id GROUP BY a.article_id ORDER BY COUNT(CASE WHEN ur.reaction_type = 'LIKE' THEN 1 END) - COUNT(CASE WHEN ur.reaction_type = 'DISLIKE' THEN 1 END) DESC LIMIT 10", nativeQuery = true)
  List<Article> findTop10();

  @Query("SELECT a FROM Article a JOIN UserWebsiteStatus uws ON a.website = uws.website " +
      "JOIN a.category c " +
      "WHERE uws.user = :user " +
      "AND (uws.lastSentArticleDate IS NULL OR a.date > uws.lastSentArticleDate) " +
      "AND (c IN :categories OR a.website IN :websites)")
  List<Article> getNewArticlesForUser(@Param("user") User user, @Param("categories") List<Category> categories, @Param("websites") List<Website> websites);
}
