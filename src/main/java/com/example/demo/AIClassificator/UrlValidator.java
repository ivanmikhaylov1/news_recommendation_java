package com.example.demo.AIClassificator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

@Slf4j
@Component
public class UrlValidator {
  private static final Pattern URL_PATTERN = Pattern.compile(
      "^https?://" +
          "([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+" +
          "[a-zA-Z]{2,}" +
          "(/[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=]*)?$"
  );

  public boolean isValidUrl(String url) {
    log.info("Начало валидации URL: {}", url);
    try {
      if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://" + url;
        log.info("Добавлен протокол HTTPS: {}", url);
      }

      if (!URL_PATTERN.matcher(url).matches()) {
        log.error("URL {} не соответствует формату", url);
        return false;
      }

      URL urlObj = new URL(url);
      log.info("URL объект создан: {}", urlObj);

      HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setInstanceFollowRedirects(true);
      String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
      connection.setRequestProperty("User-Agent", userAgent);
      connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
      connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3");
      log.info("Отправка запроса к URL: {} с User-Agent: {}", url, userAgent);

      try {
        int responseCode = connection.getResponseCode();
        log.info("Получен код ответа: {} для URL: {}", responseCode, url);
        boolean isValid = (responseCode >= 200 && responseCode < 400) ||
            responseCode == 403 ||
            responseCode == 405;

        log.info("URL {} валиден: {}", url, isValid);
        return isValid;
      } finally {
        connection.disconnect();
      }
    } catch (UnknownHostException e) {
      log.error("Не удалось найти хост для URL {}: {}", url, e.getMessage());
      return false;
    } catch (SSLHandshakeException e) {
      log.error("Ошибка SSL при проверке URL {}: {}", url, e.getMessage());
      return true;
    } catch (Exception e) {
      log.error("Ошибка при проверке URL {}: {}", url, e.getMessage(), e);
      return false;
    }
  }
}
