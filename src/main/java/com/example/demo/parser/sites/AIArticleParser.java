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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIArticleParser implements SiteParser {
  private static final Dotenv dotenv = Dotenv.load();
  private static final String KEY = dotenv.get("KEY");
  private static final String ENDPOINT = dotenv.get("ENDPOINT");
  private static final String MODEL = dotenv.get("MODEL");
  private static final long TIMEOUT = 15000;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ArticleClassifier articleClassifier;

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

  public ParsedArticle parseArticle(String html) {
    try {
      html = html.replaceAll("(?s)<script[^>]*>.*?</script>", "")
          .replaceAll("(?s)<style[^>]*>.*?</style>", "")
          .replaceAll("<link[^>]*>", "")
          .replaceAll("(?s)<noscript[^>]*>.*?</noscript>", "")
          .replaceAll("(?s)<!--.*?-->", "")
          .replaceAll("<[^>]+>", " ")
          .replaceAll("\\s+", " ")
          .trim();

      int maxLength = 8000;
      if (html.length() > maxLength) {
        html = html.substring(0, maxLength) + "...";
      }

      String systemPrompt = "Ты — эксперт по парсингу новостных статей. Твоя задача — извлечь основную информацию из HTML кода статьи.\n" +
          "Ты ДОЛЖЕН вернуть JSON объект в формате:\n" +
          "{\n" +
          "  \"title\": \"заголовок статьи\",\n" +
          "  \"description\": \"краткое описание статьи\",\n" +
          "  \"content\": \"основной текст статьи\",\n" +
          "  \"author\": \"автор статьи (если есть)\",\n" +
          "  \"publishedDate\": \"дата публикации (если есть)\"\n" +
          "}\n\n" +
          "Правила:\n" +
          "1. Извлекай только релевантную информацию\n" +
          "2. Игнорируй рекламу, меню, футеры и другие элементы интерфейса\n" +
          "3. Описание должно быть кратким (до 200 символов)\n" +
          "4. Если какое-то поле не найдено, оставь его пустым\n" +
          "5. Удаляй HTML теги из текста\n" +
          "6. Сохраняй форматирование текста (абзацы, списки)\n" +
          "7. Если текст слишком длинный, обрезай его до разумного размера\n" +
          "8. ВСЕГДА экранируй специальные символы в JSON (\\n, \\r, \\t, \\\")\n" +
          "9. Дата публикации должна быть в формате ISO 8601 (YYYY-MM-DDTHH:mm:ssZ)\n" +
          "10. Заголовок должен быть первым значимым текстом в статье\n\n" +
          "Пример ответа:\n" +
          "{\n" +
          "  \"title\": \"Новый iPhone получил революционную камеру\",\n" +
          "  \"description\": \"Apple представила новый iPhone с улучшенной системой камер и искусственным интеллектом для обработки фотографий.\",\n" +
          "  \"content\": \"Компания Apple представила новый iPhone...\",\n" +
          "  \"author\": \"Иван Петров\",\n" +
          "  \"publishedDate\": \"2024-05-06T12:00:00Z\"\n" +
          "}";

      String response = askAI(systemPrompt, html);
      ParsedArticle article = parseResponse(response);
      if (!article.getTitle().isEmpty()) {
        Article articleForClassification = new Article();
        articleForClassification.setName(article.getTitle());
        articleForClassification.setDescription(article.getDescription());
        List<Category> categories = articleClassifier.classifyArticle(articleForClassification);
        if (!categories.isEmpty()) {
          log.info("Статья '{}' классифицирована по категориям: {}",
              article.getTitle(),
              categories.stream().map(Category::getName).reduce((a, b) -> a + ", " + b).orElse(""));
        }
      }

      return article;
    } catch (Exception e) {
      log.error("Ошибка при парсинге статьи: {}", e.getMessage());
      return new ParsedArticle();
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
      int startIdx = response.indexOf("{");
      int endIdx = response.lastIndexOf("}") + 1;
      if (startIdx == -1 || endIdx <= startIdx) {
        log.error("Не найден JSON объект в ответе: {}", response);
        return new ParsedArticle();
      }

      String json = response.substring(startIdx, endIdx);
      json = json.replace("\\n", " ")
          .replace("\\r", " ")
          .replace("\\t", " ")
          .replaceAll("\\s+", " ")
          .trim();

      try {
        JsonNode root = objectMapper.readTree(json);
        ParsedArticle article = new ParsedArticle();
        if (root.has("title") && !root.get("title").asText().trim().isEmpty()) {
          article.setTitle(root.get("title").asText().trim());
        }

        if (root.has("description") && !root.get("description").asText().trim().isEmpty()) {
          article.setDescription(root.get("description").asText().trim());
        }

        if (root.has("content") && !root.get("content").asText().trim().isEmpty()) {
          article.setContent(root.get("content").asText().trim());
        }


        if (root.has("author") && !root.get("author").asText().trim().isEmpty()) {
          article.setAuthor(root.get("author").asText().trim());
        }

        if (root.has("publishedDate") && !root.get("publishedDate").asText().trim().isEmpty()) {
          article.setPublishedDate(root.get("publishedDate").asText().trim());
        }


        if (article.getTitle().isEmpty() && article.getDescription().isEmpty()) {
          log.error("Статья не содержит ни заголовка, ни описания");
          return new ParsedArticle();
        }

        return article;
      } catch (Exception e) {
        log.error("Ошибка при парсинге JSON: {}. Ответ нейросети: {}", e.getMessage(), json);
        return new ParsedArticle();
      }
    } catch (Exception e) {
      log.error("Ошибка при парсинге ответа нейросети: {}", e.getMessage());
      log.error("Ответ нейросети: {}", response);
      return new ParsedArticle();
    }
  }

  @lombok.Data
  public static class ParsedArticle {
    private String title = "";
    private String description = "";
    private String content = "";
    private String author = "";
    private String publishedDate = "";
  }
} 