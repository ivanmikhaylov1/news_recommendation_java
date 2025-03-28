package com.example.demo.parser.sites;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.entity.Article;
import org.example.parser.BaseParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ThreeDNewsParser extends BaseParser {
  private static final String BLOG_LINK = "https://3dnews.ru";

  private final int limitPageCount;

  public ThreeDNewsParser() {
    this(10);
  }

  public ThreeDNewsParser(int limitPageCount) {
    super();
    this.limitPageCount = limitPageCount;
  }

  @Override
  protected List<String> getArticleLinks() {
    return getArticleLinks(BLOG_LINK);
  }

  @Override
  public List<String> getArticleLinks(Document page) {
    List<Element> articleBlocks = page.select("div.content-block-data.white");
    List<String> links = new ArrayList<>();

    for (Element articleBlock : articleBlocks) {
      links.add(articleBlock.selectFirst("a").attr("href"));

      if (links.size() == limitPageCount) {
        break;
      }
    }
    return links;
  }

  @Override
  public Article getArticle(String link, Document page) {
    Element titleElement = page.selectFirst("title");
    Element descriptionElement = page.selectFirst("div.js-mediator-article p");
    Element dateElement = page.selectFirst("span.entry-date.tttes");

    String name = titleElement.text();
    String description = descriptionElement.text();
    String dateString = dateElement.text().split(",")[0];

    return new Article(UUID.randomUUID(), name, description, dateString, link);
  }
}
