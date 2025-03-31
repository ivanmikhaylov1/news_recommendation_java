package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import java.util.List;

public interface SiteParser {
  List<ArticleDTO> parseLastArticles();
}
