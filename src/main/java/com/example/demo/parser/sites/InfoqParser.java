package com.example.demo.parser.sites;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.entity.Article;
import org.example.parser.BaseParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class InfoqParser extends BaseParser {
  private static final String DOMAIN = "https://www.infoq.com";
  private static final String BLOG_LINK = "https://www.infoq.com/development";

  @Override
  protected List<String> getArticleLinks() {
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
  public Article getArticle(String link) {
    Document page = getPage(DOMAIN + link);
    return getArticle(link, page);  // Теперь вызываем метод, который принимает уже полученную страницу
  }

  @Override
  public Article getArticle(String link, Document page) {
    Element titleElement = page.selectFirst("h1");
    Element dateElement = page.selectFirst("p.article__readTime.date");
    List<Element> contentElements = page.select("p");

    String title = titleElement != null ? titleElement.text() : enrichTitle(link);
    String date = dateElement != null ? dateElement.text() : "Unknown date";

    StringBuilder textBuilder = new StringBuilder();
    for (Element content : contentElements) {
      String text = content.text().trim();
      if (!text.isEmpty()) {
        textBuilder.append(text).append(" ");
      }
    }

    return new Article(UUID.randomUUID(), title, textBuilder.toString().trim(), date, DOMAIN + link);
  }

  private String enrichTitle(String link) {
    String title = link;
    if (link.contains("articles")) {
      title = link.replace("https://www.infoq.com/articles/", "").replace("-", " ");
    } else if (link.contains("news")) {
      title = link.replace("https://www.infoq.com/news/", "").replace("-", " ");
    }
    return Character.toUpperCase(title.charAt(0)) + title.substring(1);
  }
}