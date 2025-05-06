package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
public abstract class BaseParser implements SiteParser, AutoCloseable {
  protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  protected static final int THREAD_COUNT = 25;
  protected static final int TIMEOUT = 20000;
  protected static final int THREADS_TIMEOUT = 60000;
  private final ExecutorService executor;
  @Value("${parser.limit}")
  protected int limitArticleCount;

  protected BaseParser() {
    executor = Executors.newFixedThreadPool(THREAD_COUNT);
  }

  @Override
  public List<ArticleDTO> parseLastArticles() {
    List<String> articleLinks = getArticleLinks();
    List<Callable<Optional<ArticleDTO>>> tasks = new ArrayList<>();

    for (String articleLink : articleLinks) {
      tasks.add(() -> getArticle(articleLink));
    }

    List<ArticleDTO> articles = new ArrayList<>();

    try {
      List<Future<Optional<ArticleDTO>>> results = executor.invokeAll(tasks);

      for (Future<Optional<ArticleDTO>> result : results) {
        try {
          Optional<ArticleDTO> article = result.get();
          article.ifPresent(articles::add);
        } catch (ExecutionException e) {
          log.error("Article parsing error", e);
        }
      }
    } catch (InterruptedException e) {
      log.error("Parsing interrupted", e);
      Thread.currentThread().interrupt();
    }

    return articles;
  }

  protected abstract List<String> getArticleLinks();

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

  protected Optional<ArticleDTO> getArticle(String link) {
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

  @Override
  public void close() {
    executor.shutdown();

    try {
      if (!executor.awaitTermination(THREADS_TIMEOUT, TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
