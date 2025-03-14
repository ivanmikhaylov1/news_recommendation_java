package com.example.demo.controller;


import com.example.demo.domain.dto.response.CategoryResponse;
import com.example.demo.domain.model.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Categories operations", description = "Управление категориями")
@RequestMapping("/api")
public interface CategoriesOperations {

  @Operation(summary = "Получить стандартные категории")
  @ApiResponse(responseCode = "200", description = "Список стандартных категорий")
  @GetMapping("/categories")
  ResponseEntity<List<CategoryResponse>> getDefaultCategories();

  @Operation(summary = "Получить пользовательские категории")
  @ApiResponse(responseCode = "200", description = "Список пользовательских категорий")
  @GetMapping("/categories/my")
  ResponseEntity<List<CategoryResponse>> getUserCategories();

  @Operation(summary = "Создать категорию")
  @ApiResponse(responseCode = "201", description = "Категория создана")
  @PostMapping("/categories")
  ResponseEntity<CategoryResponse> createCategory(@RequestBody @Valid Category category);

  @Operation(summary = "Выбрать категорию")
  @ApiResponse(responseCode = "200", description = "Категория выбрана")
  @PostMapping("/subscriptions/categories/{categoryId}")
  ResponseEntity<Void> chooseCategory(@PathVariable Long categoryId);

  @Operation(summary = "Отмена выбора категории")
  @ApiResponse(responseCode = "200", description = "Выбор отменен")
  @DeleteMapping("/subscriptions/categories/{categoryId}")
  ResponseEntity<Void> removeCategory(@PathVariable Long categoryId);
}

