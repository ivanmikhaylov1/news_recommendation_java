package com.example.demo.service;

import com.example.demo.AIClassificator.ArticleClassifier;
import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.DefaultWebsiteIds;
import com.example.demo.parser.ParserFactory;
import com.example.demo.parser.SiteParser;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {
  private final ArticlesRepository articlesRepository;
  private final WebsiteRepository websiteRepository;
  private final ArticleClassifier articleClassifier;
  private final ParserFactory parserFactory;

  @Value("${parser.limit}")
  private int limitArticleCount;

  @Value("${parser.maxDescription}")
  private int maxDescription;

  @Scheduled(fixedRateString = "${parser.fixedRateMain}")
  public void runMainParsers() {
    for (DefaultWebsiteIds value : DefaultWebsiteIds.values()) {
      try {
        Optional<Website> website = websiteRepository.findByName(value.getName());
        website.ifPresent(this::parseSite);
      } catch (Exception e) {
        log.error("Ошибка при парсинге сайта {}: {}", value, e.getMessage(), e);
      }
    }
  }

  @Scheduled(fixedRateString = "${parser.fixedRateRSS}")
  public void runRSSParser() {
    for (Website website : websiteRepository.findByOwnerIsNotNull()) {
      try {
        String url = website.getUrl();
        if (url != null && !url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
          url = "https://" + url;
          log.info("Исправлен URL для сайта {}: {}", website.getName(), url);
        }

        parseSite(website);
      } catch (Exception e) {
        log.error("Ошибка при парсинге RSS для сайта {}: {}", website.getUrl(), e.getMessage(), e);
      }
    }
  }

  @Transactional
  protected void parseSite(Website website) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
    SiteParser parser = parserFactory.getParser(website);
    List<ArticleDTO> articles = parser.parseLastArticles();

    LocalDateTime now = LocalDateTime.now();

    for (ArticleDTO articleDTO : articles) {
      if (!oldArticlesUrls.contains(articleDTO.getUrl())) {
        Article article = Article.builder()
            .name(limitStringLength(articleDTO.getName()))
            .url(limitStringLength(articleDTO.getUrl()))
            .description(cutDescription(articleDTO.getDescription()))
            .siteDate(limitStringLength(articleDTO.getDate()))
            .date(now)
            .website(website)
            .build();

        try {
          List<Category> categories = articleClassifier.classifyArticle(article);
          if (!categories.isEmpty()) {
            log.info("Статья '{}' классифицирована по категориям: {}",
                article.getName(),
                categories.stream().map(Category::getName).reduce((a, b) -> a + ", " + b).orElse(""));

            Set<Category> categorySet = new HashSet<>(categories);
            article.setCategories(categorySet);

            articlesRepository.save(article);
            log.info("Добавлена новая статья: {}", article.getName());
          } else {
            log.info("Для статьи '{}' не найдено подходящих категорий, статья не будет добавлена", article.getName());
          }
        } catch (Exception e) {
          log.error("Ошибка при классификации статьи '{}': {}", article.getName(), e.getMessage());
          log.error("Статья с ошибкой: name={}, url={}, desc_length={}",
              article.getName(), article.getUrl(),
              article.getDescription() != null ? article.getDescription().length() : 0);
        }
      }
    }
  }

  private String cutDescription(String description) {
    if (description.length() > maxDescription) {
      //todo  сокращение с помощью регулярок
      return description.substring(0, maxDescription);
    }
    return description;
  }

  private String limitStringLength(String input) {
    if (input != null && input.length() > 255) {
      return input.substring(0, 255);
    }

    return input;
  }
}
