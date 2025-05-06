package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticlesRepository extends JpaRepository<Article, Long> {
  @Query("SELECT a.url FROM Article a "
      + "WHERE a.website.id = :websiteId "
      + "ORDER BY a.date DESC " +
      "LIMIT :count")
  List<String> getLastArticles(@Param("websiteId") Long websiteId, @Param("count") Integer count);

  @Modifying
  @Query("DELETE FROM Article a WHERE a.website IN (SELECT w FROM Website w WHERE w.owner = :user)")
  void deleteByWebsiteOwner(@Param("user") User user);
}
