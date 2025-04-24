package com.example.demo.parser.sites;


import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.parser.BaseParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class ThreeDNewsParser extends BaseParser {
  @Getter
  private final String NAME = "3Dnews";

  private static final String BLOG_LINK = "https://3dnews.ru";

  @Override
  public CompletableFuture<List<String>> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    try {
      List<Element> articleBlocks = page.select("div.content-block-data.white");
      List<String> links = new ArrayList<>();

      for (Element articleBlock : articleBlocks) {
        Element linkElement = articleBlock.select("a").first();

        if (linkElement != null) {
          links.add(BLOG_LINK + linkElement.attr("href"));
        }
      }
      return links;
    } catch (Exception e) {
      log.error("Ошибка парсинга ленты: {}", BLOG_LINK, e);
      return Collections.emptyList();
    }
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link, Document page) {
    try {
      Element titleElement = page.select("title").first();
      Element descriptionElement = page.select("div.js-mediator-article p").first();
      Element dateElement = page.select("span.entry-date strong").first();

      if (dateElement == null) {
        dateElement = page.select("span.date").first();
      }

      return Optional.ofNullable(ArticleDTO.builder()
          .name(titleElement.text())
          .description(descriptionElement.text())
          .url(link)
          .date(dateElement.text().split(",")[0])
          .build());
    } catch (Exception e) {
      log.error("Ошибка парсинга новости: {}", link, e);
      return Optional.empty();
    }
  }
}
