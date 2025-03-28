package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Website;
import java.awt.print.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticlesRepository extends JpaRepository<Article, Long> {
  @Query("SELECT a.url FROM Article a "
      + "WHERE a.website.id = :websiteId "
      + "ORDER BY a.date DESC")
  List<String> getLastArticles(@Param("websiteId") Long websiteId, Pageable pageable);
}