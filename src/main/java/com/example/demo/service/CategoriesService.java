package com.example.demo.service;

import com.example.demo.domain.dto.CategoryDTO;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriesService {

  private final CategoryRepository repository;
  private final UserRepository userRepository;

  public List<CategoryDTO> getDefaultCategories() {
    List<Category> categories = repository.findByOwnerIsNull();
    return categories.stream()
        .map(category -> new CategoryDTO(category.getId(), category.getName()))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<CategoryDTO> getUserCategories(User user) {
    List<Category> categories = repository.findByUsersContaining(user);
    return categories.stream()
        .map(category -> new CategoryDTO(category.getId(), category.getName()))
        .collect(Collectors.toList());
  }

  @Transactional
  public void chooseCategory(User user, Long categoryId) {
    Optional<Category> category = repository.findById(categoryId);
    if (category.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category with this ID not found");
    }
    user.getCategories().add(category.get());
    userRepository.save(user);
  }

  @Transactional
  public void removeCategory(User user, Long categoryId) {
    Optional<Category> category = repository.findByIdAndUsersContaining(categoryId, user);
    if (category.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category with this ID not found");
    }
    user.getCategories().remove(category.get());
    userRepository.save(user);
  }

  public CategoryDTO createCategory(User user, Category category) {
    category.setOwner(user);
    repository.save(category);
    return new CategoryDTO(category.getId(), category.getName());
  }
}