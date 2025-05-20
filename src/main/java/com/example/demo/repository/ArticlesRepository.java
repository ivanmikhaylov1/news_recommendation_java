package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticlesRepository extends JpaRepository<Article, Long> {
  @Query("SELECT a.url FROM Article a WHERE a.website.id = :websiteId ORDER BY a.date DESC LIMIT :count")
  List<String> getLastArticles(@Param("websiteId") Long websiteId, @Param("count") Integer count);

  @Modifying
  @Query("DELETE FROM Article a WHERE a.website IN (SELECT w FROM Website w WHERE w.owner = :user)")
  void deleteByWebsiteOwner(@Param("user") User user);

  @Query("SELECT DISTINCT a FROM Article a WHERE a.id > :minId ORDER BY a.id")
  List<Article> findByMinId(@Param("minId") Long minId);

  @Query("SELECT COUNT(a) > 0 FROM Article a WHERE a.name = :name AND a.description = :description")
  boolean existsByNameAndDescription(@Param("name") String name, @Param("description") String description);

  Optional<Article> findByName(String name);

  Optional<Article> findByDescription(String description);

  Optional<Article> findByUrlContaining(String urlPart);
}
