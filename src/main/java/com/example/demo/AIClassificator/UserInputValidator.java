package com.example.demo.AIClassificator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserInputValidator {
  private static final Dotenv dotenv = Dotenv.load();
  private static final long TIMEOUT = 30000;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UrlValidator urlValidator;
  private final AICache aiCache;

  @Value("${ai.key.validator.value}")
  private String KEY;

  @Value("${ai.endpoint}")
  private String ENDPOINT;

  @Value("${ai.model}")
  private String MODEL;

  public ValidationResult validateCategoryName(String categoryName) {
    try {
      String systemPrompt = "Ты — валидатор категорий новостей. Твоя задача — проверять, является ли категория осмысленной и подходящей для новостей.\n" +
          "Правила:\n" +
          "1. Принимать любые осмысленные темы\n" +
          "2. Отклонять только:\n" +
          "   - бессмысленные наборы символов\n" +
          "   - оскорбительные выражения\n\n" +
          "Ты ДОЛЖЕН вернуть JSON объект в формате:\n" +
          "{\"isValid\": true/false, \"correctedValue\": \"исправленное название\", \"explanation\": \"объяснение\"}\n\n" +
          "Примеры:\n" +
          "{\"isValid\": true, \"correctedValue\": \"Технологии\", \"explanation\": \"Общая категория\"}\n" +
          "{\"isValid\": false, \"correctedValue\": \"\", \"explanation\": \"Бессмысленный набор символов\"}";

      String response = askAI(systemPrompt, categoryName);
      return parseValidationResult(response);
    } catch (Exception e) {
      log.error("Ошибка при валидации названия категории: {}", e.getMessage());
      return new ValidationResult(false, categoryName, "Произошла ошибка при проверке названия категории");
    }
  }

  public ValidationResult validateWebsiteName(String websiteName) {
    try {
      String systemPrompt = "Ты — валидатор названий новостных сайтов. Твоя задача — проверять корректность названия сайта.\n" +
          "Ты ДОЛЖЕН вернуть JSON объект с полями:\n" +
          "- isValid (true/false)\n" +
          "- correctedValue (исправленное название, если нужно исправить)\n" +
          "- explanation (объяснение, почему название валидно или нет)\n\n" +
          "Пример валидного ответа: {\"isValid\": true, \"correctedValue\": \"РБК\", \"explanation\": \"Название корректное\"}";

      String response = askAI(systemPrompt, websiteName);
      return parseValidationResult(response);
    } catch (Exception e) {
      log.error("Ошибка при валидации названия сайта: {}", e.getMessage());
      return new ValidationResult(false, websiteName, "Произошла ошибка при проверке названия сайта");
    }
  }

  public ValidationResult validateWebsiteUrl(String url) {
    try {
      boolean isValid = urlValidator.isValidUrl(url);
      if (isValid) {
        return new ValidationResult(true, url, "URL корректный и доступен");
      } else {
        return new ValidationResult(false, url, "URL недоступен или некорректен");
      }
    } catch (Exception e) {
      log.error("Ошибка при валидации URL сайта: {}", e.getMessage());
      return new ValidationResult(false, url, "Произошла ошибка при проверке URL сайта");
    }
  }

  private String askAI(String systemPrompt, String userPrompt) throws TimeoutException {
    log.info("Отправка запроса к нейросети:\nСистемный промпт: {}\nПользовательский промпт: {}", systemPrompt, userPrompt);
    try {
      String cacheKey = systemPrompt + "|" + userPrompt;
      
      if (aiCache.contains(cacheKey)) {
        log.info("Найдено в кэше, используем сохраненный ответ");
        return aiCache.get(cacheKey);
      }

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
      body.put("max_tokens", 100);

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
      String content = root.path("choices").get(0).path("message").path("content").asText();
      
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

      aiCache.put(cacheKey, content);
          
      return content;
    } catch (Exception e) {
      log.error("Ошибка при обращении к нейросети: {}", e.getMessage());
      throw new RuntimeException("Ошибка при обращении к нейросети", e);
    }
  }

  private ValidationResult parseValidationResult(String response) {
    try {
      int startIdx = response.indexOf("{");
      int endIdx = response.lastIndexOf("}") + 1;
      if (startIdx == -1 || endIdx <= startIdx) {
        log.error("Не найден JSON объект в ответе: {}", response);
        return new ValidationResult(false, "", "Не удалось обработать ответ нейросети");
      }

      String json = response.substring(startIdx, endIdx);
      JsonNode root = objectMapper.readTree(json);

      if (!root.has("isValid") || !root.has("correctedValue") || !root.has("explanation")) {
        log.error("Ответ не содержит всех необходимых полей: {}", json);
        return new ValidationResult(false, "", "Неверный формат ответа нейросети");
      }

      boolean isValid = root.get("isValid").asBoolean();
      String correctedValue = root.get("correctedValue").asText();
      String explanation = root.get("explanation").asText();

      if (correctedValue == null) {
        correctedValue = "";
      }

      return new ValidationResult(isValid, correctedValue, explanation);
    } catch (Exception e) {
      log.error("Ошибка при парсинге ответа нейросети: {}", e.getMessage());
      log.error("Ответ нейросети: {}", response);
      return new ValidationResult(false, "", "Не удалось обработать ответ нейросети");
    }
  }

  public static class ValidationResult {
    private final boolean isValid;
    private final String explanation;
    private final String value;

    public ValidationResult(boolean isValid, String value, String explanation) {
      this.isValid = isValid;
      this.value = value;
      this.explanation = explanation;
    }

    public boolean isValid() {
      return isValid;
    }

    public String getExplanation() {
      return explanation;
    }

    public String getCorrectedValue() {
      return value;
    }
  }
} 
