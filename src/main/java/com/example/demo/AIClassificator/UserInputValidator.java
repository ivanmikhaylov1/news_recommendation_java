package com.example.demo.AIClassificator;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserInputValidator {
  private static final Dotenv dotenv = Dotenv.load();
  private static final String KEY = dotenv.get("KEY");
  private static final String ENDPOINT = dotenv.get("ENDPOINT");
  private static final String MODEL = dotenv.get("MODEL");
  private static final long TIMEOUT = 15000; 
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UrlValidator urlValidator;

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
    options.setMaxTokens(100);
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
