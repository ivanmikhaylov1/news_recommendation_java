package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SiteParser {
  CompletableFuture<List<String>> getArticleLinks();

  CompletableFuture<Optional<ArticleDTO>> getArticle(String link);
}
