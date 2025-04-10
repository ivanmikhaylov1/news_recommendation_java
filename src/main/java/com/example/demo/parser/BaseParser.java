package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class BaseParser implements SiteParser {
  @Getter
  protected String NAME;

  protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  protected static final int TIMEOUT = 20000;

  @Value("${parser.limit}")
  protected int limitArticleCount;

  @Override
  public abstract List<String> getArticleLinks();

  protected List<String> getArticleLinks(String blogLink) {
    Optional<Document> page = getPage(blogLink);
    if (page.isEmpty()) {
      return List.of();
    }
    List<String> articlesLinks = getArticleLinks(page.get());
    return articlesLinks.size() > limitArticleCount ? articlesLinks.subList(0, limitArticleCount) : articlesLinks;
  }

  protected abstract List<String> getArticleLinks(Document page);

  protected abstract Optional<ArticleDTO> getArticle(String link, Document page);

  @Override
  public Optional<ArticleDTO> getArticle(String link) {
    Optional<Document> page = getPage(link);
    if (page.isEmpty()) {
      return Optional.empty();
    }
    return getArticle(link, page.get());
  }

  protected Optional<Document> getPage(String link) {
    try {
      return Optional.ofNullable(Jsoup.connect(link)
          .timeout(TIMEOUT)
          .userAgent(USER_AGENT)
          .get());
    } catch (Exception e) {
      log.error("Get request error: {}", link, e);
      return Optional.empty();
    }
  }
}
