package com.example.demo.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class HttpParser {
  protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  protected static final int TIMEOUT = 20000;

  protected CompletableFuture<Optional<Document>> getPage(String link) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return Optional.ofNullable(Jsoup.connect(link)
            .timeout(TIMEOUT)
            .userAgent(USER_AGENT)
            .get());
      } catch (Exception e) {
        log.error("Get request error: {}", link, e);
        return Optional.empty();
      }
    });
  }
} 