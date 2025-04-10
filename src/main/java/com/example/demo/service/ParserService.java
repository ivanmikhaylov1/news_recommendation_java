package com.example.demo.service;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.DefaultWebsiteIds;
import com.example.demo.parser.RSSParser;
import com.example.demo.parser.SiteParser;
import com.example.demo.parser.sites.HiTechParser;
import com.example.demo.parser.sites.InfoqParser;
import com.example.demo.parser.sites.ThreeDNewsParser;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.CategoryRepository;
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
  private final CategoryRepository categoryRepository;
  private final WebsiteRepository websiteRepository;

  private final HiTechParser hiTechParser;
  private final InfoqParser infoqParser;
  private final ThreeDNewsParser threeDNewsParser;

  private final RSSParser rssParser;

  @Value("${parser.limit}")
  private int limitArticleCount;

  @Value("${parser.maxDescription}")
  private int maxDescription;

  @Scheduled(fixedRateString = "${parser.fixedRateMain}")
  public void runMainParsers() {
    SiteParser parser = threeDNewsParser;

    for (DefaultWebsiteIds value : DefaultWebsiteIds.values()) {
      try {
        switch (value) {
          case HI_TECH -> parser = hiTechParser;
          case INFOQ -> parser = infoqParser;
          case THREE_D -> parser = threeDNewsParser;
        }

        Optional<Website> website = websiteRepository.findByNameAndOwnerIsNull(value.getName());
        if (website.isPresent()) {
          parseSite(website.get(), parser);
        }
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

  @Transactional
  protected void parseSite(Website website, SiteParser parser) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
    List<String> newArticlesUrls = parser.getArticleLinks();

    for (String newArticleUrl : newArticlesUrls) {
      if (!oldArticlesUrls.contains(newArticleUrl)) {
        Optional<ArticleDTO> article = parser.getArticle(newArticleUrl);

        article.ifPresent(articleDTO -> saveArticle(articleDTO, website));
      }
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
    if (description.length() > maxDescription) {
      //todo сделать сокращение с помощью регулярок
      return description.substring(0, maxDescription);
    }
    return description;
  }

  private Set<Category> getTextCategories(String text) {
    //todo
    return new HashSet<>(categoryRepository.findAll());
  }
}
