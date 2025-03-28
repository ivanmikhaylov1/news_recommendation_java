package com.example.demo.parser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.example.entity.Article;
import org.example.entity.Catalog;
import org.example.entity.User;
import org.example.entity.Website;
import org.example.ml.TextClassifier;
import org.example.parser.sites.HiTechParser;
import org.example.parser.sites.InfoqParser;
import org.example.parser.sites.RSSParser;
import org.example.parser.sites.ThreeDNewsParser;
import org.example.repository.Implementation.ArticleRepositoryImpl;
import org.example.repository.Implementation.CatalogRepositoryImpl;
import org.example.repository.Implementation.UserRepositoryImpl;
import org.example.repository.interfaces.ArticleRepository;
import org.example.repository.interfaces.CatalogRepository;
import org.example.repository.interfaces.UserRepository;
import org.example.utils.DataSourceConfig;

public class ScheduledParserExecutor {
  private final DataSource dataSource = DataSourceConfig.getDataSource();
  private final ArticleRepository articleRepository = new ArticleRepositoryImpl();

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public ScheduledParserExecutor() {
    scheduler.scheduleAtFixedRate(this::parseAndClassifyArticles, 0, 5, TimeUnit.MINUTES);
  }

  private void parseAndClassifyArticles() {
    UUID userId = UUID.fromString("469f968d-f2ba-4e94-87f0-8ad8acb27923");

    try {
      // Парсим статьи пользователя
      List<Article> articles = parseUserWebsites(userId);

      // Классифицируем статьи
      List<Article> classifiedArticles = classifyArticles(articles);

      classifiedArticles.forEach(article -> {
        System.out.println("Classified Article: " + article.name());
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private List<Article> parseUserWebsites(UUID userId) {
    List<Article> articles = new ArrayList<>();
    List<Website> userWebsites = getUserWebsites(userId);

    for (Website website : userWebsites) {
      SiteParse parser = getParserForWebsite(website.getUrl());
      articles.addAll(parser.parseLastArticles());
    }

    return articles;
  }

  private List<Website> getUserWebsites(UUID userId) {
    List<Website> userWebsites = new ArrayList<>();
    String sql = "SELECT website_id, url, name FROM user_website WHERE user_id = ?";

    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setObject(1, userId);

      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          UUID websiteId = (UUID) resultSet.getObject("website_id");
          String url = resultSet.getString("url");
          String name = resultSet.getString("name");
          userWebsites.add(new Website(websiteId, name, url, userId));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return userWebsites;
  }

  private List<Article> classifyArticles(List<Article> articles) throws Exception {
    List<Catalog> catalogs = getAllUniqCatalogs();

    TextClassifier textClassifier = new TextClassifier(catalogs);
    List<Article> classifiedArticles = new ArrayList<>();

    for (Article article : articles) {
      Map<Catalog, Float> classifiedArticle = textClassifier.predictTopicsForText(article.description(), catalogs);

      classifiedArticle.entrySet().stream()
              .filter(entry -> entry.getValue() > 0.10)
              .forEach(entry -> {
                Catalog bestCatalog = entry.getKey();
                articleRepository.saveArticle(article);
                articleRepository.saveArticleCategory(article.id(), bestCatalog.getId(), article.websiteId());
                classifiedArticles.add(article);
              });
    }

    return classifiedArticles;
  }

  private List<Catalog> getAllUniqCatalogs() {
    List<Catalog> catalogs = new ArrayList<>();
    UserRepository userRepository = new UserRepositoryImpl();
    CatalogRepository catalogRepository = new CatalogRepositoryImpl();
    List<User> users = userRepository.findAll();

    for (User user : users) {
      catalogs.addAll(catalogRepository.getUserCatalogs(user.getId()));
    }
    return catalogs;
  }

  private SiteParse getParserForWebsite(String url) {
    if (url.contains("hi-tech")) {
      return new HiTechParser();
    } else if (url.contains("infoq")) {
      return new InfoqParser();
    } else if (url.contains("3dnews")) {
      return new ThreeDNewsParser();
    } else {
      return new RSSParser(url);
    }
  }
}
