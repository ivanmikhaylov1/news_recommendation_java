package com.example.demo.controller;


import com.example.demo.domain.dto.request.IdRequest;
import com.example.demo.domain.model.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Categories operations", description = "Управление категориями")
@RequestMapping("/api/categories")
public interface CategoriesOperations {

  @Operation(summary = "Получить стандартные категории")
  @ApiResponse(responseCode = "200", description = "Список стандартных категорий")
  @GetMapping
  ResponseEntity<List<Category>> getDefaultCategories();

  @Operation(summary = "Получить пользовательские категории")
  @ApiResponse(responseCode = "200", description = "Список пользовательских категорий")
  @GetMapping("/my")
  ResponseEntity<List<Category>> getUserCategories();

  @Operation(summary = "Создать категорию")
  @ApiResponse(responseCode = "201", description = "Категория создана")
  @PostMapping
  ResponseEntity<Category> createCategory(@RequestBody @Valid Category category);

  @Operation(summary = "Выбрать категорию")
  @ApiResponse(responseCode = "200", description = "Категория выбрана")
  @PutMapping
  ResponseEntity<Void> chooseCategory(@RequestBody @Valid IdRequest idRequest);
}

