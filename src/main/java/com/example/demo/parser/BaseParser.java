package com.example.demo.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.entity.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseParser implements SiteParse, AutoCloseable {
  protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  protected static final int THREAD_COUNT = 25;
  protected static final int TIMEOUT = 20000;
  protected static final int THREADS_TIMEOUT = 60000;

  private static final Logger log = LoggerFactory.getLogger(BaseParser.class);
  private ExecutorService executor;

  protected BaseParser() {
    executor = Executors.newFixedThreadPool(THREAD_COUNT);
  }

  @Override
  public List<Article> parseLastArticles() {
    List<String> articleLinks = getArticleLinks();
    List<Callable<Article>> tasks = new ArrayList<>();

    for (String articleLink : articleLinks) {
      tasks.add(() -> getArticle(articleLink));
    }

    List<Article> articles = new ArrayList<>();

    try {
      List<Future<Article>> results = executor.invokeAll(tasks);

      for (Future<Article> result : results) {
        try {
          Article article = result.get();
          if (article != null) {
            articles.add(article);
          }
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
    Document page = getPage(blogLink);
    if (page == null) {
      return List.of();
    }
    return getArticleLinks(page);
  }

  protected abstract List<String> getArticleLinks(Document page);

  protected abstract Article getArticle(String link, Document page);

  protected Article getArticle(String link) {
    Document page = getPage(link);
    if (page == null) {
      return null;
    }
    return getArticle(link, page);
  }

  protected Document getPage(String link) {
    try {
      return Jsoup.connect(link)
          .timeout(TIMEOUT)
          .userAgent(USER_AGENT)
          .get();
    } catch (Exception e) {
      log.error("Get request error: {}", link, e);
      return null;
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
