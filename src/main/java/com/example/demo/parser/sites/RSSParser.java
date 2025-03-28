package com.example.demo.parser.sites;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.entity.Article;
import org.example.parser.SiteParse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RSSParser implements SiteParse {
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
  private static final Logger log = LoggerFactory.getLogger(InfoqParser.class);
  private static final int TIMEOUT = 20000;
  private static final int LIMIT_ARTICLES_COUNT = 10;

  private final String link;

  public RSSParser(String link) {
    this.link = link;
  }

  @Override
  public List<Article> parseLastArticles() {
    Document page = getPage(link);

    return parseLastArticles(page);
  }

  public List<Article> parseLastArticles(Document page) {
    List<Article> articles = new ArrayList<>();

    if (page != null) {
      try {
        Element channel = page.selectFirst("channel");
        List<Element> articlesData = channel.select("item");

        for (Element articleData : articlesData) {
          articles.add(getArticle(articleData));

          if (articles.size() >=  LIMIT_ARTICLES_COUNT) {
            break;
          }
        }
      } catch (Exception e) {
        log.error("Website {} parsing error!", link, e);
      }
    }
    return articles;
  }

  private Article getArticle(Element item) {
    Element titleElement = item.selectFirst("title");
    Element descriptionElement = item.selectFirst("description");
    Element dateElement = item.selectFirst("pubDate");
    Element linkElement = item.selectFirst("link");

    String name = titleElement.text();
    String description = descriptionElement.text();
    String dateString = dateElement.text();
    String link = linkElement.text();

    return new Article(UUID.randomUUID(), name,description, dateString, link);
  }

  private Document getPage(String link) {
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
}
