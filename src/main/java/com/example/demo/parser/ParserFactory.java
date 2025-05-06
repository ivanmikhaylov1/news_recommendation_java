package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.sites.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParserFactory {
  private final HiTechParser hiTechParser;
  private final InfoqParser infoqParser;
  private final ThreeDNewsParser threeDNewsParser;
  private final RSSParser rssParser;
  private final AIArticleParser aiArticleParser;

  public SiteParser getParser(Website website) {
    if (!isWebsiteAccessible(website.getUrl())) {
      log.error("Сайт {} недоступен или требует авторизации", website.getUrl());
      return new EmptyParser();
    }

    switch (website.getName()) {
      case "Hi-Tech" -> {
        return hiTechParser;
      }
      case "Infoq" -> {
        return infoqParser;
      }
      case "3Dnews" -> {
        return threeDNewsParser;
      }
    }

    if (website.getUrl().contains("/rss") || website.getUrl().endsWith(".xml")) {
      rssParser.setLink(website.getUrl());
      return rssParser;
    }
    
    return new AISiteParser(aiArticleParser, website.getUrl());
  }

  private boolean isWebsiteAccessible(String url) {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setRequestMethod("HEAD");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

      int responseCode = connection.getResponseCode();
      return responseCode >= 200 && responseCode < 400;
    } catch (IOException e) {
      log.error("Ошибка при проверке доступности сайта {}: {}", url, e.getMessage());
      return false;
    }
  }

  private static class EmptyParser implements SiteParser {
    @Override
    public List<ArticleDTO> parseLastArticles() {
      return List.of();
    }
  }

  private static class AISiteParser implements SiteParser {
    private final AIArticleParser aiArticleParser;
    private final String url;

    public AISiteParser(AIArticleParser aiArticleParser, String url) {
      this.aiArticleParser = aiArticleParser;
      this.url = url;
    }

    @Override
    public List<ArticleDTO> parseLastArticles() {
      try {
        Document document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .timeout(10000)
            .get();

        AIArticleParser.ParsedArticle parsedArticle = aiArticleParser.parseArticle(document.html());

        ArticleDTO article = ArticleDTO.builder()
            .name(parsedArticle.getTitle())
            .description(parsedArticle.getDescription())
            .date(parsedArticle.getPublishedDate())
            .url(url)
            .build();

        return List.of(article);
      } catch (Exception e) {
        log.error("Ошибка при парсинге сайта {}: {}", url, e.getMessage());
        return List.of();
      }
    }
  }
}
