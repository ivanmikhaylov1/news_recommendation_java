package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;

import java.util.List;
import java.util.Optional;

public interface SiteParser {
  List<String> getArticleLinks();

  Optional<ArticleDTO> getArticle(String link);
}
