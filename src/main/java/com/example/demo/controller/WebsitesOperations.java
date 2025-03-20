package com.example.demo.controller;

import com.example.demo.domain.dto.response.WebsiteResponse;
import com.example.demo.domain.model.Website;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "Websites operations", description = "Управление веб-сайтами")
@RequestMapping("/api")
public interface WebsitesOperations {

  @Operation(summary = "Получить стандартные веб-сайты")
  @ApiResponse(responseCode = "200", description = "Список стандартных сайтов")
  @GetMapping("/websites")
  ResponseEntity<List<WebsiteResponse>> getDefaultWebsites();

  @Operation(summary = "Получить пользовательские веб-сайты")
  @ApiResponse(responseCode = "200", description = "Список сайтов пользователя")
  @GetMapping("/websites/my")
  ResponseEntity<List<WebsiteResponse>> getUserWebsites();

  @Operation(summary = "Создать веб-сайт")
  @ApiResponse(responseCode = "201", description = "Веб-сайт создан")
  @PostMapping("/websites")
  ResponseEntity<WebsiteResponse> createWebsite(@RequestBody @Valid Website website);

  @Operation(summary = "Выбрать веб-сайт")
  @ApiResponse(responseCode = "200", description = "Веб-сайт выбран")
  @PostMapping("/subscriptions/websites")
  ResponseEntity<Void> chooseWebsite(@RequestParam Long websiteId);

  @Operation(summary = "Отмена выбора веб-сайта")
  @ApiResponse(responseCode = "200", description = "Выбор отменен")
  @DeleteMapping("/subscriptions/websites")
  ResponseEntity<Void> removeWebsite(@RequestParam Long websiteId);
}
