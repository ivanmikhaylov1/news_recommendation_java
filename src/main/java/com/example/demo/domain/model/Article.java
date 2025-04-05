package com.example.demo.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "articles")
@Schema(description = "Статья, полученная с веб-сайта")
public class Article {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "article_id_seq")
  @SequenceGenerator(name = "article_id_seq", sequenceName = "article_id_seq", allocationSize = 1)
  @Column(name = "article_id")
  @Schema(description = "Уникальный идентификатор статьи", example = "1")
  private Long id;

  @Column(nullable = false)
  @Schema(description = "Название статьи", example = "Как работать с OpenAPI")
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  @Schema(description = "Описание статьи", example = "Подробный разбор работы с OpenAPI в Spring Boot")
  private String description;

  @Column(name = "site_date", nullable = false)
  @Schema(description = "Строка даты с сайта", example = "2024-02-20")
  private String siteDate;

  @Column(nullable = false)
  @Schema(description = "Дата публикации", example = "2024-02-20T15:30:00")
  private LocalDateTime date;

  @Column(nullable = false, unique = true)
  @Schema(description = "Уникальный URL статьи", example = "https://example.com/articles/openapi-guide")
  private String url;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "website_id", nullable = false)
  @Schema(description = "Веб-сайт, на котором опубликована статья")
  private Website website;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "article_category",
      joinColumns = @JoinColumn(name = "article_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id")
  )
  @Schema(description = "Категории, к которым относится статья")
  private Set<Category> categories;
}
