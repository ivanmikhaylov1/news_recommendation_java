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
public class HiTechParser extends BaseParser {
  private static final String BLOG_LINK = "https://hi-tech.mail.ru/category/technology/";
  @Getter
  private final String NAME = "Hi-Tech";
  private final Integer MIN_DESCRIPTION = 20;

  @Override
  public CompletableFuture<List<String>> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    try {
      List<Element> titleElements = page.select("a");
      List<String> links = new ArrayList<>();

      for (Element titleElement : titleElements) {
        String link = titleElement.attr("href");
        if (link != null && link.startsWith("https://hi-tech.mail.ru/news/")) {
          links.add(link);
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
      Element titleElement = page.select("h1").first();
      List<Element> descriptionElements = page.select("div[data-qa=\"Text\"]");
      Element dateElement = page.select("time, span[class*=date], div[class*=date]").first();

      String description = "";

      for (Element descriptionElement : descriptionElements) {
        String text = descriptionElement.text();
        if (text != null && text.length() > MIN_DESCRIPTION) {
          description = text;
        }
      }

      return Optional.of(ArticleDTO.builder()
              .name(titleElement.text().trim())
              .description(description)
              .url(link)
              .date(dateElement.text().trim())
              .build());
    } catch (Exception e) {
      log.error("Ошибка парсинга новости: {}", link, e);
      return Optional.empty();
    }
  }
}
