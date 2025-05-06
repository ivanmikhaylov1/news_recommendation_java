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
public class HiTechParser extends BaseParser {
  private static final String BLOG_LINK = "https://hi-tech.mail.ru/news/";

  @Override
  public String getNAME() {
    return BLOG_LINK;
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    List<Element> titleElements = page.select("h3[data-qa='Title'] a");
    List<String> links = new ArrayList<>();

    for (Element titleElement : titleElements) {
      String link = titleElement.attr("href");
      links.add(link);
    }

    return links;
  }

  @Override
  public Optional<ArticleDTO> getArticle(String link, Document page) {
    try {
      Element titleElement = page.select("h1").first();
      Element descriptionElement = page.select("div[data-qa='Text']").first();
      Element dateElement = page.select("time").first();

      return Optional.ofNullable(ArticleDTO.builder()
          .name(titleElement.text())
          .description(descriptionElement.text())
          .url(link)
          .date(dateElement.text())
          .build());
    } catch (Exception e) {
      log.error("Parsing error: {}", link, e);
      return Optional.empty();
    }
  }
}
