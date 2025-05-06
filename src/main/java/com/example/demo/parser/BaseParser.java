package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public abstract class BaseParser extends HttpParser implements SiteParser, AutoCloseable {
  protected static final int THREAD_COUNT = 25;
  protected static final int THREADS_TIMEOUT = 60000;
  private final ExecutorService executor;

  @Value("${parser.limit}")
  protected int limitArticleCount;

  protected BaseParser() {
    executor = Executors.newFixedThreadPool(THREAD_COUNT);
  }

  public abstract String getNAME();

  @Override
  public List<ArticleDTO> parseLastArticles() {
    try {
      List<String> articleLinks = getArticleLinks().get();
      List<ArticleDTO> articles = new ArrayList<>();

      for (String link : articleLinks) {
        Optional<ArticleDTO> article = getArticle(link).get();
        article.ifPresent(articles::add);
      }

      return articles;
    } catch (Exception e) {
      log.error("Error parsing articles", e);
      return List.of();
    }
  }

  @Override
  public CompletableFuture<List<String>> getArticleLinks() {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return getArticleLinks(getNAME()).get();
      } catch (Exception e) {
        log.error("Error getting article links", e);
        return List.of();
      }
    }, executor);
  }

  protected CompletableFuture<List<String>> getArticleLinks(String blogLink) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Optional<Document> page = getPage(blogLink).get();
        if (page.isEmpty()) {
          return List.of();
        }
        List<String> articlesLinks = getArticleLinks(page.get());
        return articlesLinks.size() > limitArticleCount ? articlesLinks.subList(0, limitArticleCount) : articlesLinks;
      } catch (Exception e) {
        log.error("Error getting article links from {}", blogLink, e);
        return List.of();
      }
    }, executor);
  }

  protected abstract List<String> getArticleLinks(Document page);

  protected abstract Optional<ArticleDTO> getArticle(String link, Document page);

  @Override
  public CompletableFuture<Optional<ArticleDTO>> getArticle(String link) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Optional<Document> page = getPage(link).get();
        if (page.isEmpty()) {
          return Optional.empty();
        }
        return getArticle(link, page.get());
      } catch (Exception e) {
        log.error("Error getting article from {}", link, e);
        return Optional.empty();
      }
    }, executor);
  }

  @Override
  public void close() {
    executor.shutdown();
  }
}
