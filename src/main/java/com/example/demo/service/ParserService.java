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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
  private final CategoryRepository categoryRepository;
  private final WebsiteRepository websiteRepository;

  private final List<BaseParser> baseParsers;

  private final RSSParser rssParser;

  @Value("${parser.limit}")
  private int limitArticleCount;

  @Value("${parser.maxDescription}")
  private int maxDescription;

  @Async
  @Scheduled(fixedRateString = "${parser.fixedRateMain}")
  public void runMainParsers() {
    List<CompletableFuture<Void>> futures = baseParsers.stream()
            .map(parser -> CompletableFuture.runAsync(() -> parseSite(parser)))
            .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void parseSite(BaseParser parser) {
    Optional<Website> optWebsite = websiteRepository.findByNameAndOwnerIsNull(parser.getNAME());
    if (optWebsite.isEmpty()) {
      log.error("Ошибка в название сайта: {}", parser.getNAME());
      return;
    }

    Website website = optWebsite.get();
    log.debug("Парсинг сайта {}", website.getUrl());

    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount + 10);
    log.debug("Прошлые ссылки {}", oldArticlesUrls);
    parser.getArticleLinks()
            .thenAccept(newArticlesUrls -> {
              log.debug("Новые ссылки с леты: {}", newArticlesUrls);
              for (String newArticleUrl : newArticlesUrls) {
                if (!oldArticlesUrls.contains(newArticleUrl)) {
                  log.debug("Парсинг новости {}", newArticleUrl);
                  parser.getArticle(newArticleUrl)
                          .thenAccept(
                                  optArticle -> optArticle.ifPresent(article -> {
                                    saveArticle(article, website);
                                  })
                          )
                          .join();
                }
              }
            });
  }

  @Async
  @Scheduled(fixedRateString = "${parser.fixedRateRSS}")
  public void runRSSParser() {
    List<Website> websites = websiteRepository.findByOwnerIsNotNull();

    List<CompletableFuture<Void>> futures = websites.stream()
            .map(website -> CompletableFuture.runAsync(() -> parseRSSSite(website)))
            .toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void parseRSSSite(Website website) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
    log.debug("Парсинг сайта {}", website.getUrl());
    rssParser.parseLastArticles(website.getUrl())
            .thenAccept(newArticles -> {
              for (ArticleDTO articleDTO : newArticles) {
                if (!oldArticlesUrls.contains(articleDTO.getUrl())) {
                  saveArticle(articleDTO, website);
                }
              }
            });
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

    log.debug("Статья {} сохранена", articleDTO.getUrl());
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
