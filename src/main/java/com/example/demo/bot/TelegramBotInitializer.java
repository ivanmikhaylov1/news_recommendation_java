package com.example.demo.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
public class TelegramBotInitializer {
  private final TelegramBot bot;

  public TelegramBotInitializer(TelegramBot bot) {
    this.bot = bot;
  }

  @EventListener({ContextRefreshedEvent.class})
  public void init() {
    try {
      TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
      telegramBotsApi.registerBot(bot);
    } catch (TelegramApiException e) {
      log.error("Ошибка при регистрации телеграм бота: {}", e.getMessage());
    }
  }
}
