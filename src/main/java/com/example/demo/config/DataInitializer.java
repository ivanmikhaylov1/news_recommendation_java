package com.example.demo.config;

import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
  private final CategoryRepository categoryRepository;
  private final WebsiteRepository websiteRepository;

  @Override
  public void run(String... args) {
    if (categoryRepository.count() == 0) {
      initializeCategories();
    }

    if (websiteRepository.count() == 0) {
      initializeWebsites();
    }
  }

  private void initializeCategories() {
    Category technology = Category.builder()
        .name("Technology")
        .build();

    Category science = Category.builder()
        .name("Science")
        .build();

    Category health = Category.builder()
        .name("Health")
        .build();

    categoryRepository.save(technology);
    categoryRepository.save(science);
    categoryRepository.save(health);
  }

  private void initializeWebsites() {
    Website techCrunch = Website.builder()
        .name("TechCrunch")
        .url("https://techcrunch.com")
        .build();

    Website scienceDaily = Website.builder()
        .name("ScienceDaily")
        .url("https://www.sciencedaily.com")
        .build();

    Website healthline = Website.builder()
        .name("Healthline")
        .url("https://www.healthline.com")
        .build();

    websiteRepository.save(techCrunch);
    websiteRepository.save(scienceDaily);
    websiteRepository.save(healthline);
  }
}