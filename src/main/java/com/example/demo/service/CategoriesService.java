package com.example.demo.service;

import com.example.demo.domain.dto.CategoryDTO;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void chooseCategory(User user, Long categoryId) {
    Optional<Category> categoryOpt = repository.findById(categoryId);
    if (categoryOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category with this ID not found");
    }

    Category category = categoryOpt.get();
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    if (freshUser.getCategories() == null) {
      freshUser.setCategories(new java.util.HashSet<>());
    }

    freshUser.getCategories().add(category);
    userRepository.save(freshUser);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void removeCategory(User user, Long categoryId) {
    Optional<Category> categoryOpt = repository.findByIdAndUsersContaining(categoryId, user);
    if (categoryOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category with this ID not found");
    }

    Category category = categoryOpt.get();
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    freshUser.getCategories().remove(category);
    userRepository.save(freshUser);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CategoryDTO createCategory(User user, Category category) {
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    category.setOwner(freshUser);
    Category savedCategory = repository.save(category);

    return new CategoryDTO(savedCategory.getId(), savedCategory.getName());
  }
}