package com.example.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Категория (для ответа пользователю)")
public class CategoryDTO {

  @Schema(description = "Идентификатор категории", example = "1")
  private Long id;

  @Schema(description = "Название категории", example = "Технологии")
  private String name;
}