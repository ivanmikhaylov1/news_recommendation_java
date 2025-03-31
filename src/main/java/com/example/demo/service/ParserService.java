package com.example.demo.service;

import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.parser.DefaultWebsiteIds;
import com.example.demo.parser.SiteParser;
import com.example.demo.parser.sites.HiTechParser;
import com.example.demo.parser.sites.InfoqParser;
import com.example.demo.parser.sites.RSSParser;
import com.example.demo.parser.sites.ThreeDNewsParser;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.beans.Transient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
      switch (value) {
        case HI_TECH -> parser = hiTechParser;
        case INFOQ -> parser = infoqParser;
        case THREE_D -> parser = threeDNewsParser;
      }

      Optional<Website> website = websiteRepository.findById(value.getId());
      parseSite(website.get(), parser);
    }
  }

  @Scheduled(fixedRateString = "${parser.fixedRateRSS}")
  public void runRSSParser() {
    for (Website website : websiteRepository.findByOwnerIsNotNull()) {
      rssParser.setLink(website.getUrl());
      parseSite(website, rssParser);
    }
  }

  @Transactional
  private void parseSite(Website website, SiteParser parser) {
    List<String> oldArticlesUrls = articlesRepository.getLastArticles(website.getId(), limitArticleCount);
    List<ArticleDTO> articles = parser.parseLastArticles();

    LocalDateTime now = LocalDateTime.now();

    for (ArticleDTO articleDTO : articles) {
      if (!oldArticlesUrls.contains(articleDTO.getUrl())) {
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
    }
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

