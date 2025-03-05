package com.example.demo.service;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.ArticlesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticlesService {

  private final ArticlesRepository repository;
  private final UsersService usersService;
  private final CategoriesService categoriesService;
  private final WebsitesService websitesService;

  public List<Article> getNewArticles() {
    User user = usersService.getCurrentUser();
    List<Category> categories = categoriesService.getUserCategories();
    List<Website> websites = websitesService.getUserWebsites();
    return repository.getNewArticlesForUser(user, categories, websites);
  }
}
