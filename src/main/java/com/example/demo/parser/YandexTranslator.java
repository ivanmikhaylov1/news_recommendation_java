package com.example.demo.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YandexTranslator {

  private static final String URL = "https://translate.api.cloud.yandex.net/translate/v2/translate";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  @Value("${yandex.token}")
  private String token;
  @Value("${yandex.folder-id}")
  private String folderId;

  public CompletableFuture<Optional<String>> translate(String text) {
    return translate(text, "en", "ru");
  }

  public CompletableFuture<Optional<String>> translate(String text, String sourceLang) {
    return translate(text, "en", "ru");
  }

  public CompletableFuture<Optional<String>> translate(String text, String sourceLang,
      String targetLang) {
    String requestBody = String.format("""
        {
          "sourceLanguageCode": "%s",
          "targetLanguageCode": "%s",
          "texts": ["%s"],
          "folderId": "%s"
        }
        """, sourceLang, targetLang, escapeJson(text), folderId);

    return makeRequest(requestBody)
        .thenApply(this::parseTranslatedText);
  }

  private Optional<String> parseTranslatedText(Optional<String> responseOpt) {
    if (responseOpt.isEmpty()) {
      return Optional.empty();
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseOpt.get());
      JsonNode translations = root.get("translations");
      if (translations != null && translations.isArray() && !translations.isEmpty()) {
        return Optional.of(translations.get(0).get("text").asText());
      } else {
        log.warn("Ответ API перевода не содержит ожидаемого поля 'translations': {}",
            responseOpt.get());
        return Optional.empty();
      }
    } catch (Exception e) {
      log.error("Не удалось распарсить JSON-ответ от API перевода: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }

  private String escapeJson(String text) {
    return text.replace("\"", "\\\"");
  }

  private CompletableFuture<Optional<String>> makeRequest(String query) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(URL))
        .header("Content-Type", "application/json")
        .header("Authorization", "Api-Key " + token)
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build();

    return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(response -> {
          int statusCode = response.statusCode();
          if (statusCode != 200) {
            log.error("Ошибка перевода: код ответа {}. Тело: {}", statusCode, response.body());
            return Optional.<String>empty();
          }
          return Optional.of(response.body());
        })
        .exceptionally(e -> {
          log.error("Ошибка при выполнении запроса к API перевода: {}", e.getMessage(), e);
          return Optional.empty();
        });
  }
}