package com.example.demo.parser.sites;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.parser.SiteParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RSSParser implements SiteParser {
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  private static final int TIMEOUT = 20000;

  @Value("${parser.limit}")
  private Integer limitArticleCount;

  @Setter
  private String link;

  @Override
  public List<ArticleDTO> parseLastArticles() {
    Optional<Document> page = getPage(link);

    return parseLastArticles(page.get());
  }

  public List<ArticleDTO> parseLastArticles(Document page) {
    List<ArticleDTO> articles = new ArrayList<>();

    if (page != null) {
      try {
        Element channel = page.select("channel").first();
        List<Element> articlesData = channel.select("item");

        for (Element articleData : articlesData) {
          Optional<ArticleDTO> article = getArticle(articleData);
          article.ifPresent(articles::add);

          if (articles.size() >=  limitArticleCount) {
            break;
          }
        }
      } catch (Exception e) {
        log.error("Website {} parsing error!", link, e);
      }
    }
    return articles;
  }

  private Optional<ArticleDTO> getArticle(Element item) {
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
      log.error("Parsing error: {}", link, e);
      return Optional.empty();
    }
  }

  private Optional<Document> getPage(String link) {
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
}
