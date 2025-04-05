package com.example.demo.configuration;

import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WebsiteRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {
  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final WebsiteRepository websiteRepository;

  @PostConstruct
  @Transactional
  public void init() {
    try {
      User testUser = new User();
      testUser.setId(123456789L);
      userRepository.save(testUser);
      createDefaultCategories();
      createDefaultWebsites();

    } catch (Exception e) {
      log.error("Ошибка при инициализации тестовых данных: {}", e.getMessage(), e);
    }
  }

  private void createDefaultCategories() {
    if (categoryRepository.count() == 0) {
      Category category1 = new Category();
      category1.setName("DevOps");
      categoryRepository.save(category1);

      Category category2 = new Category();
      category2.setName("Frontend");
      categoryRepository.save(category2);

      Category category3 = new Category();
      category3.setName("Backend");
      categoryRepository.save(category3);

      Category category4 = new Category();
      category4.setName("Data Science");
      categoryRepository.save(category4);

      Category category5 = new Category();
      category5.setName("Machine Learning");
      categoryRepository.save(category5);

      Category category6 = new Category();
      category6.setName("Cybersecurity");
      categoryRepository.save(category6);

      Category category7 = new Category();
      category7.setName("Cloud Computing");
      categoryRepository.save(category7);

      Category category8 = new Category();
      category8.setName("Mobile Development");
      categoryRepository.save(category8);

      Category category9 = new Category();
      category9.setName("Game Development");
      categoryRepository.save(category9);

      Category category10 = new Category();
      category10.setName("Databases");
      categoryRepository.save(category10);
    }
  }

  private void createDefaultWebsites() {
    if (websiteRepository.count() == 0) {
      Website hiTech = new Website();
      hiTech.setName("Hi-Tech");
      hiTech.setUrl("https://hi-tech.mail.ru");
      websiteRepository.save(hiTech);

      Website infoq = new Website();
      infoq.setName("Infoq");
      infoq.setUrl("https://www.infoq.com");
      websiteRepository.save(infoq);

      Website threeD = new Website();
      threeD.setName("3Dnews");
      threeD.setUrl("https://3dnews.ru");
      websiteRepository.save(threeD);
    }
  }
}
