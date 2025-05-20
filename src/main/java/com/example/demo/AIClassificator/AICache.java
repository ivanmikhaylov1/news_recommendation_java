package com.example.demo.AIClassificator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AICache {
  private static final int MAX_CACHE_SIZE = 1000;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public String get(String key) {
    return cache.get(key);
  }

  public void put(String key, String value) {
    if (cache.size() >= MAX_CACHE_SIZE) {
      log.warn("Кэш достиг максимального размера, очищаем старые записи");
      cache.clear();
    }

    cache.put(key, value);
  }

  public boolean contains(String key) {
    return cache.containsKey(key);
  }
}
