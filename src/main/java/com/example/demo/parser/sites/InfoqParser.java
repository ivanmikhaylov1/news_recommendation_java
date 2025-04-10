package com.example.demo.parser.sites;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.parser.BaseParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class InfoqParser extends BaseParser {
  @Getter
  private static final String NAME = "Infoq";

  private static final String DOMAIN = "https://www.infoq.com";
  private static final String BLOG_LINK = "https://www.infoq.com/development";

  @Override
  public List<String> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    List<Element> titleElements = page.select("h4.card__title a");
    List<String> links = new ArrayList<>();

    for (Element titleElement : titleElements) {
      String link = titleElement.attr("href");
      if (link.contains("news") || link.contains("articles")) {
        links.add(link);
      }
    }

    return links;
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link) {
    return super.getArticle(DOMAIN + link);
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link, Document page) {
    try {
      Element titleElement = page.select("h1").first();
      Element dateElement = page.select("p.article__readTime.date").first();
      List<Element> contentElements = page.select("p");

      StringBuilder textBuilder = new StringBuilder();
      for (Element content : contentElements) {
        String text = content.text().trim();
        if (!text.isEmpty()) {
          textBuilder.append(text).append(" ");
        }
      }

      return Optional.ofNullable(ArticleDTO.builder()
          .name(titleElement.text())
          .description(textBuilder.toString().trim())
          .url(link)
          .date(dateElement.text())
          .build());
    } catch (Exception e) {
      log.error("Parsing error: {}", link, e);
      return Optional.empty();
    }
  }
}