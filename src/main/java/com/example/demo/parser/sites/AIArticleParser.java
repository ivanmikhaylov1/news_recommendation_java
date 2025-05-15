package com.example.demo.parser.sites;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.example.demo.AIClassificator.ArticleClassifier;
import com.example.demo.domain.dto.ArticleDTO;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.parser.SiteParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AIArticleParser implements SiteParser {
  private static final Dotenv dotenv = Dotenv.load();
  private static final String KEY = dotenv.get("KEY");
  private static final String ENDPOINT = dotenv.get("ENDPOINT");
  private static final String MODEL = dotenv.get("MODEL");
  private static final long TIMEOUT = 15000;
  private static final int MAX_REQUESTS = 5;
  private final AtomicInteger parserRequestsCount = new AtomicInteger(0);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ArticleClassifier articleClassifier;
  private final ArticlesRepository articlesRepository;
  private final CategoryRepository categoryRepository;
  private Website website;

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
    // TODO: Реализовать получение последних статей
    return new ArrayList<>();
  }

  @Override
  public CompletableFuture<List<String>> getArticleLinks() {
    return CompletableFuture.supplyAsync(() -> {
      // TODO: Реализовать получение списка ссылок на статьи
      return new ArrayList<>();
    });
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

      String cleanedHtml = cleanHtml(html);
      if (cleanedHtml.length() > 15000) {
        cleanedHtml = cleanedHtml.substring(0, 15000);
      }

      String response = askAI(getSystemPrompt(categoryNames), cleanedHtml);
      ParsedArticle parsedArticle = parseResponse(response);

      if (!parsedArticle.getTitle().isEmpty() && !parsedArticle.getDescription().isEmpty()) {
        String contentHash = String.valueOf(
            (parsedArticle.getTitle() + parsedArticle.getDescription()).hashCode()
        );
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
            .filter(category -> parsedArticle.getCategories().contains(category.getName()))
            .collect(Collectors.toSet());
        
        if (!categories.isEmpty()) {
          article.setCategories(categories);
          articlesRepository.save(article);
          log.info("Статья '{}' сохранена с категориями: {}", 
              article.getName(), 
              categories.stream().map(Category::getName).collect(Collectors.joining(", ")));
        } else {
          log.info("Для статьи '{}' не найдено подходящих категорий", article.getName());
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

    ChatCompletionsClient client = new ChatCompletionsClientBuilder()
        .credential(new AzureKeyCredential(KEY))
        .endpoint(ENDPOINT)
        .buildClient();

    List<ChatRequestMessage> chatMessages = Arrays.asList(
        new ChatRequestSystemMessage(systemPrompt),
        new ChatRequestUserMessage(userPrompt)
    );

    ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages);
    options.setModel(MODEL);
    options.setTemperature(0.2);
    options.setMaxTokens(8000);
    options.setTopP(0.8);
    options.setFrequencyPenalty(0.5);
    options.setPresencePenalty(0.5);

    final long startTime = System.currentTimeMillis();
    ChatCompletions completions = client.complete(options);
    String response = completions.getChoices().get(0).getMessage().getContent();

    log.info("Получен ответ от нейросети:\n{}", response);

    if (System.currentTimeMillis() - startTime > TIMEOUT) {
      throw new TimeoutException("Превышено время ожидания ответа от нейросети");
    }

    return response;
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
          categories.add(categoryNode.asText());
        }
      }
      
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
