package com.example.demo.parser.sites;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.entity.Article;
import org.example.parser.BaseParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class HiTechParser extends BaseParser {
  private static final String BLOG_LINK = "https://hi-tech.mail.ru/news/";

  @Override
  protected List<String> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
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
  public Article getArticle(String link, Document page) {
    Element titleElement = page.selectFirst("h1");
    Element descriptionElement = page.selectFirst("div[data-qa='Text']");
    Element dateElement = page.selectFirst("time");

    String title = titleElement != null ? titleElement.text() : "Unknown title";
    String description = descriptionElement != null ? descriptionElement.text() : "";
    String date = dateElement != null ? dateElement.attr("datetime") : "Unknown date";

    return new Article(UUID.randomUUID(), title, description, date, link);
  }
}
