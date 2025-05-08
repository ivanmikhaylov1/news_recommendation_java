package com.example.demo.bot;

import com.example.demo.AIClassificator.UserInputValidator;
import com.example.demo.configuration.BotConfig;
import com.example.demo.domain.dto.CategoryDTO;
import com.example.demo.domain.dto.NotificationScheduleDTO;
import com.example.demo.domain.dto.WebsiteDTO;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.service.CategoriesService;
import com.example.demo.service.NotificationScheduleService;
import com.example.demo.service.UserService;
import com.example.demo.service.WebsitesService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
  private static final String ADD_CATEGORY_COMMAND = "/add_category";
  private static final String ADD_WEBSITE_COMMAND = "/add_website";
  private final BotConfig botConfig;
  private final UserService userService;
  private final CategoriesService categoriesService;
  private final WebsitesService websitesService;
  private final NotificationScheduleService notificationScheduleService;
  private final UserInputValidator userInputValidator;

  // Карта для хранения идентификатора последнего отправленного сообщения для каждого чата
  private final Map<Long, Integer> lastMessageIds = new ConcurrentHashMap<>();

  // Добавляем новое поле для хранения состояния диалога пользователей
  private final Map<Long, DialogState> userDialogStates = new ConcurrentHashMap<>();
  // Поле для хранения временных данных диалога
  private final Map<Long, String> tempDialogData = new ConcurrentHashMap<>();

  @Override
  public String getBotUsername() {
    return botConfig.getName();
  }

  @Override
  public String getBotToken() {
    return botConfig.getToken();
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();

      // Автоматическая регистрация пользователя
      try {
        User user = userService.getOrCreateUser(userId);

        // Проверяем, находится ли пользователь в диалоге
        DialogState currentState = userDialogStates.getOrDefault(userId, DialogState.NONE);

        if (currentState != DialogState.NONE) {
          // Обрабатываем сообщение в контексте текущего диалога
          handleDialogMessage(chatId, user, userId, messageText, currentState);
        } else if (messageText.startsWith("/")) {
          // Обработка команд, начинающихся с "/"
          if (messageText.startsWith(ADD_CATEGORY_COMMAND)) {
            handleAddCategoryCommand(chatId, user, messageText);
          } else if (messageText.startsWith(ADD_WEBSITE_COMMAND)) {
            handleAddWebsiteCommand(chatId, user, messageText);
          } else {
            switch (messageText) {
              case "/start":
                startCommandReceived(chatId, user, update.getMessage().getChat().getFirstName());
                break;
              case "/categories":
                sendNewInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
                break;
              case "/websites":
                sendNewInteractiveInterface(chatId, "Выберите интересующие вас источники новостей:", InterfaceType.WEBSITES, user);
                break;
              case "/mycategories":
                showUserCategoriesWithNewMessage(chatId, user);
                break;
              case "/mywebsites":
                showUserWebsitesWithNewMessage(chatId, user);
                break;
              case "/menu":
                showMainMenuWithNewMessage(chatId);
                break;
              case "/help":
                sendHelpMessage(chatId);
                break;
              default:
                sendMessage(chatId, "Отправьте /help для просмотра доступных команд.");
            }
          }
        } else {
          // Обработка обычных текстовых сообщений не в контексте диалога
          sendMessage(chatId, "Используйте команды или кнопки для взаимодействия с ботом. Отправьте /help для просмотра доступных команд.");
        }
      } catch (Exception e) {
        sendMessage(chatId, "Произошла ошибка при обработке вашего запроса");
      }
    } else if (update.hasCallbackQuery()) {
      // Обработка нажатий на кнопки
      String callbackData = update.getCallbackQuery().getData();
      long chatId = update.getCallbackQuery().getMessage().getChatId();
      int messageId = update.getCallbackQuery().getMessage().getMessageId();
      Long userId = update.getCallbackQuery().getFrom().getId();

      try {
        User user = userService.getOrCreateUser(userId);

        // Сохраняем ID сообщения для дальнейшего обновления
        lastMessageIds.put(chatId, messageId);

        // Временное сообщение, показывающее, что команда обрабатывается
        String processingMessage = "⌛ Обработка...";

        if (callbackData.startsWith("category_")) {
          // Обновляем сообщение с информацией о выполнении
          updateMessageWithKeyboard(chatId, messageId, processingMessage, null);

          // Выбор категории
          Long categoryId = Long.parseLong(callbackData.substring(9));
          categoriesService.chooseCategory(user, categoryId);

          // Обновляем с результатом
          updateMessageWithKeyboard(chatId, messageId, "✅ Категория успешно добавлена!", null);

          // Показываем обновленный список категорий
          showUserCategories(chatId, user);

          // Завершаем диалог
          cancelDialog(chatId, userId);
        } else if (callbackData.equals("add_custom_category")) {
          // Запустить диалог для добавления категории
          startAddCategoryDialog(chatId, userId);
        } else if (callbackData.startsWith("remove_category_")) {
          // Обновляем сообщение с информацией о выполнении
          updateMessageWithKeyboard(chatId, messageId, processingMessage, null);

          // Удаление категории
          Long categoryId = Long.parseLong(callbackData.substring(16));
          categoriesService.removeCategory(user, categoryId);

          // Обновляем с результатом
          updateMessageWithKeyboard(chatId, messageId, "✅ Категория успешно удалена!", null);

          // Обновляем список категорий
          showUserCategories(chatId, user);
        } else if (callbackData.equals("add_more_categories")) {
          // Показываем клавиатуру с категориями для добавления
          sendInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
        } else if (callbackData.equals("continue_to_websites")) {
          // После выбора категорий переходим к выбору сайтов
          sendInteractiveInterface(chatId, "Выберите интересующие вас источники новостей:", InterfaceType.WEBSITES, user);
        } else if (callbackData.startsWith("website_")) {
          // Обновляем сообщение с информацией о выполнении
          updateMessageWithKeyboard(chatId, messageId, processingMessage, null);

          // Выбор сайта
          Long websiteId = Long.parseLong(callbackData.substring(8));
          websitesService.chooseWebsite(user, websiteId);

          // Обновляем с результатом
          updateMessageWithKeyboard(chatId, messageId, "✅ Сайт успешно добавлен!", null);

          // Показываем обновленный список сайтов
          showUserWebsites(chatId, user);
        } else if (callbackData.equals("add_custom_website")) {
          // Запустить диалог для добавления сайта
          startAddWebsiteDialog(chatId, userId);
        } else if (callbackData.startsWith("remove_website_")) {
          // Обновляем сообщение с информацией о выполнении
          updateMessageWithKeyboard(chatId, messageId, processingMessage, null);

          // Удаление сайта
          Long websiteId = Long.parseLong(callbackData.substring(15));
          websitesService.removeWebsite(user, websiteId);

          // Обновляем с результатом
          updateMessageWithKeyboard(chatId, messageId, "✅ Сайт успешно удален!", null);

          // Обновляем список сайтов
          showUserWebsites(chatId, user);
        } else if (callbackData.equals("add_more_websites")) {
          // Показываем клавиатуру с сайтами для добавления
          sendInteractiveInterface(chatId, "Выберите интересующие вас источники новостей:", InterfaceType.WEBSITES, user);
        } else if (callbackData.equals("setup_complete")) {
          // Завершение настройки
          updateMessageWithKeyboard(chatId, messageId,
              "✅ Настройка завершена! Теперь вы будете получать новости по выбранным категориям и с выбранных сайтов.", null);
          showMainMenu(chatId);
        } else if (callbackData.equals("back_to_main_menu")) {
          // Возврат в главное меню
          showMainMenu(chatId);
        } else if (callbackData.equals("open_categories")) {
          // Открытие меню категорий
          showUserCategories(chatId, user);
        } else if (callbackData.equals("open_websites")) {
          // Открытие меню сайтов
          showUserWebsites(chatId, user);
        } else if (callbackData.equals("open_settings")) {
          // Открытие меню настроек
          showSettingsMenu(chatId, user);
        } else if (callbackData.equals("change_notification_time")) {
          // Выбор времени начала уведомлений
          showTimeSelectionMenu(chatId, user, true);
        } else if (callbackData.startsWith("set_start_hour_")) {
          try {
            // Разделяем строку по последнему символу '_' чтобы корректно извлечь число
            String[] parts = callbackData.split("_");
            String hourString = parts[parts.length - 1];

            // Проверка, что строка содержит только цифры
            if (!hourString.matches("\\d+")) {
              throw new NumberFormatException("Строка '" + hourString + "' содержит не только цифры");
            }

            int hour = Integer.parseInt(hourString);

            // Проверка диапазона часов
            if (hour < 0 || hour > 23) {
              updateMessageWithKeyboard(chatId, messageId,
                  "Произошла ошибка: недопустимое значение часа. Пожалуйста, выберите время от 0 до 23.", null);
              showTimeSelectionMenu(chatId, user, true);
              return;
            }

            // Сохраняем временно выбранное время начала в сессионном объекте пользователя
            NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
            if (scheduleDTO != null) {
              // Обновляем только расписание, сохраняя конечное время
              notificationScheduleService.updateNotificationTime(user.getId(), hour, scheduleDTO.getEndHour());
              updateMessageWithKeyboard(chatId, messageId,
                  "Время начала получения уведомлений установлено на " + hour + ":00", null);

              // Сразу переходим к выбору времени окончания
              showTimeSelectionMenu(chatId, user, false);
            } else {
              updateMessageWithKeyboard(chatId, messageId, "Ошибка при обновлении настроек", null);
            }
          } catch (Exception e) {
            updateMessageWithKeyboard(chatId, messageId,
                "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте снова.", null);
            showTimeSelectionMenu(chatId, user, true);
          }
        } else if (callbackData.startsWith("set_end_hour_")) {
          try {
            // Разделяем строку по последнему символу '_' чтобы корректно извлечь число
            String[] parts = callbackData.split("_");
            String hourString = parts[parts.length - 1];

            // Проверка, что строка содержит только цифры
            if (!hourString.matches("\\d+")) {
              throw new NumberFormatException("Строка '" + hourString + "' содержит не только цифры");
            }

            int hour = Integer.parseInt(hourString);

            // Проверка диапазона часов
            if (hour < 0 || hour > 23) {
              updateMessageWithKeyboard(chatId, messageId,
                  "Произошла ошибка: недопустимое значение часа. Пожалуйста, выберите время от 0 до 23.", null);
              showTimeSelectionMenu(chatId, user, false);
              return;
            }

            NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
            if (scheduleDTO != null) {
              // Проверяем, что время окончания не раньше времени начала
              int startHour = scheduleDTO.getStartHour();
              if (hour <= startHour) {
                updateMessageWithKeyboard(chatId, messageId,
                    "Время окончания должно быть позже времени начала. Пожалуйста, выберите время позже " + startHour + ":00", null);
                showTimeSelectionMenu(chatId, user, false);
              } else {
                // Обновляем расписание уведомлений
                notificationScheduleService.updateNotificationTime(user.getId(), startHour, hour);

                // Отправляем обновление вместо нового сообщения
                StringBuilder resultMessage = new StringBuilder();
                resultMessage.append("Время окончания получения уведомлений установлено на ").append(hour).append(":00\n");
                resultMessage.append("Установлен период уведомлений: с ").append(startHour).append(":00 до ").append(hour).append(":00");

                updateMessageWithKeyboard(chatId, messageId, resultMessage.toString(), null);
                showSettingsMenu(chatId, user); // Возвращаемся в настройки
              }
            } else {
              updateMessageWithKeyboard(chatId, messageId, "Ошибка при обновлении настроек", null);
            }
          } catch (Exception e) {
            updateMessageWithKeyboard(chatId, messageId,
                "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте снова.", null);
            showTimeSelectionMenu(chatId, user, false);
          }
        } else if (callbackData.equals("back_to_settings")) {
          // Возврат в меню настроек
          showSettingsMenu(chatId, user);
        } else if (callbackData.equals("delete_account_confirm")) {
          // Подтверждение удаления аккаунта
          showDeleteConfirmation(chatId);
        } else if (callbackData.equals("delete_account_confirmed")) {
          // Удаление аккаунта пользователя
          deleteAccount(chatId, userId);
        } else if (callbackData.equals("toggle_notifications")) {
          // Включение/отключение уведомлений
          NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
          if (scheduleDTO != null) {
            boolean newState = !scheduleDTO.getIsActive();
            notificationScheduleService.toggleScheduleActive(user.getId(), newState);
            // Обновляем сообщение с настройками
            showSettingsMenu(chatId, user);
          } else {
            updateMessageWithKeyboard(chatId, messageId, "Ошибка при обновлении настроек", null);
          }
        } else if (callbackData.equals("cancel_dialog")) {
          // Отмена текущего диалога
          cancelDialog(chatId, userId);
        } else {
          updateMessageWithKeyboard(chatId, messageId, "Попробуйте использовать меню команд", null);
        }
      } catch (Exception e) {
        try {
          // В случае ошибки пытаемся обновить текущее сообщение с информацией об ошибке
          updateMessageWithKeyboard(chatId, messageId, "Произошла ошибка при обработке вашего запроса", null);
        } catch (Exception ex) {
          // Если не удается обновить сообщение, отправляем новое
          sendMessage(chatId, "Произошла ошибка при обработке вашего запроса");
        }
      }
    }
  }

  private void sendHelpMessage(long chatId) {
    String helpText = "Доступные команды:\n\n" + "/start - начать работу с ботом\n" +
        "/menu - открыть главное меню\n" +
        "/categories - выбрать категории новостей\n" +
        "/websites - выбрать источники новостей\n" +
        "/mycategories - просмотреть мои категории\n" +
        "/mywebsites - просмотреть мои источники\n" +
        "/help - показать это сообщение\n";

    sendMessage(chatId, helpText);
  }

  private void showUserCategories(long chatId, User user) {
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    if (userCategories.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных категорий. Используйте /categories чтобы выбрать категории.");
      return;
    }

    sendInteractiveInterface(chatId, "Ваши категории:", InterfaceType.USER_CATEGORIES, user);
  }

  private void showUserWebsites(long chatId, User user) {
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

    if (userWebsites.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных сайтов. Используйте /websites чтобы выбрать источники новостей.");
      return;
    }

    sendInteractiveInterface(chatId, "Ваши источники новостей:", InterfaceType.USER_WEBSITES, user);
  }

  /**
   * Отправляет сообщение и возвращает его ID для дальнейшего обновления
   */
  private Integer sendMessageAndGetId(long chatId, String text) {
    try {
      Message sentMessage = execute(SendMessage.builder()
          .chatId(chatId)
          .text(text)
          .build());
      return sentMessage.getMessageId();
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке сообщения: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Обновляет текст сообщения без клавиатуры
   */
  private void updateMessage(long chatId, Integer messageId, String text) {
    if (messageId == null) {
      sendMessage(chatId, text);
      return;
    }

    try {
      execute(EditMessageText.builder()
          .chatId(chatId)
          .messageId(messageId)
          .text(text)
          .build());
    } catch (TelegramApiException e) {
      log.error("Ошибка при обновлении сообщения: {}", e.getMessage());
      // В случае ошибки пытаемся отправить новое сообщение
      sendMessage(chatId, text);
    }
  }

  /**
   * Обработка команды добавления своей категории
   */
  private void handleAddCategoryCommand(long chatId, User user, String messageText) {
    try {
      // Проверяем, что есть что добавлять
      String categoryName = messageText.substring(ADD_CATEGORY_COMMAND.length()).trim();
      if (categoryName.isEmpty()) {
        // Отправляем сообщение с правильным форматом команды
        sendMessage(chatId, "Укажите название категории. Пример: " + ADD_CATEGORY_COMMAND + " Технологии");
        return;
      }

      // Проверяем, не является ли введенный текст командой бота
      if (categoryName.startsWith("/")) {
        sendMessage(chatId, "Название категории не может начинаться с символа '/'. Пожалуйста, введите другое название.");
        return;
      }

      // Отправляем сообщение о начале обработки
      Integer processingMessageId = sendMessageAndGetId(chatId, "⌛ Проверяем и добавляем категорию...");

      // Извлекаем название категории и проверяем его валидность через нейросеть
      UserInputValidator.ValidationResult validationResult = userInputValidator.validateCategoryName(categoryName);

      if (validationResult.isValid()) {
        try {
          // Если название прошло валидацию, используем исправленное название
          String validCategoryName = validationResult.getCorrectedValue();

          // Отправляем сообщение о создании категории
          Category newCategory = new Category();
          newCategory.setName(validCategoryName);
          CategoryDTO categoryDTO = categoriesService.createCategory(user, newCategory);

          // Автоматически добавляем созданную категорию пользователю
          categoriesService.chooseCategory(user, categoryDTO.getId());

          updateMessage(chatId, processingMessageId, String.format("✅ Категория \"%s\" успешно добавлена!", validCategoryName));

          // Показываем обновленный список выбранных категорий
          sendNewInteractiveInterface(chatId, "Ваши категории:", InterfaceType.USER_CATEGORIES, user);

          // Завершаем диалог
          cancelDialog(chatId, user.getId());
        } catch (ResponseStatusException e) {
          if (e.getStatusCode() == HttpStatus.CONFLICT) {
            updateMessage(chatId, processingMessageId, String.format("❌ %s", e.getReason()));
          } else {
            updateMessage(chatId, processingMessageId, "❌ Произошла ошибка при создании категории. Попробуйте другое название.");
          }
          // Показываем интерфейс для добавления категорий снова
          sendNewInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
        }
      } else {
        // Если название не прошло валидацию, отправляем сообщение с ошибкой
        updateMessage(chatId, processingMessageId, String.format("❌ Ошибка: %s", validationResult.getExplanation()));

        // Показываем интерфейс для добавления категорий снова
        sendNewInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
      }
    } catch (StringIndexOutOfBoundsException e) {
      sendMessage(chatId, "Укажите название категории. Пример: " + ADD_CATEGORY_COMMAND + " Технологии");
    } catch (Exception e) {
      log.error("Ошибка при добавлении категории: {}", e.getMessage());
      sendMessage(chatId, "Произошла ошибка при добавлении категории. Пожалуйста, попробуйте позже.");
    }
  }

  /**
   * Обработка команды добавления своего сайта
   */
  private void handleAddWebsiteCommand(long chatId, User user, String messageText) {
    try {
      // Проверяем, что есть что добавлять
      String websiteInfo = messageText.substring(ADD_WEBSITE_COMMAND.length()).trim();
      if (websiteInfo.isEmpty()) {
        // Отправляем сообщение с правильным форматом команды
        sendMessage(chatId, "Укажите название и URL сайта. Пример: " + ADD_WEBSITE_COMMAND + " РБК https://www.rbc.ru");
        return;
      }

      // Отправляем сообщение о начале обработки
      Integer processingMessageId = sendMessageAndGetId(chatId, "⌛ Проверяем и добавляем сайт...");

      // Парсим строку для получения названия и URL
      String[] parts = websiteInfo.split("\\s+", 2);
      if (parts.length < 2) {
        updateMessage(chatId, processingMessageId, "❌ Ошибка: укажите и название, и URL сайта. Пример: " + ADD_WEBSITE_COMMAND + " РБК https://www.rbc.ru");
        return;
      }

      String websiteName = parts[0].trim();
      String websiteUrl = parts[1].trim();

      // Выполняем валидацию названия и URL через нейронную сеть
      UserInputValidator.ValidationResult nameValidationResult = userInputValidator.validateWebsiteName(websiteName);
      UserInputValidator.ValidationResult urlValidationResult = userInputValidator.validateWebsiteUrl(websiteUrl);

      if (nameValidationResult.isValid() && urlValidationResult.isValid()) {
        // Если оба поля прошли валидацию, используем исправленные значения
        String validWebsiteName = nameValidationResult.getCorrectedValue();
        String validWebsiteUrl = urlValidationResult.getCorrectedValue();

        // Добавляем сайт
        Website newWebsite = new Website();
        newWebsite.setName(validWebsiteName);
        newWebsite.setUrl(validWebsiteUrl);
        WebsiteDTO websiteDTO = websitesService.createWebsite(user, newWebsite);

        // Автоматически добавляем созданный сайт пользователю
        websitesService.chooseWebsite(user, websiteDTO.getId());

        updateMessage(chatId, processingMessageId, String.format("✅ Сайт \"%s\" (%s) успешно добавлен!", validWebsiteName, validWebsiteUrl));

        // Показываем обновленный список выбранных сайтов
        sendNewInteractiveInterface(chatId, "Ваши источники новостей:", InterfaceType.USER_WEBSITES, user);
      } else {
        // Если какое-то из полей не прошло валидацию, отправляем сообщение с ошибкой
        StringBuilder errorMessage = new StringBuilder("❌ Ошибка при добавлении сайта:\n");
        if (!nameValidationResult.isValid()) {
          errorMessage.append("- Название: ").append(nameValidationResult.getExplanation()).append("\n");
        }
        if (!urlValidationResult.isValid()) {
          errorMessage.append("- URL: ").append(urlValidationResult.getExplanation());
        }
        updateMessage(chatId, processingMessageId, errorMessage.toString());

        // Показываем интерфейс для добавления сайтов снова
        sendNewInteractiveInterface(chatId, "Выберите интересующие вас источники новостей:", InterfaceType.WEBSITES, user);
      }
    } catch (StringIndexOutOfBoundsException e) {
      sendMessage(chatId, "Укажите название и URL сайта. Пример: " + ADD_WEBSITE_COMMAND + " РБК https://www.rbc.ru");
    } catch (Exception e) {
      log.error("Ошибка при добавлении сайта: {}", e.getMessage());
      sendMessage(chatId, "Произошла ошибка при добавлении сайта. Пожалуйста, попробуйте позже.");
    }
  }

  private void startCommandReceived(long chatId, User user, String name) {
    // Отправляем только приветственное сообщение отдельно
    StringBuilder welcomeMessage = new StringBuilder();
    welcomeMessage.append("Привет, ").append(name).append("! Добро пожаловать в бот для рекомендации новостей.");
    sendMessage(chatId, welcomeMessage.toString());

    // Проверяем, есть ли у пользователя уже выбранные категории
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    if (userCategories.isEmpty()) {
      // Если категорий нет, предлагаем выбрать через интерактивное сообщение
      sendNewInteractiveInterface(chatId, "Для начала выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
    } else {
      // Если категории уже есть, показываем информацию о них
      StringBuilder statusMessage = new StringBuilder();
      statusMessage.append("У вас уже выбраны категории: ")
          .append(userCategories.stream()
              .map(CategoryDTO::getName)
              .collect(Collectors.joining(", ")));

      List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

      if (userWebsites.isEmpty()) {
        statusMessage.append("\n\nТеперь выберите интересующие вас сайты:");
        // Отправляем интерактивное сообщение с выбором сайтов
        sendNewInteractiveInterface(chatId, statusMessage.toString(), InterfaceType.WEBSITES, user);
      } else {
        statusMessage.append("\n\nВыбранные источники новостей: ")
            .append(userWebsites.stream()
                .map(WebsiteDTO::getName)
                .collect(Collectors.joining(", ")));

        // Отправляем интерактивное сообщение с главным меню
        sendNewInteractiveInterface(chatId, statusMessage.toString(), InterfaceType.MAIN_MENU, user);
      }
    }
  }

  /**
   * Обновляет существующее сообщение или отправляет новое, если обновление невозможно
   */
  public void sendMessage(long chatId, String textToSend) {
    Integer lastMessageId = lastMessageIds.get(chatId);

    if (lastMessageId != null) {
      // Пробуем обновить существующее сообщение
      try {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(lastMessageId);
        editMessage.setText(textToSend);
        execute(editMessage);
        return; // Если обновление успешно, выходим из метода
      } catch (TelegramApiException e) {
        // В случае ошибки обновления (например, сообщение слишком старое или удалено)
        // продолжаем выполнение для отправки нового сообщения
        log.debug("Не удалось обновить сообщение: {}", e.getMessage());
      }
    }

    // Отправляем новое сообщение
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(textToSend);
    try {
      Message sentMessage = execute(message);
      // Сохраняем ID отправленного сообщения
      lastMessageIds.put(chatId, sentMessage.getMessageId());
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке сообщения: {}", e.getMessage());
    }
  }

  /**
   * Отправляет сообщение с клавиатурой
   */
  public Message sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup markup) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(text);
    message.setReplyMarkup(markup);

    try {
      Message sentMessage = execute(message);
      // Сохраняем ID отправленного сообщения
      lastMessageIds.put(chatId, sentMessage.getMessageId());
      return sentMessage;
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке сообщения с клавиатурой: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Обновляет сообщение с клавиатурой
   */
  public void updateMessageWithKeyboard(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
    EditMessageText editMessage = new EditMessageText();
    editMessage.setChatId(String.valueOf(chatId));
    editMessage.setMessageId(messageId);
    editMessage.setText(text);
    editMessage.setReplyMarkup(markup);

    try {
      execute(editMessage);
    } catch (TelegramApiException e) {
      if (e.getMessage().contains("message is not modified")) {
        // Если сообщение не изменилось, просто игнорируем ошибку
        log.debug("Сообщение не требует обновления: {}", e.getMessage());
      } else {
        // Для других ошибок отправляем новое сообщение
        log.error("Ошибка при обновлении сообщения с клавиатурой: {}", e.getMessage());
        sendMessageWithKeyboard(chatId, text, markup);
      }
    }
  }

  /**
   * Показывает главное меню с кнопками выбора категорий, сайтов и настроек
   */
  private void showMainMenu(long chatId) {
    sendInteractiveInterface(chatId, "Главное меню:", InterfaceType.MAIN_MENU, null);
  }

  /**
   * Показывает меню настроек пользователя
   */
  private void showSettingsMenu(long chatId, User user) {
    try {
      // Получаем активное расписание пользователя через сервис
      NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
      if (scheduleDTO == null) {
        sendMessage(chatId, "Ошибка при загрузке настроек");
        return;
      }

      String settings = "Настройки:\n\n" + String.format("Период получения уведомлений: с %d:00 до %d:00\n",
          scheduleDTO.getStartHour(), scheduleDTO.getEndHour()) +
          String.format("Статус уведомлений: %s\n\n",
              scheduleDTO.getIsActive() ? "Включены" : "Отключены");

      // Получаем текущее сообщение
      Integer lastMessageId = lastMessageIds.get(chatId);
      if (lastMessageId != null) {
        // Пробуем обновить существующее сообщение
        updateMessageWithKeyboard(chatId, lastMessageId, settings, createSettingsKeyboard(user));
      } else {
        // Если нет сохраненного ID сообщения, отправляем новое
        sendInteractiveInterface(chatId, settings, InterfaceType.SETTINGS, user);
      }
    } catch (Exception e) {
      log.error("Ошибка при загрузке настроек: {}", e.getMessage());
      sendMessage(chatId, "Произошла ошибка при загрузке настроек");
    }
  }

  /**
   * Показывает меню выбора времени получения уведомлений
   */
  private void showTimeSelectionMenu(long chatId, User user, boolean isStartTime) {
    try {
      String title = isStartTime ?
          "Шаг 1: Выберите час начала получения уведомлений:" :
          "Шаг 2: Выберите час окончания получения уведомлений:";

      // Получаем активное расписание пользователя через сервис
      NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
      if (scheduleDTO == null) {
        sendMessage(chatId, "Ошибка при загрузке настроек");
        return;
      }

      InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
      List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

      // Получаем минимальный час для отображения кнопок
      int minHour = 0;
      if (!isStartTime) {
        // Если выбираем время конца, то минимальный час - это час начала + 1
        minHour = scheduleDTO.getStartHour() + 1;
        if (minHour > 23) {
          sendMessage(chatId, "Ошибка: время начала установлено на 23:00, что не позволяет выбрать время окончания.");
          showSettingsMenu(chatId, user);
          return;
        }
      }

      // Добавляем кнопки с часами в 4 ряда (по 6 часов в ряду)
      for (int rowStart = 0; rowStart < 24; rowStart += 6) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        boolean hasValidHours = false; // Флаг для проверки наличия хотя бы одного допустимого часа в ряду

        for (int hour = rowStart; hour < rowStart + 6 && hour < 24; hour++) {
          // Пропускаем часы раньше минимального для времени окончания
          if (!isStartTime && hour < minHour) {
            continue;
          }

          hasValidHours = true;
          InlineKeyboardButton hourButton = new InlineKeyboardButton();

          // Подсветка текущего выбранного времени
          String hourText = String.format("%02d:00", hour);
          if ((isStartTime && hour == scheduleDTO.getStartHour()) ||
              (!isStartTime && hour == scheduleDTO.getEndHour())) {
            hourText = hourText + "";
          }

          hourButton.setText(hourText);

          // Обеспечиваем корректный формат callback_data без спецсимволов
          String callbackPrefix = isStartTime ? "set_start_hour" : "set_end_hour";
          String callbackData = callbackPrefix + "_" + hour;
          hourButton.setCallbackData(callbackData);

          row.add(hourButton);
        }

        // Добавляем ряд только если в нем есть хотя бы одна кнопка
        if (hasValidHours) {
          rowsInline.add(row);
        }
      }

      // Проверяем, есть ли кнопки для отображения
      if (rowsInline.isEmpty() && !isStartTime) {
        sendMessage(chatId, "Нет доступных часов для выбора времени окончания. Пожалуйста, установите время начала раньше.");
        showTimeSelectionMenu(chatId, user, true);
        return;
      }

      // Кнопка отмены
      List<InlineKeyboardButton> cancelRow = new ArrayList<>();
      InlineKeyboardButton cancelButton = new InlineKeyboardButton();
      cancelButton.setText("Отмена");
      cancelButton.setCallbackData("back_to_settings");
      cancelRow.add(cancelButton);
      rowsInline.add(cancelRow);
      markupInline.setKeyboard(rowsInline);
      sendMessageWithKeyboard(chatId, title, markupInline);
    } catch (Exception e) {
      sendMessage(chatId, "Произошла ошибка при загрузке настроек");
    }
  }

  /**
   * Показывает меню подтверждения удаления аккаунта
   */
  private void showDeleteConfirmation(long chatId) {
    String messageText = """
        Вы уверены, что хотите удалить свой аккаунт?
        Все ваши настройки, выбранные категории и источники будут удалены безвозвратно.
        """;

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Кнопки подтверждения и отмены
    List<InlineKeyboardButton> confirmRow = new ArrayList<>();

    InlineKeyboardButton cancelButton = new InlineKeyboardButton();
    cancelButton.setText("Отмена");
    cancelButton.setCallbackData("back_to_settings");
    confirmRow.add(cancelButton);
    InlineKeyboardButton confirmButton = new InlineKeyboardButton();
    confirmButton.setText("Да, удалить аккаунт");
    confirmButton.setCallbackData("delete_account_confirmed");
    confirmRow.add(confirmButton);
    rowsInline.add(confirmRow);
    markupInline.setKeyboard(rowsInline);
    sendMessageWithKeyboard(chatId, messageText, markupInline);
  }

  /**
   * Отправляет или обновляет интерактивное сообщение с соответствующим интерфейсом
   */
  private void sendInteractiveInterface(long chatId, String text, InterfaceType type, User user) {
    InlineKeyboardMarkup markup = null;
    switch (type) {
      case MAIN_MENU:
        markup = createMainMenuKeyboard();
        break;
      case CATEGORIES:
        markup = createCategoriesKeyboard(user);
        break;
      case WEBSITES:
        markup = createWebsitesKeyboard(user);
        break;
      case USER_CATEGORIES:
        markup = createUserCategoriesKeyboard(user);
        break;
      case USER_WEBSITES:
        markup = createUserWebsitesKeyboard(user);
        break;
      case SETTINGS:
        markup = createSettingsKeyboard(user);
        break;
      case TIME_SELECTION:
        break;
    }

    Integer lastMessageId = lastMessageIds.get(chatId);

    if (lastMessageId != null) {
      // Пробуем обновить существующее сообщение
      try {
        updateMessageWithKeyboard(chatId, lastMessageId, text, markup);
        return;
      } catch (Exception e) {
        log.debug("Не удалось обновить интерактивное сообщение: {}", e.getMessage());
        // Если не удалось обновить, продолжаем и отправляем новое сообщение
      }
    }

    // Отправляем новое сообщение
    sendMessageWithKeyboard(chatId, text, markup);
  }

  /**
   * Создает клавиатуру для главного меню
   */
  private InlineKeyboardMarkup createMainMenuKeyboard() {
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Кнопка выбора категорий
    List<InlineKeyboardButton> categoriesRow = new ArrayList<>();
    InlineKeyboardButton categoriesButton = new InlineKeyboardButton();
    categoriesButton.setText("Выбор категорий");
    categoriesButton.setCallbackData("open_categories");
    categoriesRow.add(categoriesButton);
    rows.add(categoriesRow);

    // Кнопка выбора сайтов
    List<InlineKeyboardButton> websitesRow = new ArrayList<>();
    InlineKeyboardButton websitesButton = new InlineKeyboardButton();
    websitesButton.setText("Выбор источников новостей");
    websitesButton.setCallbackData("open_websites");
    websitesRow.add(websitesButton);
    rows.add(websitesRow);

    // Кнопка настроек
    List<InlineKeyboardButton> settingsRow = new ArrayList<>();
    InlineKeyboardButton settingsButton = new InlineKeyboardButton();
    settingsButton.setText("Настройки");
    settingsButton.setCallbackData("open_settings");
    settingsRow.add(settingsButton);
    rows.add(settingsRow);

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Создает клавиатуру для выбора категорий
   */
  private InlineKeyboardMarkup createCategoriesKeyboard(User user) {
    List<CategoryDTO> defaultCategories = categoriesService.getDefaultCategories();
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);
    List<Long> userCategoryIds = userCategories.stream()
        .map(CategoryDTO::getId)
        .toList();

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Добавляем кнопки только для категорий, которые пользователь еще не выбрал
    for (CategoryDTO category : defaultCategories) {
      // Пропускаем уже выбранные категории
      if (userCategoryIds.contains(category.getId())) {
        continue;
      }

      List<InlineKeyboardButton> row = new ArrayList<>();
      InlineKeyboardButton button = new InlineKeyboardButton();
      button.setText(category.getName());
      button.setCallbackData("category_" + category.getId());
      row.add(button);
      rows.add(row);
    }

    // Добавляем кнопку для создания своей категории
    List<InlineKeyboardButton> customRow = new ArrayList<>();
    InlineKeyboardButton customButton = new InlineKeyboardButton();
    customButton.setText("Добавить свою категорию");
    customButton.setCallbackData("add_custom_category");
    customRow.add(customButton);
    rows.add(customRow);

    // Если у пользователя уже есть категории, добавляем кнопку для перехода к выбору сайтов
    if (!userCategories.isEmpty()) {
      List<InlineKeyboardButton> continueRow = new ArrayList<>();
      InlineKeyboardButton continueButton = new InlineKeyboardButton();
      continueButton.setText("Перейти к выбору сайтов");
      continueButton.setCallbackData("continue_to_websites");
      continueRow.add(continueButton);
      rows.add(continueRow);
    }

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Создает клавиатуру для выбора сайтов
   */
  private InlineKeyboardMarkup createWebsitesKeyboard(User user) {
    List<WebsiteDTO> defaultWebsites = websitesService.getDefaultWebsites();
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);
    List<Long> userWebsiteIds = userWebsites.stream()
        .map(WebsiteDTO::getId)
        .toList();

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Добавляем кнопки только для сайтов, которые пользователь еще не выбрал
    for (WebsiteDTO website : defaultWebsites) {
      // Пропускаем уже выбранные сайты
      if (userWebsiteIds.contains(website.getId())) {
        continue;
      }

      List<InlineKeyboardButton> row = new ArrayList<>();
      InlineKeyboardButton button = new InlineKeyboardButton();
      button.setText(website.getName());
      button.setCallbackData("website_" + website.getId());
      row.add(button);
      rows.add(row);
    }

    // Добавляем кнопку для создания своего сайта
    List<InlineKeyboardButton> customRow = new ArrayList<>();
    InlineKeyboardButton customButton = new InlineKeyboardButton();
    customButton.setText("Добавить свой источник");
    customButton.setCallbackData("add_custom_website");
    customRow.add(customButton);
    rows.add(customRow);

    // Если у пользователя уже есть сайты, добавляем кнопку для завершения настройки
    if (!userWebsites.isEmpty()) {
      List<InlineKeyboardButton> completeRow = new ArrayList<>();
      InlineKeyboardButton completeButton = new InlineKeyboardButton();
      completeButton.setText("Завершить настройку");
      completeButton.setCallbackData("setup_complete");
      completeRow.add(completeButton);
      rows.add(completeRow);
    }

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Создает клавиатуру для просмотра категорий пользователя
   */
  private InlineKeyboardMarkup createUserCategoriesKeyboard(User user) {
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Добавляем кнопки для каждой категории пользователя с возможностью удаления
    for (CategoryDTO category : userCategories) {
      List<InlineKeyboardButton> row = new ArrayList<>();

      InlineKeyboardButton nameButton = new InlineKeyboardButton();
      nameButton.setText(category.getName());
      nameButton.setCallbackData("info_category_" + category.getId());
      row.add(nameButton);

      // Кнопка для удаления категории
      InlineKeyboardButton deleteButton = new InlineKeyboardButton();
      deleteButton.setText("❌");
      deleteButton.setCallbackData("remove_category_" + category.getId());
      row.add(deleteButton);

      rows.add(row);
    }

    // Добавляем кнопку для добавления категорий
    List<InlineKeyboardButton> addRow = new ArrayList<>();
    InlineKeyboardButton addButton = new InlineKeyboardButton();
    addButton.setText("Добавить еще категории");
    addButton.setCallbackData("add_more_categories");
    addRow.add(addButton);
    rows.add(addRow);

    // Добавляем кнопку для перехода к выбору сайтов
    List<InlineKeyboardButton> continueRow = new ArrayList<>();
    InlineKeyboardButton continueButton = new InlineKeyboardButton();
    continueButton.setText("Перейти к выбору сайтов");
    continueButton.setCallbackData("continue_to_websites");
    continueRow.add(continueButton);
    rows.add(continueRow);

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Создает клавиатуру для просмотра сайтов пользователя
   */
  private InlineKeyboardMarkup createUserWebsitesKeyboard(User user) {
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Добавляем кнопки для каждого сайта пользователя с возможностью удаления
    for (WebsiteDTO website : userWebsites) {
      List<InlineKeyboardButton> row = new ArrayList<>();

      InlineKeyboardButton nameButton = new InlineKeyboardButton();
      nameButton.setText(website.getName());
      nameButton.setCallbackData("info_website_" + website.getId());
      row.add(nameButton);

      // Кнопка для удаления сайта
      InlineKeyboardButton deleteButton = new InlineKeyboardButton();
      deleteButton.setText("❌");
      deleteButton.setCallbackData("remove_website_" + website.getId());
      row.add(deleteButton);

      rows.add(row);
    }

    // Добавляем кнопку для добавления сайтов
    List<InlineKeyboardButton> addRow = new ArrayList<>();
    InlineKeyboardButton addButton = new InlineKeyboardButton();
    addButton.setText("Добавить еще сайты");
    addButton.setCallbackData("add_more_websites");
    addRow.add(addButton);
    rows.add(addRow);

    // Добавляем кнопку для завершения настройки
    List<InlineKeyboardButton> completeRow = new ArrayList<>();
    InlineKeyboardButton completeButton = new InlineKeyboardButton();
    completeButton.setText("Завершить настройку");
    completeButton.setCallbackData("setup_complete");
    completeRow.add(completeButton);
    rows.add(completeRow);

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Создает клавиатуру для настроек
   */
  private InlineKeyboardMarkup createSettingsKeyboard(User user) {
    NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user.getId());
    if (scheduleDTO == null) {
      return null;
    }

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

    // Кнопка изменения времени получения уведомлений
    List<InlineKeyboardButton> timeRow = new ArrayList<>();
    InlineKeyboardButton timeButton = new InlineKeyboardButton();
    timeButton.setText("Изменить время уведомлений");
    timeButton.setCallbackData("change_notification_time");
    timeRow.add(timeButton);
    rows.add(timeRow);

    // Кнопка включения/отключения уведомлений
    List<InlineKeyboardButton> toggleRow = new ArrayList<>();
    InlineKeyboardButton toggleButton = new InlineKeyboardButton();
    toggleButton.setText(Boolean.TRUE.equals(scheduleDTO.getIsActive()) ?
        "Отключить уведомления" : "Включить уведомления");
    toggleButton.setCallbackData("toggle_notifications");
    toggleRow.add(toggleButton);
    rows.add(toggleRow);

    // Кнопка удаления аккаунта
    List<InlineKeyboardButton> deleteRow = new ArrayList<>();
    InlineKeyboardButton deleteButton = new InlineKeyboardButton();
    deleteButton.setText("Удалить аккаунт");
    deleteButton.setCallbackData("delete_account_confirm");
    deleteRow.add(deleteButton);
    rows.add(deleteRow);

    // Кнопка возврата в главное меню
    List<InlineKeyboardButton> backRow = new ArrayList<>();
    InlineKeyboardButton backButton = new InlineKeyboardButton();
    backButton.setText("↩️ Назад в главное меню");
    backButton.setCallbackData("back_to_main_menu");
    backRow.add(backButton);
    rows.add(backRow);

    markup.setKeyboard(rows);
    return markup;
  }

  /**
   * Отправляет новое интерактивное сообщение без попытки обновления существующего
   */
  private void sendNewInteractiveInterface(long chatId, String text, InterfaceType type, User user) {
    InlineKeyboardMarkup markup = null;
    switch (type) {
      case MAIN_MENU:
        markup = createMainMenuKeyboard();
        break;
      case CATEGORIES:
        markup = createCategoriesKeyboard(user);
        break;
      case WEBSITES:
        markup = createWebsitesKeyboard(user);
        break;
      case USER_CATEGORIES:
        markup = createUserCategoriesKeyboard(user);
        break;
      case USER_WEBSITES:
        markup = createUserWebsitesKeyboard(user);
        break;
      case SETTINGS:
        markup = createSettingsKeyboard(user);
        break;
      case TIME_SELECTION:
        break;
    }

    sendMessageWithKeyboard(chatId, text, markup);
  }

  /**
   * Показывает главное меню через новое сообщение
   */
  private void showMainMenuWithNewMessage(long chatId) {
    sendNewInteractiveInterface(chatId, "Главное меню:", InterfaceType.MAIN_MENU, null);
  }

  /**
   * Показывает категории пользователя через новое сообщение
   */
  private void showUserCategoriesWithNewMessage(long chatId, User user) {
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    if (userCategories.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных категорий. Используйте /categories чтобы выбрать категории.");
      return;
    }

    sendNewInteractiveInterface(chatId, "Ваши категории:", InterfaceType.USER_CATEGORIES, user);
  }

  /**
   * Показывает сайты пользователя через новое сообщение
   */
  private void showUserWebsitesWithNewMessage(long chatId, User user) {
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

    if (userWebsites.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных сайтов. Используйте /websites чтобы выбрать источники новостей.");
      return;
    }

    sendNewInteractiveInterface(chatId, "Ваши источники новостей:", InterfaceType.USER_WEBSITES, user);
  }

  /**
   * Обрабатывает сообщения пользователя в контексте текущего диалога
   */
  private void handleDialogMessage(long chatId, User user, Long userId, String messageText, DialogState state) {
    switch (state) {
      case WAITING_CATEGORY_NAME:
        // Обрабатываем ввод названия категории
        handleCategoryNameInput(chatId, user, userId, messageText);
        break;
      case WAITING_WEBSITE_NAME:
        // Обрабатываем ввод названия сайта
        handleWebsiteNameInput(chatId, user, userId, messageText);
        break;
      case WAITING_WEBSITE_URL:
        // Обрабатываем ввод URL сайта
        handleWebsiteUrlInput(chatId, user, userId, messageText);
        break;
      default:
        // Неизвестное состояние - отменяем диалог
        cancelDialog(chatId, userId);
        sendMessage(chatId, "Произошла ошибка в диалоге. Пожалуйста, попробуйте снова.");
    }
  }

  /**
   * Запускает диалог для добавления категории
   */
  private void startAddCategoryDialog(long chatId, Long userId) {
    // Отправляем сообщение с запросом названия категории
    sendMessage(chatId, "Пожалуйста, введите название новой категории.");

    // Устанавливаем состояние диалога
    userDialogStates.put(userId, DialogState.WAITING_CATEGORY_NAME);
  }

  /**
   * Обрабатывает ввод названия категории
   */
  private void handleCategoryNameInput(long chatId, User user, Long userId, String categoryName) {
    // Отправляем сообщение о начале обработки
    Integer processingMessageId = sendMessageAndGetId(chatId, "⌛ Проверяем и добавляем категорию...");

    try {
      // Проверяем название через нейросеть
      UserInputValidator.ValidationResult validationResult = userInputValidator.validateCategoryName(categoryName);

      if (validationResult.isValid()) {
        // Если название валидно, добавляем категорию
        String validCategoryName = validationResult.getCorrectedValue();

        Category newCategory = new Category();
        newCategory.setName(validCategoryName);
        CategoryDTO categoryDTO = categoriesService.createCategory(user, newCategory);

        // Автоматически добавляем категорию пользователю
        categoriesService.chooseCategory(user, categoryDTO.getId());

        // Обновляем сообщение о результате
        updateMessage(chatId, processingMessageId, String.format("✅ Категория \"%s\" успешно добавлена!", validCategoryName));

        // Показываем обновленный список категорий
        sendNewInteractiveInterface(chatId, "Ваши категории:", InterfaceType.USER_CATEGORIES, user);

        // Завершаем диалог
        cancelDialog(chatId, userId);
      } else {
        // Если название не валидно, сообщаем об ошибке
        updateMessage(chatId, processingMessageId, String.format("❌ Ошибка: %s\n\nПопробуйте ввести другое название.", validationResult.getExplanation()));

        // Показываем интерфейс для добавления категорий снова
        sendNewInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
      }
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        // Если категория уже существует, показываем список доступных категорий
        List<CategoryDTO> defaultCategories = categoriesService.getDefaultCategories();
        StringBuilder message = new StringBuilder();
        message.append("❌ Категория с таким названием уже существует.\n\n");
        message.append("Доступные категории:\n");
        for (CategoryDTO category : defaultCategories) {
          message.append("- ").append(category.getName()).append("\n");
        }
        message.append("\nВыберите одну из существующих категорий или введите другое название.");

        updateMessage(chatId, processingMessageId, message.toString());
        sendNewInteractiveInterface(chatId, "Выберите интересующие вас категории новостей:", InterfaceType.CATEGORIES, user);
      } else {
        updateMessage(chatId, processingMessageId, "❌ Произошла ошибка при создании категории. Попробуйте позже.");
      }
    } catch (Exception e) {
      log.error("Ошибка при добавлении категории: {}", e.getMessage());
      updateMessage(chatId, processingMessageId, "❌ Произошла ошибка при добавлении категории. Попробуйте позже.");
    }
  }

  /**
   * Запускает диалог для добавления сайта
   */
  private void startAddWebsiteDialog(long chatId, Long userId) {
    // Отправляем сообщение с запросом названия сайта
    sendMessage(chatId, "Пожалуйста, введите название нового источника новостей.");

    // Устанавливаем состояние диалога
    userDialogStates.put(userId, DialogState.WAITING_WEBSITE_NAME);
  }

  /**
   * Обрабатывает ввод названия сайта
   */
  private void handleWebsiteNameInput(long chatId, User user, Long userId, String websiteName) {
    // Проверяем, что название не пустое
    String trimmedName = websiteName.trim();
    if (trimmedName.isEmpty()) {
      sendMessage(chatId, "Название источника не может быть пустым.");
      return;
    }

    try {
      // Проверяем название через нейросеть
      UserInputValidator.ValidationResult validationResult = userInputValidator.validateWebsiteName(trimmedName);

      if (validationResult.isValid()) {
        // Если название валидно, сохраняем его и запрашиваем URL
        String validWebsiteName = validationResult.getCorrectedValue();

        // Сохраняем название для использования при вводе URL
        tempDialogData.put(userId, validWebsiteName);

        // Переходим к следующему шагу диалога
        userDialogStates.put(userId, DialogState.WAITING_WEBSITE_URL);

        // Если название было исправлено нейросетью, сообщаем об этом
        if (!trimmedName.equals(validWebsiteName)) {
          sendMessage(chatId, String.format("Название было исправлено на \"%s\".\n\nТеперь, пожалуйста, введите URL источника (например, https://www.example.com).", validWebsiteName));
        } else {
          sendMessage(chatId, "Теперь, пожалуйста, введите URL источника (например, https://www.example.com).");
        }
      } else {
        // Если название не валидно, сообщаем об ошибке
        sendMessage(chatId, String.format("❌ Ошибка: %s\n\nПопробуйте ввести другое название.", validationResult.getExplanation()));
      }
    } catch (Exception e) {
      log.error("Ошибка при проверке названия сайта: {}", e.getMessage());
      sendMessage(chatId, "Произошла ошибка при проверке названия. Попробуйте еще раз.");
    }
  }

  /**
   * Обрабатывает ввод URL сайта
   */
  private void handleWebsiteUrlInput(long chatId, User user, Long userId, String websiteUrl) {
    // Проверяем, что URL не пустой
    String trimmedUrl = websiteUrl.trim();
    if (trimmedUrl.isEmpty()) {
      sendMessage(chatId, "URL не может быть пустым. Попробуйте еще раз.");
      return;
    }

    // Получаем сохраненное название сайта
    String websiteName = tempDialogData.get(userId);
    if (websiteName == null) {
      // Если название не найдено, начинаем диалог заново
      sendMessage(chatId, "Произошла ошибка: название сайта не найдено. Пожалуйста, начните заново.");
      startAddWebsiteDialog(chatId, userId);
      return;
    }

    // Отправляем сообщение о начале обработки
    Integer processingMessageId = sendMessageAndGetId(chatId, "⌛ Проверяем и добавляем источник новостей...");

    try {
      // Проверяем URL через curl
      UserInputValidator.ValidationResult validationResult = userInputValidator.validateWebsiteUrl(trimmedUrl);

      if (validationResult.isValid()) {
        // Если URL валиден, добавляем сайт
        String validWebsiteUrl = validationResult.getCorrectedValue();

        Website newWebsite = new Website();
        newWebsite.setName(websiteName);
        newWebsite.setUrl(validWebsiteUrl);
        WebsiteDTO websiteDTO = websitesService.createWebsite(user, newWebsite);

        // Автоматически добавляем сайт пользователю
        websitesService.chooseWebsite(user, websiteDTO.getId());

        // Обновляем сообщение о результате
        updateMessage(chatId, processingMessageId, String.format("✅ Источник \"%s\" (%s) успешно добавлен!", websiteName, validWebsiteUrl));

        // Показываем обновленный список сайтов
        sendNewInteractiveInterface(chatId, "Ваши источники новостей:", InterfaceType.USER_WEBSITES, user);

        // Завершаем диалог
        cancelDialog(chatId, userId);
      } else {
        // Если URL не валиден, сообщаем об ошибке
        updateMessage(chatId, processingMessageId, String.format("❌ Ошибка: %s\n\nПопробуйте ввести корректный URL.", validationResult.getExplanation()));
      }
    } catch (Exception e) {
      log.error("Ошибка при добавлении сайта: {}", e.getMessage());
      updateMessage(chatId, processingMessageId, "Произошла ошибка при добавлении источника. Попробуйте еще раз.");
    }
  }

  /**
   * Отменяет текущий диалог пользователя
   */
  private void cancelDialog(long chatId, Long userId) {
    userDialogStates.remove(userId);
    tempDialogData.remove(userId);
  }

  private void deleteAccount(long chatId, long userId) {
    try {
      // Порядок важен для избежания проблем с внешними ключами
      // 1. Получаем пользователя
      User user = userService.getOrCreateUser(userId);

      // 2. Очищаем связи пользователя с сайтами (не удаляя сами сайты)
      List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);
      for (WebsiteDTO website : userWebsites) {
        websitesService.removeWebsite(user, website.getId());
      }

      // 3. Очищаем связи пользователя с категориями (не удаляя сами категории)
      List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);
      for (CategoryDTO category : userCategories) {
        categoriesService.removeCategory(user, category.getId());
      }

      // 4. Удаляем расписания уведомлений пользователя
      notificationScheduleService.deleteAllUserSchedules(user.getId());

      // 5. Теперь удаляем аккаунт пользователя
      userService.deleteUser(userId);

      // Получаем messageId из callbackQuery
      Integer messageId = lastMessageIds.get(chatId);
      if (messageId != null) {
        updateMessageWithKeyboard(chatId, messageId,
            "Ваш аккаунт успешно удален. Все данные и настройки стерты.", null);
      } else {
        sendMessage(chatId, "Ваш аккаунт успешно удален. Все данные и настройки стерты.");
      }
    } catch (Exception e) {
      log.error("Ошибка при удалении аккаунта: {}", e.getMessage(), e);
      Integer messageId = lastMessageIds.get(chatId);
      if (messageId != null) {
        updateMessageWithKeyboard(chatId, messageId,
            "Произошла ошибка при удалении аккаунта. Пожалуйста, попробуйте позже.", null);
      } else {
        sendMessage(chatId, "Произошла ошибка при удалении аккаунта. Пожалуйста, попробуйте позже.");
      }
    }
  }

  // enum для отслеживания состояния диалога пользователя
  private enum DialogState {
    NONE,                   // Нет активного диалога
    WAITING_CATEGORY_NAME,  // Ожидаем ввода названия категории
    WAITING_WEBSITE_NAME,   // Ожидаем ввода названия сайта
    WAITING_WEBSITE_URL     // Ожидаем ввода URL сайта после ввода названия
  }

  /**
   * Типы интерактивных интерфейсов для удобного управления обновлениями
   */
  private enum InterfaceType {
    MAIN_MENU,
    CATEGORIES,
    WEBSITES,
    USER_CATEGORIES,
    USER_WEBSITES,
    SETTINGS,
    TIME_SELECTION
  }
}
