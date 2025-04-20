package com.example.demo.service;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.BaseParser;
import com.example.demo.parser.RSSParser;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {
  private final ArticlesRepository articlesRepository;
  private final CategoryRepository categoryRepository;
  private final WebsiteRepository websiteRepository;

  private final List<BaseParser> baseParsers;

  private final RSSParser rssParser;

  @Value("${parser.limit}")
  private int limitArticleCount;

  @Value("${parser.maxDescription}")
  private int maxDescription;

  @Scheduled(fixedRateString = "${parser.fixedRateMain}")
  public void runMainParsers() {
    for (BaseParser parser : baseParsers) {
      try {
        Optional<Website> website = websiteRepository.findByNameAndOwnerIsNull(parser.getNAME());
        website.ifPresent(value -> parseSite(value, parser));
      } catch (Exception e) {
        log.error("Ошибка при парсинге сайта {}: {}", parser.getNAME(), e.getMessage(), e);
      }
    }
  }

  //  @Scheduled(fixedRateString = "${parser.fixedRateRSS}")
  public void runRSSParser() {
    for (Website website : websiteRepository.findByOwnerIsNotNull()) {
      try {
        String url = website.getUrl();
        if (url != null && !url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
          url = "https://" + url;
          log.info("Исправлен URL для сайта {}: {}", website.getName(), url);
        }
        rssParser.setLink(url);

        List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
        List<ArticleDTO> articles = rssParser.parseLastArticles();

        for (ArticleDTO articleDTO : articles) {
          if (!oldArticlesUrls.contains(articleDTO.getUrl())) {
            saveArticle(articleDTO, website);
          }
        }
      } catch (Exception e) {
        log.error("Ошибка при парсинге RSS для сайта {}: {}", website.getUrl(), e.getMessage(), e);
      }
    }
  }

  protected void parseSite(Website website, BaseParser parser) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount + 10);

    try {
      List<String> newArticlesUrls = parser.getArticleLinks();

      for (String newArticleUrl : newArticlesUrls) {
        if (!oldArticlesUrls.contains(newArticleUrl)) {
          Optional<ArticleDTO> article = parser.getArticle(newArticleUrl);

          article.ifPresent(articleDTO -> saveArticle(articleDTO, website));
        }
      }
    } catch (Exception e) {
      log.error("Ошибка при парсинге сайта {}: {}", website.getUrl(), e.getMessage(), e);
    }
  }

  private void saveArticle(ArticleDTO articleDTO, Website website) {
    LocalDateTime now = LocalDateTime.now();
    Set<Category> categories = getTextCategories(articleDTO.getDescription());

    Article article = Article.builder()
            .name(articleDTO.getName())
            .url(articleDTO.getUrl())
            .description(cutDescription(articleDTO.getDescription()))
            .siteDate(articleDTO.getDate())
            .date(now)
            .website(website)
            .categories(categories)
            .build();

    articlesRepository.save(article);
  }

  private String cutDescription(String description) {
    if (description.length() <= maxDescription) {
      return description;
    }

    Pattern pattern = Pattern.compile("\\.(?=\\s|$)");
    Matcher matcher = pattern.matcher(description.substring(0, maxDescription));

    int lastDotIndex = -1;
    while (matcher.find()) {
      lastDotIndex = matcher.end();
    }

    if (lastDotIndex != -1) {
      return description.substring(0, lastDotIndex).trim();
    } else {
      int lastSpace = description.substring(0, maxDescription).lastIndexOf(" ");
      return description.substring(0, lastSpace > 0 ? lastSpace : maxDescription).trim();
    }
  }

  private Set<Category> getTextCategories(String text) {
    //todo
    return new HashSet<>(categoryRepository.findAll());
  }
}
