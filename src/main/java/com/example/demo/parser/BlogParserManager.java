package com.example.demo.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.entity.Article;
import org.example.entity.Website;
import org.example.parser.sites.HiTechParser;
import org.example.repository.interfaces.WebsiteRepository;
import org.jsoup.nodes.Document;

public class BlogParserManager {
  private final Map<Integer, BaseParser> parsers = new HashMap<>();
  private int parserId = 1;
  private final WebsiteRepository websiteRepository;

  public BlogParserManager(WebsiteRepository websiteRepository) {
    this.websiteRepository = websiteRepository;
    loadWebsitesFromDatabase();
  }

  private void loadWebsitesFromDatabase() {
    List<Website> websites = websiteRepository.getBasicWebsites();

    for (Website website : websites) {
      if ("HI_TECH".equals(website.getName())) {
        parsers.put(parserId++, new HiTechParser());
      } else {
      }
    }
  }

  public List<Article> parse(int siteId) {
    BaseParser parser = parsers.get(siteId);
    if (parser != null) {
      List<String> articleLinks = parser.getArticleLinks(parser.getPage("https://hi-tech.mail.ru/news/"));
      List<Article> articles = new ArrayList<>();

      for (String link : articleLinks) {
        Document articlePage = parser.getPage(link);
        articles.add(parser.getArticle(link, articlePage));
      }

      return articles;
    } else {
      throw new IllegalArgumentException("Парсер для указанного сайта не найден: " + siteId);
    }
  }
}
