package com.example.demo.repository;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.ArticleReaction;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ArticleReactionRepositoryTest {

  @Autowired
  private ArticleReactionRepository articleReactionRepository;

  @Autowired
  private ArticlesRepository articlesRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private WebsiteRepository websiteRepository;

  private Article article;
  private ArticleReaction articleReaction;

  @BeforeEach
  void setUp() {
    Website website = websiteRepository.save(Website.builder()
        .name("Test Website")
        .url("https://test.com")
        .build());

    Category category = categoryRepository.save(Category.builder()
        .name("Test Category")
        .build());

    article = articlesRepository.save(Article.builder()
        .name("Test Article")
        .description("Test Description")
        .date(LocalDateTime.now())
        .url("https://test.com/article")
        .website(website)
        .category(category)
        .build());

    articleReaction = ArticleReaction.builder()
        .article(article)
        .likesCount(0)
        .dislikesCount(0)
        .rating(0.0f)
        .build();
  }

  @Test
  void shouldSaveArticleReaction() {
    ArticleReaction savedReaction = articleReactionRepository.save(articleReaction);
    assertThat(savedReaction).isNotNull();
    assertThat(savedReaction.getId()).isNotNull();
    assertThat(savedReaction.getArticle()).isEqualTo(article);
    assertThat(savedReaction.getLikesCount()).isZero();
    assertThat(savedReaction.getDislikesCount()).isZero();
    assertThat(savedReaction.getRating()).isZero();
  }

  @Test
  void shouldFindByArticleId() {
    articleReactionRepository.save(articleReaction);
    ArticleReaction foundReaction = articleReactionRepository.findByArticleId(article.getId());
    assertThat(foundReaction).isNotNull();
    assertThat(foundReaction.getArticle().getId()).isEqualTo(article.getId());
  }

  @Test
  void shouldFindTop10Articles() {
    articleReactionRepository.deleteAll();
    articlesRepository.deleteAll();

    for (int i = 0; i < 15; i++) {
      Website website = websiteRepository.save(Website.builder()
          .name("Website " + i)
          .url("https://test" + i + ".com")
          .build());

      Category category = categoryRepository.save(Category.builder()
          .name("Category " + i)
          .build());

      Article article = articlesRepository.save(Article.builder()
          .name("Article " + i)
          .description("Description " + i)
          .date(LocalDateTime.now())
          .url("https://test.com/article" + i)
          .website(website)
          .category(category)
          .build());

      ArticleReaction reaction = ArticleReaction.builder()
          .article(article)
          .likesCount(14 - i)
          .dislikesCount(0)
          .rating((float) (14 - i))
          .build();

      articleReactionRepository.save(reaction);
    }

    List<Article> top10Articles = articleReactionRepository.findTop10();
    assertThat(top10Articles).hasSize(10);
    assertThat(top10Articles.get(0).getName()).isEqualTo("Article 0");
    assertThat(top10Articles.get(9).getName()).isEqualTo("Article 9");
  }
}
