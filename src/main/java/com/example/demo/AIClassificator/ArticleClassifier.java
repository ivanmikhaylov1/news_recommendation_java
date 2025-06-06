package com.example.demo.AIClassificator;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.repository.CategoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleClassifier {
  private static final Dotenv dotenv = Dotenv.load();
  private static final long TIMEOUT = 600000;
  private final CategoryRepository categoryRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AICache aiCache;

  @Value("${ai.key.classifier.value}")
  private String KEY;

  @Value("${ai.endpoint}")
  private String ENDPOINT;

  @Value("${ai.model}")
  private String MODEL;

  public List<Category> classifyArticle(Article article) {
    try {
      if (article == null || article.getDescription() == null || article.getDescription().isEmpty()) {
        return new ArrayList<>();
      }

      List<Category> allCategories = categoryRepository.findAll();
      if (allCategories.isEmpty()) {
        return new ArrayList<>();
      }

      String text = article.getDescription();
      if (text.length() > 500) {
        text = text.substring(0, 500);
      }

      text = text.replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t")
          .replace("\"", "\\\"");

      List<String> categoryNames = allCategories.stream()
          .map(Category::getName)
          .filter(name -> !name.startsWith("/"))
          .filter(name -> name.matches("^[a-zA-Zа-яА-Я0-9\\s]+$"))
          .toList();

      if (categoryNames.isEmpty()) {
        log.warn("Нет валидных категорий для классификации");
        return new ArrayList<>();
      }

      String systemPrompt = "Ты — эксперт по классификации технических статей. " +
          "Твоя задача — определить, к каким категориям относится текст. " +
          "Используй ТОЛЬКО категории из предоставленного списка: " + categoryNames + "\n" +
          "Если текст не подходит ни под одну категорию, верни пустой массив []. " +
          "Отвечай ТОЛЬКО JSON массивом категорий, без дополнительного текста. " +
          "Примеры правильных ответов:\n" +
          "Текст о разработке игр: [\"Game Development\"]\n" +
          "Текст о квантовых вычислениях: [\"Data Science\", \"Machine Learning\"]\n" +
          "Текст о безопасности: [\"Cybersecurity\"]\n" +
          "Текст о мобильных приложениях: [\"Mobile Development\"]\n" +
          "Текст о базах данных: [\"Databases\"]\n" +
          "Текст о веб-разработке: [\"Frontend\", \"Backend\"]\n" +
          "Текст о DevOps: [\"DevOps\"]\n" +
          "Текст о облачных технологиях: [\"Cloud Computing\"]\n" +
          "Текст о пользовательской категории: [\"Пользовательская категория\"]\n" +
          "Текст о нескольких темах: [\"Frontend\", \"Backend\", \"DevOps\"]\n" +
          "Текст о смешанных категориях: [\"Data Science\", \"Пользовательская категория\"]\n" +
          "Внимательно проанализируй текст и выбери наиболее подходящие категории. " +
          "Если текст относится к нескольким категориям, верни массив с несколькими категориями. " +
          "Не бойся использовать пользовательские категории, если они подходят по смыслу.";

      String cacheKey = systemPrompt + "|" + text;
      
      if (aiCache.contains(cacheKey)) {
        log.info("Найдено в кэше, используем сохраненный результат классификации");
        String cachedResponse = aiCache.get(cacheKey);
        List<String> selectedCategoryNames = extractCategories(cachedResponse, categoryNames);
        List<Category> resultCategories = new ArrayList<>();
        for (Category category : allCategories) {
          if (selectedCategoryNames.contains(category.getName())) {
            resultCategories.add(category);
          }
        }

        return resultCategories;
      }

      try {
        String response = askAI(systemPrompt, text);
        List<String> selectedCategoryNames = extractCategories(response, categoryNames);
        List<Category> resultCategories = new ArrayList<>();
        for (Category category : allCategories) {
          if (selectedCategoryNames.contains(category.getName())) {
            resultCategories.add(category);
          }
        }

        return resultCategories;
      } catch (Exception e) {
        log.error("Ошибка при обращении к нейросети: {}", e.getMessage());
        return new ArrayList<>();
      }
    } catch (Exception e) {
      log.error("Ошибка при классификации статьи: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private String askAI(String systemPrompt, String userPrompt) {
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
      body.put("max_tokens", 2000);
      body.put("min_tokens", 50);

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

  private List<String> extractCategories(String response, List<String> availableCategories) {
    try {
      response = response.trim()
          .replaceAll("\\s+", " ")
          .replaceAll("^[^\\[]*", "")
          .replaceAll("[^\\]]*$", "");

      log.debug("Очищенный ответ нейросети: {}", response);
      if (!response.contains("[")) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[[^\\]]*\\]");
        java.util.regex.Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
          response = matcher.group();
          log.debug("Найден JSON массив в тексте: {}", response);
        } else {
          pattern = java.util.regex.Pattern.compile("\\{\\s*\"category\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");
          matcher = pattern.matcher(response);
          if (matcher.find()) {
            String category = matcher.group(1);
            response = "[\"" + category + "\"]";
            log.debug("Преобразован JSON объект в массив: {}", response);
          } else {
            log.error("Не найден JSON массив в ответе нейросети: {}", response);
            return new ArrayList<>();
          }
        }
      }

      try {
        String[] categories = objectMapper.readValue(response, String[].class);
        List<String> result = new ArrayList<>();
        for (String category : categories) {
          if (category != null && !category.trim().isEmpty()) {
            String normalizedCategory = category.trim();
            boolean found = false;
            for (String availableCategory : availableCategories) {
              if (availableCategory.equalsIgnoreCase(normalizedCategory)) {
                result.add(availableCategory);
                found = true;
                break;
              }
            }

            if (!found) {
              log.warn("Нейросеть вернула недопустимую категорию: {}", normalizedCategory);
            }
          }
        }

        if (result.isEmpty()) {
          log.warn("Нейросеть не определила ни одной допустимой категории для статьи");
        } else {
          log.info("Нейросеть определила категории: {}", result);
        }

        return result;
      } catch (JsonProcessingException e) {
        log.error("Ошибка при парсинге JSON: {}. Ответ нейросети: {}", e.getMessage(), response);
      }
    } catch (Exception e) {
      log.error("Ошибка при извлечении категорий: {}. Ответ нейросети: {}", e.getMessage(), response);
    }

    return new ArrayList<>();
  }
}
