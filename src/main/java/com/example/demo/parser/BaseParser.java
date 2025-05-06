package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class BaseParser extends HttpParser implements SiteParser {

  public abstract String getNAME();

  @Value("${parser.limit}")
  protected int limitArticleCount;

  @Override
  public abstract CompletableFuture<List<String>> getArticleLinks();

    protected CompletableFuture<List<String>> getArticleLinks(String blogLink) {
        return getPage(blogLink)
                .thenApply(page -> {
                    if (page.isEmpty()) {
                        return List.of();
                    }
                    List<String> articlesLinks = getArticleLinks(page.get());
                    return articlesLinks.size() > limitArticleCount ? articlesLinks.subList(0, limitArticleCount) : articlesLinks;
                });
  }

  protected abstract List<String> getArticleLinks(Document page);

  protected abstract Optional<ArticleDTO> getArticle(String link, Document page);

  @Override
  public CompletableFuture<Optional<ArticleDTO>> getArticle(String link) {
      return getPage(link)
              .thenApply(page -> {
                  if (page.isEmpty()) {
                      return Optional.empty();
                  }
                  return getArticle(link, page.get());
              });
  }
}
