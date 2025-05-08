package com.example.demo.service;

import com.example.demo.AIClassificator.ArticleClassifier;
import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.DefaultWebsiteIds;
import com.example.demo.parser.ParserFactory;
import com.example.demo.parser.SiteParser;
import com.example.demo.parser.YandexTranslator;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {
  private final ArticlesRepository articlesRepository;
  private final WebsiteRepository websiteRepository;
  private final ArticleClassifier articleClassifier;
  private final ParserFactory parserFactory;
  private final YandexTranslator yandexTranslator;

  @Value("${parser.limit}")
  private int limitArticleCount;

  @Value("${parser.maxDescription}")
  private int maxDescription;

  @Async
  @Scheduled(fixedRateString = "${parser.fixedRateMain}")
  public void runMainParsers() {
    List<CompletableFuture<Void>> futures = Arrays.stream(DefaultWebsiteIds.values())
        .map(value -> CompletableFuture.runAsync(() -> {
          try {
            Optional<Website> website = websiteRepository.findByName(value.getName());
            website.ifPresent(this::parseSite);
          } catch (Exception e) {
            log.error("Ошибка при парсинге сайта {}: {}", value, e.getMessage(), e);
          }
        }))
        .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  @Async
  @Scheduled(fixedRateString = "${parser.fixedRateRSS}")
  public void runAIParser() {
    List<Website> websites = websiteRepository.findByOwnerIsNotNull();
    List<CompletableFuture<Void>> futures = websites.stream()
        .map(website -> CompletableFuture.runAsync(() -> {
          try {
            String url = website.getUrl();
            if (url != null && !url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
              url = "https://" + url;
              log.info("Исправлен URL для сайта {}: {}", website.getName(), url);
            }
            parseSite(website);
          } catch (Exception e) {
            log.error("Ошибка при парсинге сайта {} с помощью AI: {}", website.getUrl(), e.getMessage(), e);
          }
        }))
        .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  @Transactional
  protected void parseSite(Website website) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
    SiteParser parser = parserFactory.getParser(website);
    List<ArticleDTO> articles = parser.parseLastArticles();

    for (ArticleDTO articleDTO : articles) {
      if (!oldArticlesUrls.contains(articleDTO.getUrl())) {
        try {
          if (!parser.getLanguage().equals("ru")) {
            translateAndSaveArticle(articleDTO, website, parser.getLanguage());
          } else {
            saveArticle(articleDTO, website);
          }
        } catch (Exception e) {
          log.error("Ошибка при обработке статьи {}: {}", articleDTO.getUrl(), e.getMessage(), e);
        }
      }
    }
  }

  private void translateAndSaveArticle(ArticleDTO articleDTO, Website website, String language) {
    CompletableFuture<Optional<String>> translatedNameFuture = yandexTranslator.translate(
        articleDTO.getName(), language, "ru");
    CompletableFuture<Optional<String>> translatedDescriptionFuture = yandexTranslator.translate(
        articleDTO.getDescription(), language, "ru");

    CompletableFuture.allOf(translatedNameFuture, translatedDescriptionFuture)
        .thenAccept(v -> {
          try {
            String translatedName = translatedNameFuture.join().orElseGet(() -> {
              log.warn("Не удалось перевести заголовок статьи: {}", articleDTO.getName());
              return articleDTO.getName();
            });

            String translatedDescription = translatedDescriptionFuture.join().orElseGet(() -> {
              log.warn("Не удалось перевести описание статьи: {}", articleDTO.getUrl());
              return articleDTO.getDescription();
            });

            articleDTO.setName(translatedName);
            articleDTO.setDescription(translatedDescription);

            log.debug("Статья {} переведена", articleDTO.getUrl());

            saveArticle(articleDTO, website);
          } catch (Exception e) {
            log.error("Ошибка при сохранении переведенной статьи {}: {}", articleDTO.getUrl(), e.getMessage(), e);
          }
        })
        .join(); // Ждем завершения перевода и сохранения
  }

  private void saveArticle(ArticleDTO articleDTO, Website website) {
    Article article = Article.builder()
        .name(limitStringLength(articleDTO.getName()))
        .url(limitStringLength(articleDTO.getUrl()))
        .description(cutDescription(articleDTO.getDescription()))
        .siteDate(limitStringLength(articleDTO.getDate()))
        .date(LocalDateTime.now())
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

  private String cutDescription(String description) {
    if (description == null || description.length() <= maxDescription) {
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

  private String limitStringLength(String input) {
    if (input != null && input.length() > 255) {
      return input.substring(0, 255);
    }
    return input;
  }
}
