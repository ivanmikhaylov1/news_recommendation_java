package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class RSSParser extends HttpParser {
  @Value("${parser.limit}")
  private Integer limitArticleCount;

  public CompletableFuture<List<ArticleDTO>> parseLastArticles(String link) {
    return getPage(link)
            .thenApply(optPage -> optPage
                    .map(page -> parseLastArticles(page, link))
                    .orElse(Collections.emptyList())
            );
  }

  public List<ArticleDTO> parseLastArticles(Document page, String link) {
    List<ArticleDTO> articles = new ArrayList<>();

    if (page != null) {
      try {
        Element channel = page.select("channel").first();
        List<Element> articlesData = channel.select("item");

        for (Element articleData : articlesData) {
          Optional<ArticleDTO> article = getArticle(articleData, link);
          article.ifPresent(articles::add);

          if (articles.size() >= limitArticleCount) {
            break;
          }
        }
      } catch (Exception e) {
        log.error("Ошибка парсинга ленты: {}", link, e);
        return Collections.emptyList();
      }
    }
    return articles;
  }

  private Optional<ArticleDTO> getArticle(Element item, String link) {
    try {
      Element titleElement = item.select("title").first();
      Element descriptionElement = item.select("description").first();
      Element dateElement = item.select("pubDate").first();
      Element linkElement = item.select("link").first();

      return Optional.ofNullable(ArticleDTO.builder()
          .name(titleElement.text())
          .description(descriptionElement.text())
          .url(linkElement.text())
          .date(dateElement.text())
          .build());
    } catch (Exception e) {
      log.error("Ошибка парсинга новости: {}", link, e);
      return Optional.empty();
    }
  }
}
