package com.example.demo.repository;

import com.example.demo.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres"
})
class UserReactionRepositoryTest {

  @Autowired
  private UserReactionRepository userReactionRepository;

  @Autowired
  private ArticlesRepository articlesRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private WebsiteRepository websiteRepository;

  @Autowired
  private UserRepository userRepository;

  private Article article;
  private User user;
  private UserReaction userReaction;

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

    user = userRepository.save(User.builder()
        .username("testUser")
        .password("password")
        .build());

    userReaction = UserReaction.builder()
        .user(user)
        .article(article)
        .reactionType(UserReaction.ReactionType.LIKE)
        .build();
  }

  @Test
  void shouldFindByUserReaction() {
    userReactionRepository.save(userReaction);
    Optional<UserReaction> foundReaction = userReactionRepository.findByUserReaction(user.getId(), article.getId());
    assertThat(foundReaction).isPresent();
    assertThat(foundReaction.get().getUser().getId()).isEqualTo(user.getId());
    assertThat(foundReaction.get().getArticle().getId()).isEqualTo(article.getId());
    assertThat(foundReaction.get().getReactionType()).isEqualTo(UserReaction.ReactionType.LIKE);
  }

  @Test
  void shouldCountReactionsByType() {
    userReactionRepository.save(userReaction);
    User user2 = userRepository.save(User.builder()
        .username("testUser2")
        .password("password")
        .build());

    UserReaction userReaction2 = UserReaction.builder()
        .user(user2)
        .article(article)
        .reactionType(UserReaction.ReactionType.LIKE)
        .build();

    userReactionRepository.save(userReaction2);
    long likeCount = userReactionRepository.countReactionsByType(article.getId(), UserReaction.ReactionType.LIKE);
    long dislikeCount = userReactionRepository.countReactionsByType(article.getId(), UserReaction.ReactionType.DISLIKE);
    assertThat(likeCount).isEqualTo(2);
    assertThat(dislikeCount).isZero();
  }
}