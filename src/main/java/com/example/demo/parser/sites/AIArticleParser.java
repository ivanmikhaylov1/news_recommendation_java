package com.example.demo.parser.sites;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.example.demo.AIClassificator.ArticleClassifier;
import com.example.demo.AIClassificator.AICache;
import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.parser.SiteParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.domain.model.Website;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIArticleParser implements SiteParser {
  private static final Dotenv dotenv = Dotenv.load();
  private static final long TIMEOUT = 300000;
  private static final int MAX_REQUESTS = 5;
  private final AtomicInteger parserRequestsCount = new AtomicInteger(0);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ArticleClassifier articleClassifier;
  private final ArticlesRepository articlesRepository;
  private final CategoryRepository categoryRepository;
  private final AICache aiCache;
  private Website website;

  @Value("${ai.key.parser.value}")
  private String KEY;

  @Value("${ai.endpoint}")
  private String ENDPOINT;

  @Value("${ai.model}")
  private String MODEL;

  public void setWebsite(Website website) {
    this.website = website;
  }

  private String getSystemPrompt(List<String> categoryNames) {
    return """
        Ты - эксперт по анализу новостных статей. Твоя задача - извлечь основную информацию из HTML-страницы и классифицировать статью.
        
        Извлеки следующую информацию:
        1. Заголовок статьи
        2. Описание/краткое содержание
        3. Дату публикации
        4. Категории статьи (выбери из списка: %s)
        
        Правила классификации:
        - Выбери не более 3 наиболее подходящих категорий из списка
        - Используй ТОЛЬКО категории из указанного списка, без изменений и дополнений
        - Если статья не подходит ни под одну категорию, верни пустой массив категорий
        - Категории должны точно соответствовать названиям из списка
        - ВАЖНО: используй ТОЧНО эти названия категорий, без изменений: %s
        
        Верни информацию в формате JSON:
        {
          "title": "Заголовок статьи",
          "description": "Описание статьи",
          "publishedDate": "Дата публикации",
          "categories": ["Категория1", "Категория2"]
        }
        
        Если не можешь определить какую-то информацию, верни пустую строку для этого поля.
        ВАЖНО: верни ТОЛЬКО JSON объект без дополнительного форматирования, обратных кавычек и других символов.
        """.formatted(String.join(", ", categoryNames), String.join(", ", categoryNames));
  }

  @Override
  public String getNAME() {
    return "AI";
  }

  @Override
  public String getLanguage() {
    return "ru";
  }

  @Override
  public List<ArticleDTO> parseLastArticles() {
    return new ArrayList<>();
  }

  @Override
  public CompletableFuture<List<String>> getArticleLinks() {
    return CompletableFuture.supplyAsync(ArrayList::new);
  }

  @Override
  public CompletableFuture<Optional<ArticleDTO>> getArticle(String link) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        ParsedArticle parsedArticle = parseArticle(link);
        if (parsedArticle.getTitle().isEmpty() && parsedArticle.getDescription().isEmpty()) {
          return Optional.empty();
        }

        ArticleDTO articleDTO = ArticleDTO.builder()
            .name(parsedArticle.getTitle())
            .description(parsedArticle.getDescription())
            .date(parsedArticle.getPublishedDate())
            .url(link)
            .build();

        return Optional.of(articleDTO);
      } catch (Exception e) {
        log.error("Ошибка при получении статьи по ссылке {}: {}", link, e.getMessage());
        return Optional.empty();
      }
    });
  }

  private String cleanHtml(String html) {
    return html.replaceAll("(?s)<script[^>]*>.*?</script>", "")
        .replaceAll("(?s)<style[^>]*>.*?</style>", "")
        .replaceAll("<link[^>]*>", "")
        .replaceAll("(?s)<noscript[^>]*>.*?</noscript>", "")
        .replaceAll("(?s)<!--.*?-->", "")
        .replaceAll("<[^>]+>", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public ParsedArticle parseArticle(String html) {
    if (website == null) {
      log.error("Website не установлен");
      return new ParsedArticle("", "", "", List.of());
    }

    try {
      List<Category> allCategories = categoryRepository.findAll();
      if (allCategories.isEmpty()) {
        log.warn("Нет категорий в базе данных");
        return new ParsedArticle("", "", "", List.of());
      }

      List<String> categoryNames = allCategories.stream()
          .map(Category::getName)
          .filter(name -> !name.startsWith("/"))
          .filter(name -> name.matches("^[a-zA-Zа-яА-Я0-9\\s]+$"))
          .toList();

      if (categoryNames.isEmpty()) {
        log.warn("Нет валидных категорий для классификации");
        return new ParsedArticle("", "", "", List.of());
      }

      log.info("Доступные категории для классификации: {}", String.join(", ", categoryNames));

      String cleanedHtml = cleanHtml(html);
      if (cleanedHtml.length() > 15000) {
        cleanedHtml = cleanedHtml.substring(0, 15000);
      }

      String systemPrompt = getSystemPrompt(categoryNames);
      String cacheKey = systemPrompt + "|" + cleanedHtml;
      
      if (aiCache.contains(cacheKey)) {
        log.info("Найдено в кэше, используем сохраненный ответ");
        String cachedResponse = aiCache.get(cacheKey);
        ParsedArticle parsedArticle = parseResponse(cachedResponse);
        
        if (!parsedArticle.getTitle().isEmpty() && !parsedArticle.getDescription().isEmpty()) {
          if (articlesRepository.existsByNameAndDescription(parsedArticle.getTitle(), parsedArticle.getDescription())) {
            log.info("Статья '{}' уже существует в базе данных", parsedArticle.getTitle());
            return parsedArticle;
          }

          String contentHash = String.valueOf(
              (parsedArticle.getTitle() + parsedArticle.getDescription()).hashCode()
          );
          Optional<Article> existingArticleByHash = articlesRepository.findByUrlContaining(contentHash);
          if (existingArticleByHash.isPresent()) {
            Article existingArticle = existingArticleByHash.get();
            if (existingArticle.getCategories() == null || existingArticle.getCategories().isEmpty()) {
              log.info("Статья '{}' уже существует в базе данных без категорий, пропускаем повторную обработку", parsedArticle.getTitle());
              return parsedArticle;
            }

            log.info("Статья с похожим контентом уже существует в базе данных");
            return parsedArticle;
          }
        }
        
        return parsedArticle;
      }

      String response = askAI(systemPrompt, cleanedHtml);
      ParsedArticle parsedArticle = parseResponse(response);

      if (!parsedArticle.getTitle().isEmpty() && !parsedArticle.getDescription().isEmpty()) {
        Optional<Article> existingArticleByTitle = articlesRepository.findByName(parsedArticle.getTitle());
        if (existingArticleByTitle.isPresent()) {
          Article existingArticle = existingArticleByTitle.get();
          if (existingArticle.getId() == null) {
            log.info("Статья '{}' найдена, но не сохранена в базе данных, продолжаем обработку", parsedArticle.getTitle());
          } else {
            if (existingArticle.getCategories() == null || existingArticle.getCategories().isEmpty()) {
              log.info("Статья '{}' уже существует в базе данных без категорий, пропускаем повторную обработку", parsedArticle.getTitle());
              return parsedArticle;
            }

            log.info("Статья с заголовком '{}' уже существует в базе данных", parsedArticle.getTitle());
            return parsedArticle;
          }
        }

        Optional<Article> existingArticleByDescription = articlesRepository.findByDescription(parsedArticle.getDescription());
        if (existingArticleByDescription.isPresent()) {
          Article existingArticle = existingArticleByDescription.get();
          if (existingArticle.getId() == null) {
            log.info("Статья с описанием '{}' найдена, но не сохранена в базе данных, продолжаем обработку", parsedArticle.getDescription());
          } else {
            if (existingArticle.getCategories() == null || existingArticle.getCategories().isEmpty()) {
              log.info("Статья с описанием '{}' уже существует в базе данных без категорий, пропускаем повторную обработку", parsedArticle.getDescription());
              return parsedArticle;
            }

            log.info("Статья с описанием '{}' уже существует в базе данных", parsedArticle.getDescription());
            return parsedArticle;
          }
        }

        String contentHash = String.valueOf(
            (parsedArticle.getTitle() + parsedArticle.getDescription()).hashCode()
        );
        Optional<Article> existingArticleByHash = articlesRepository.findByUrlContaining(contentHash);
        if (existingArticleByHash.isPresent()) {
          Article existingArticle = existingArticleByHash.get();
          if (existingArticle.getId() == null) {
            log.info("Статья с похожим контентом найдена, но не сохранена в базе данных, продолжаем обработку");
          } else {
            if (existingArticle.getCategories() == null || existingArticle.getCategories().isEmpty()) {
              log.info("Статья с похожим контентом уже существует в базе данных без категорий, пропускаем повторную обработку");
              return parsedArticle;
            }

            log.info("Статья с похожим контентом уже существует в базе данных");
            return parsedArticle;
          }
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String articleUrl = website.getUrl() + "#" + contentHash + "_" + timestamp;

        Article article = Article.builder()
            .name(parsedArticle.getTitle())
            .description(parsedArticle.getDescription())
            .date(LocalDateTime.now())
            .siteDate(parsedArticle.getPublishedDate())
            .url(articleUrl)
            .website(website)
            .build();

        Set<Category> categories = allCategories.stream()
            .filter(category -> {
              boolean matches = parsedArticle.getCategories().stream()
                  .anyMatch(parsedCategory -> 
                      category.getName().equalsIgnoreCase(parsedCategory.trim()));
              if (matches) {
                log.debug("Найдено соответствие категории: {}", category.getName());
              }
              return matches;
            })
            .collect(Collectors.toSet());
        
        if (!categories.isEmpty()) {
          article.setCategories(categories);
          articlesRepository.save(article);
          log.info("Статья '{}' сохранена с категориями: {}", 
              article.getName(), 
              categories.stream().map(Category::getName).collect(Collectors.joining(", ")));
        } else {
          log.warn("Для статьи '{}' не найдено соответствий среди категорий. " +
              "Полученные категории: {}, Доступные категории: {}", 
              article.getName(),
              String.join(", ", parsedArticle.getCategories()),
              allCategories.stream().map(Category::getName).collect(Collectors.joining(", ")));
          articlesRepository.save(article);
          log.info("Статья '{}' сохранена без категорий", article.getName());
        }
      }

      return parsedArticle;
    } catch (Exception e) {
      log.error("Ошибка при парсинге статьи: {}", e.getMessage());
      return new ParsedArticle("", "", "", List.of());
    }
  }

  private String askAI(String systemPrompt, String userPrompt) throws TimeoutException {
    log.info("Отправка запроса к нейросети:\nСистемный промпт: {}\nПользовательский промпт: {}", systemPrompt, userPrompt);

    try {
      Map<String, Object> system = Map.of(
          "role", "system",
          "content", systemPrompt);
      Map<String, Object> user = Map.of(
          "role", "user",
          "content", userPrompt);

      List<Map<String, Object>> messages = List.of(system, user);
      Map<String, Object> body = new HashMap<>();
      body.put("model", MODEL);
      body.put("messages", messages);
      body.put("temperature", 0.2);
      body.put("top_p", 0.8);
      body.put("frequency_penalty", 0.5);
      body.put("presence_penalty", 0.5);
      body.put("max_tokens", 8000);
      body.put("min_tokens", 100);

      String requestBody = objectMapper.writeValueAsString(body);

      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ENDPOINT + "chat/completions"))
          .header("Authorization", "Bearer " + KEY)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      final long startTime = System.currentTimeMillis();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      String responseBody = response.body();

      log.info("Получен ответ от нейросети:\n{}", responseBody);

      if (System.currentTimeMillis() - startTime > TIMEOUT) {
        throw new TimeoutException("Превышено время ожидания ответа от нейросети");
      }

      JsonNode root = objectMapper.readTree(responseBody);
      
      if (!root.has("choices") || !root.get("choices").isArray() || root.get("choices").size() == 0) {
        log.error("Неверная структура ответа от нейросети: отсутствует массив choices");
        throw new RuntimeException("Неверная структура ответа от нейросети");
      }

      JsonNode choices = root.get("choices");
      JsonNode firstChoice = choices.get(0);
      
      if (firstChoice == null || !firstChoice.has("message") || !firstChoice.get("message").has("content")) {
        log.error("Неверная структура ответа от нейросети: отсутствует message.content");
        throw new RuntimeException("Неверная структура ответа от нейросети");
      }

      String content = firstChoice.get("message").get("content").asText();
      
      log.debug("Получено содержимое от нейросети до обработки: {}", content);
      
      if (content == null || content.trim().isEmpty()) {
        log.error("Получено пустое содержимое от нейросети");
        throw new RuntimeException("Пустой ответ от нейросети");
      }
      
      content = content.replaceAll("```json\\s*", "")
          .replaceAll("```\\s*$", "")
          .replaceAll("`", "")
          .trim();
          
      log.debug("Содержимое после обработки: {}", content);
      
      if (content.isEmpty()) {
        log.error("После обработки получена пустая строка");
        throw new RuntimeException("Пустой ответ после обработки");
      }

      String cacheKey = systemPrompt + "|" + userPrompt;
      aiCache.put(cacheKey, content);
          
      return content;
    } catch (Exception e) {
      log.error("Ошибка при обращении к нейросети: {}", e.getMessage());
      throw new RuntimeException("Ошибка при обращении к нейросети", e);
    }
  }

  private ParsedArticle parseResponse(String response) {
    try {
      response = response.replaceAll("```json\\s*", "")
          .replaceAll("```\\s*$", "")
          .replaceAll("`", "")
          .trim();

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(response);
      
      String title = root.path("title").asText("");
      String description = root.path("description").asText("");
      String publishedDate = root.path("publishedDate").asText("");
      
      List<String> categories = new ArrayList<>();
      JsonNode categoriesNode = root.path("categories");
      if (categoriesNode.isArray()) {
        for (JsonNode categoryNode : categoriesNode) {
          String category = categoryNode.asText().trim();
          if (!category.isEmpty()) {
            categories.add(category);
            log.debug("Добавлена категория: {}", category);
          }
        }
      }
      
      log.info("Распознанные категории для статьи '{}': {}", title, String.join(", ", categories));
      
      return new ParsedArticle(title, description, publishedDate, categories);
    } catch (Exception e) {
      log.error("Ошибка при разборе ответа от AI: {}\nОтвет: {}", e.getMessage(), response);
      return new ParsedArticle("", "", "", List.of());
    }
  }

  @lombok.Data
  public static class ParsedArticle {
    private String title = "";
    private String description = "";
    private String publishedDate = "";
    private List<String> categories = new ArrayList<>();
    private String url = "";

    public ParsedArticle(String title, String description, String publishedDate, List<String> categories) {
      this.title = title;
      this.description = description;
      this.publishedDate = publishedDate;
      this.categories = categories;
    }

    public ParsedArticle() {
    }
  }
}
