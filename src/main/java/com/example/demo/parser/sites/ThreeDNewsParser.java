package com.example.demo.parser.sites;


import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.parser.BaseParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class ThreeDNewsParser extends BaseParser {
  private static final String BLOG_LINK = "https://3dnews.ru";

  @Override
  protected List<String> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    List<Element> articleBlocks = page.select("div.content-block-data.white");
    List<String> links = new ArrayList<>();

    for (Element articleBlock : articleBlocks) {
      Element linkElement = articleBlock.select("a").first();

      if (linkElement != null) {
        links.add(linkElement.attr("href"));
      }
    }
    return links;
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link) {
    return super.getArticle(BLOG_LINK + link);
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link, Document page) {
    try {
      Element titleElement = page.select("title").first();
      Element descriptionElement = page.select("div.js-mediator-article p").first();
      Element dateElement = page.select("span.entry-date.tttes").first();

      if (titleElement == null || descriptionElement == null) {
        log.warn("Не удалось найти заголовок или описание для: {}", link);
        return Optional.empty();
      }

      String dateText = "Неизвестная дата";
      if (dateElement != null) {
        String[] dateParts = dateElement.text().split(",");
        dateText = dateParts.length > 0 ? dateParts[0] : dateText;
      } else {
        log.warn("Не удалось найти дату для: {}", link);
      }

      return Optional.ofNullable(ArticleDTO.builder()
          .name(titleElement.text())
          .description(descriptionElement.text())
          .url(link)
          .date(dateText)
          .build());
    } catch (Exception e) {
      log.error("Parsing error: {}", link, e);
      return Optional.empty();
    }
  }
}
