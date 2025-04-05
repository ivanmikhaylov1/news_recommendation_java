package com.example.demo.bot;

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
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
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
        if (messageText.startsWith(ADD_CATEGORY_COMMAND)) {
          handleAddCategoryCommand(chatId, user, messageText);
          sendWebsitesKeyboard(userId, user);
        } else if (messageText.startsWith(ADD_WEBSITE_COMMAND)) {
          handleAddWebsiteCommand(chatId, user, messageText);
          showMainMenu(chatId);
        } else {
          switch (messageText) {
            case "/start":
              startCommandReceived(chatId, user, update.getMessage().getChat().getFirstName());
              break;
            case "/categories":
              sendCategoriesKeyboard(chatId, user);
              break;
            case "/websites":
              sendWebsitesKeyboard(chatId, user);
              break;
            case "/mycategories":
              showUserCategories(chatId, user);
              break;
            case "/mywebsites":
              showUserWebsites(chatId, user);
              break;
            case "/menu":
              showMainMenu(chatId);
              break;
            case "/help":
              sendHelpMessage(chatId);
              break;
            default:
              sendMessage(chatId, "Отправьте /help для просмотра доступных команд.");
          }
        }
      } catch (Exception e) {
        sendMessage(chatId, "Произошла ошибка при обработке вашего запроса");
      }
    } else if (update.hasCallbackQuery()) {
      // Обработка нажатий на кнопки
      String callbackData = update.getCallbackQuery().getData();
      long chatId = update.getCallbackQuery().getMessage().getChatId();
      Long userId = update.getCallbackQuery().getFrom().getId();

      try {
        User user = userService.getOrCreateUser(userId);

        if (callbackData.startsWith("category_")) {
          // Выбор категории
          Long categoryId = Long.parseLong(callbackData.substring(9));
          categoriesService.chooseCategory(user, categoryId);
          sendMessage(chatId, "Категория успешно добавлена!");
          showUserCategories(chatId, user); // Показываем обновленный список категорий
        } else if (callbackData.equals("add_custom_category")) {
          // Добавление собственной категории
          sendMessage(chatId, "Чтобы добавить свою категорию, отправьте команду с названием: " + ADD_CATEGORY_COMMAND + " ИМЯ_КАТЕГОРИИ");
        } else if (callbackData.startsWith("remove_category_")) {
          // Удаление категории
          Long categoryId = Long.parseLong(callbackData.substring(16));
          categoriesService.removeCategory(user, categoryId);
          sendMessage(chatId, "Категория успешно удалена!");
          showUserCategories(chatId, user); // Обновляем список категорий
        } else if (callbackData.equals("add_more_categories")) {
          // Показываем клавиатуру с категориями для добавления
          sendCategoriesKeyboard(chatId, user);
        } else if (callbackData.equals("continue_to_websites")) {
          // После выбора категорий переходим к выбору сайтов
          sendWebsitesKeyboard(chatId, user);
        } else if (callbackData.startsWith("website_")) {
          // Выбор сайта
          Long websiteId = Long.parseLong(callbackData.substring(8));
          websitesService.chooseWebsite(user, websiteId);
          sendMessage(chatId, "Сайт успешно добавлен!");
          showUserWebsites(chatId, user); // Показываем обновленный список сайтов
        } else if (callbackData.equals("add_custom_website")) {
          // Добавление собственного сайта
          sendMessage(chatId, "Чтобы добавить свой сайт, отправьте команду с названием и URL: " + ADD_WEBSITE_COMMAND + " НАЗВАНИЕ URL");
        } else if (callbackData.startsWith("remove_website_")) {
          // Удаление сайта
          Long websiteId = Long.parseLong(callbackData.substring(15));
          websitesService.removeWebsite(user, websiteId);
          sendMessage(chatId, "Сайт успешно удален!");
          showUserWebsites(chatId, user); // Обновляем список сайтов
        } else if (callbackData.equals("add_more_websites")) {
          // Показываем клавиатуру с сайтами для добавления
          sendWebsitesKeyboard(chatId, user);
        } else if (callbackData.equals("setup_complete")) {
          // Завершение настройки
          sendMessage(chatId, "Настройка завершена! Теперь вы будете получать новости по выбранным категориям и с выбранных сайтов.");
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
          // Установка времени начала уведомлений
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
              sendMessage(chatId, "Произошла ошибка: недопустимое значение часа. Пожалуйста, выберите время от 0 до 23.");
              showTimeSelectionMenu(chatId, user, true);
              return;
            }

            // Сохраняем временно выбранное время начала в сессионном объекте пользователя
            NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user);
            if (scheduleDTO != null) {
              // Обновляем только расписание, сохраняя конечное время
              notificationScheduleService.updateNotificationTime(user, hour, scheduleDTO.getEndHour());
              sendMessage(chatId, "Время начала получения уведомлений установлено на " + hour + ":00");

              // Сразу переходим к выбору времени окончания
              showTimeSelectionMenu(chatId, user, false);
            } else {
              sendMessage(chatId, "Ошибка при обновлении настроек");
            }
          } catch (Exception e) {
            sendMessage(chatId, "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте снова.");
            showTimeSelectionMenu(chatId, user, true);
          }
        } else if (callbackData.startsWith("set_end_hour_")) {
          // Установка времени окончания уведомлений
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
              sendMessage(chatId, "Произошла ошибка: недопустимое значение часа. Пожалуйста, выберите время от 0 до 23.");
              showTimeSelectionMenu(chatId, user, false);
              return;
            }

            NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user);
            if (scheduleDTO != null) {
              // Проверяем, что время окончания не раньше времени начала
              int startHour = scheduleDTO.getStartHour();
              if (hour <= startHour) {
                sendMessage(chatId, "Время окончания должно быть позже времени начала. Пожалуйста, выберите время позже " + startHour + ":00");
                showTimeSelectionMenu(chatId, user, false);
              } else {
                // Обновляем расписание уведомлений
                notificationScheduleService.updateNotificationTime(user, startHour, hour);

                sendMessage(chatId, "Время окончания получения уведомлений установлено на " + hour + ":00");
                sendMessage(chatId, "Установлен период уведомлений: с " + startHour + ":00 до " + hour + ":00");
                showSettingsMenu(chatId, user); // Возвращаемся в настройки
              }
            } else {
              sendMessage(chatId, "Ошибка при обновлении настроек");
            }
          } catch (Exception e) {
            sendMessage(chatId, "Произошла ошибка при обработке запроса. Пожалуйста, попробуйте снова.");
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
          userService.deleteUser(userId);
          sendMessage(chatId, "Ваш аккаунт был успешно удален. Чтобы начать заново, отправьте /start.");
        } else if (callbackData.equals("toggle_notifications")) {
          // Включение/отключение уведомлений
          NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user);
          if (scheduleDTO != null) {
            boolean newState = !scheduleDTO.getIsActive();
            notificationScheduleService.toggleScheduleActive(user, newState);
            String statusMessage = newState ?
                "Уведомления включены. Вы будете получать новости в установленное время." :
                "Уведомления отключены. Вы не будете получать автоматические новости.";
            sendMessage(chatId, statusMessage);
            showSettingsMenu(chatId, user);
          } else {
            sendMessage(chatId, "Ошибка при обновлении настроек");
          }
        } else {
          sendMessage(chatId, "Попробуйте использовать меню команд");
        }
      } catch (Exception e) {
        sendMessage(chatId, "Произошла ошибка при обработке вашего запроса");
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
        ADD_CATEGORY_COMMAND + " ИМЯ - добавить собственную категорию\n" +
        ADD_WEBSITE_COMMAND + " НАЗВАНИЕ URL - добавить собственный источник\n" +
        "/help - показать это сообщение\n";

    sendMessage(chatId, helpText);
  }

  private void showUserCategories(long chatId, User user) {
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    if (userCategories.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных категорий. Используйте /categories чтобы выбрать категории.");
      return;
    }

    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Ваши категории:");

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Добавляем кнопки для каждой категории пользователя с возможностью удаления
    for (CategoryDTO category : userCategories) {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();

      InlineKeyboardButton categoryNameButton = new InlineKeyboardButton();
      categoryNameButton.setText(category.getName());
      categoryNameButton.setCallbackData("info_category_" + category.getId());
      rowInline.add(categoryNameButton);

      // Кнопка для удаления категории
      InlineKeyboardButton deleteButton = new InlineKeyboardButton();
      deleteButton.setText("❌");
      deleteButton.setCallbackData("remove_category_" + category.getId());
      rowInline.add(deleteButton);

      rowsInline.add(rowInline);
    }

    // Добавляем кнопку для добавления категорий
    List<InlineKeyboardButton> addCategoriesRow = new ArrayList<>();
    InlineKeyboardButton addCategoriesButton = new InlineKeyboardButton();
    addCategoriesButton.setText("Добавить еще категории");
    addCategoriesButton.setCallbackData("add_more_categories");
    addCategoriesRow.add(addCategoriesButton);
    rowsInline.add(addCategoriesRow);

    // Добавляем кнопку для перехода к выбору сайтов
    List<InlineKeyboardButton> continueToWebsitesRow = new ArrayList<>();
    InlineKeyboardButton continueToWebsitesButton = new InlineKeyboardButton();
    continueToWebsitesButton.setText("Перейти к выбору сайтов");
    continueToWebsitesButton.setCallbackData("continue_to_websites");
    continueToWebsitesRow.add(continueToWebsitesButton);
    rowsInline.add(continueToWebsitesRow);

    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке списка категорий пользователя: {}", e.getMessage());
    }
  }

  private void showUserWebsites(long chatId, User user) {
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

    if (userWebsites.isEmpty()) {
      sendMessage(chatId, "У вас пока нет выбранных сайтов. Используйте /websites чтобы выбрать источники новостей.");
      return;
    }

    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Ваши источники новостей:");

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Добавляем кнопки для каждого сайта пользователя с возможностью удаления
    for (WebsiteDTO website : userWebsites) {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton websiteNameButton = new InlineKeyboardButton();
      websiteNameButton.setText(website.getName());
      websiteNameButton.setCallbackData("info_website_" + website.getId());
      rowInline.add(websiteNameButton);
      InlineKeyboardButton deleteButton = new InlineKeyboardButton();
      deleteButton.setText("❌");
      deleteButton.setCallbackData("remove_website_" + website.getId());
      rowInline.add(deleteButton);

      rowsInline.add(rowInline);
    }

    // Добавляем кнопку для добавления сайтов
    List<InlineKeyboardButton> addWebsitesRow = new ArrayList<>();
    InlineKeyboardButton addWebsitesButton = new InlineKeyboardButton();
    addWebsitesButton.setText("Добавить еще сайты");
    addWebsitesButton.setCallbackData("add_more_websites");
    addWebsitesRow.add(addWebsitesButton);
    rowsInline.add(addWebsitesRow);

    // Добавляем кнопку для завершения настройки
    List<InlineKeyboardButton> setupCompleteRow = new ArrayList<>();
    InlineKeyboardButton setupCompleteButton = new InlineKeyboardButton();
    setupCompleteButton.setText("Завершить настройку");
    setupCompleteButton.setCallbackData("setup_complete");
    setupCompleteRow.add(setupCompleteButton);
    rowsInline.add(setupCompleteRow);

    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке списка сайтов пользователя: {}", e.getMessage());
    }
  }

  private void handleAddCategoryCommand(long chatId, User user, String messageText) {
    // Извлекаем название категории из сообщения
    if (messageText.length() <= ADD_CATEGORY_COMMAND.length() + 1) {
      sendMessage(chatId, "Пожалуйста, укажите название категории: " + ADD_CATEGORY_COMMAND + " ИМЯ_КАТЕГОРИИ");
      return;
    }

    String categoryName = messageText.substring(ADD_CATEGORY_COMMAND.length() + 1).trim();
    if (categoryName.isEmpty()) {
      sendMessage(chatId, "Название категории не может быть пустым!");
      return;
    }

    try {
      Category newCategory = new Category();
      newCategory.setName(categoryName);
      CategoryDTO createdCategory = categoriesService.createCategory(user, newCategory);

      // Автоматически добавляем созданную категорию пользователю
      categoriesService.chooseCategory(user, createdCategory.getId());

      sendMessage(chatId, "Категория '" + categoryName + "' успешно создана и добавлена в ваш список!");
    } catch (Exception e) {
      sendMessage(chatId, "Не удалось создать категорию. Возможно, такая категория уже существует.");
    }
  }

  private void handleAddWebsiteCommand(long chatId, User user, String messageText) {
    // Извлекаем название и URL сайта из сообщения
    if (messageText.length() <= ADD_WEBSITE_COMMAND.length() + 1) {
      sendMessage(chatId, "Пожалуйста, укажите название и URL сайта: " + ADD_WEBSITE_COMMAND + " НАЗВАНИЕ URL");
      return;
    }

    String[] parts = messageText.substring(ADD_WEBSITE_COMMAND.length() + 1).trim().split("\\s+", 2);

    if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      sendMessage(chatId, "Пожалуйста, укажите и название, и URL сайта: " + ADD_WEBSITE_COMMAND + " НАЗВАНИЕ URL");
      return;
    }

    String websiteName = parts[0].trim();
    String websiteUrl = parts[1].trim();

    try {
      Website newWebsite = new Website();
      newWebsite.setName(websiteName);
      newWebsite.setUrl(websiteUrl);
      WebsiteDTO createdWebsite = websitesService.createWebsite(user, newWebsite);

      // Автоматически добавляем созданный сайт пользователю
      websitesService.chooseWebsite(user, createdWebsite.getId());

      sendMessage(chatId, "Сайт '" + websiteName + "' успешно создан и добавлен в ваш список!");
    } catch (Exception e) {
      sendMessage(chatId, "Не удалось создать сайт. Возможно, такой сайт уже существует.");
    }
  }

  private void startCommandReceived(long chatId, User user, String name) {
    String answer = "Привет, " + name + "! Добро пожаловать в бот для рекомендации новостей.";
    sendMessage(chatId, answer);

    // Проверяем, есть ли у пользователя уже выбранные категории
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);

    if (userCategories.isEmpty()) {
      // Если категорий нет, предлагаем выбрать
      sendMessage(chatId, "Для начала выберите интересующие вас категории новостей.");
      sendCategoriesKeyboard(chatId, user);
    } else {
      // Если категории уже есть, показываем краткую справку
      String categoriesInfo = "У вас уже выбраны категории: " +
          userCategories.stream()
              .map(CategoryDTO::getName)
              .collect(Collectors.joining(", "));

      List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);

      if (userWebsites.isEmpty()) {
        sendMessage(chatId, categoriesInfo + "\n\nТеперь выберите интересующие вас сайты.");
        sendWebsitesKeyboard(chatId, user);
      } else {
        String websitesInfo = "Выбранные источники новостей: " +
            userWebsites.stream()
                .map(WebsiteDTO::getName)
                .collect(Collectors.joining(", "));

        sendMessage(chatId, categoriesInfo + "\n\n" + websitesInfo);

        // Показываем главное меню
        showMainMenu(chatId);
      }
    }
  }

  private void sendCategoriesKeyboard(long chatId, User user) {
    List<CategoryDTO> defaultCategories = categoriesService.getDefaultCategories();

    if (defaultCategories.isEmpty()) {
      sendMessage(chatId, "Категории пока недоступны");
      return;
    }

    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Выберите интересующие вас категории новостей:");

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Получаем категории пользователя для исключения уже выбранных
    List<CategoryDTO> userCategories = categoriesService.getUserCategories(user);
    List<Long> userCategoryIds = userCategories.stream()
        .map(CategoryDTO::getId)
        .toList();

    // Добавляем кнопки только для категорий, которые пользователь еще не выбрал
    for (CategoryDTO category : defaultCategories) {
      // Пропускаем уже выбранные категории
      if (userCategoryIds.contains(category.getId())) {
        continue;
      }

      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton categoryButton = new InlineKeyboardButton();
      categoryButton.setText(category.getName());
      categoryButton.setCallbackData("category_" + category.getId());
      rowInline.add(categoryButton);
      rowsInline.add(rowInline);
    }

    // Если нет новых категорий для добавления
    if (rowsInline.isEmpty()) {
      if (userCategories.isEmpty()) {
        sendMessage(chatId, "Категории пока недоступны. Чтобы добавить свою категорию, используйте команду " +
            ADD_CATEGORY_COMMAND + " ИМЯ_КАТЕГОРИИ");
      } else {
        sendMessage(chatId, "Вы уже выбрали все доступные категории. Переходим к выбору сайтов.");
        sendWebsitesKeyboard(chatId, user);
      }
      return;
    }

    // Добавляем кнопку для создания своей категории
    List<InlineKeyboardButton> customCategoryRow = new ArrayList<>();
    InlineKeyboardButton customCategoryButton = new InlineKeyboardButton();
    customCategoryButton.setText("Добавить свою категорию");
    customCategoryButton.setCallbackData("add_custom_category");
    customCategoryRow.add(customCategoryButton);
    rowsInline.add(customCategoryRow);

    // Если у пользователя уже есть категории, добавляем кнопку для перехода к выбору сайтов
    if (!userCategories.isEmpty()) {
      List<InlineKeyboardButton> continueToWebsitesRow = new ArrayList<>();
      InlineKeyboardButton continueToWebsitesButton = new InlineKeyboardButton();
      continueToWebsitesButton.setText("Перейти к выбору сайтов");
      continueToWebsitesButton.setCallbackData("continue_to_websites");
      continueToWebsitesRow.add(continueToWebsitesButton);
      rowsInline.add(continueToWebsitesRow);
    }

    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке клавиатуры категорий: {}", e.getMessage());
    }
  }

  private void sendWebsitesKeyboard(long chatId, User user) {
    List<WebsiteDTO> defaultWebsites = websitesService.getDefaultWebsites();

    if (defaultWebsites.isEmpty()) {
      sendMessage(chatId, "Источники новостей пока недоступны");
      return;
    }

    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Выберите интересующие вас источники новостей:");

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Получаем сайты пользователя для исключения уже выбранных
    List<WebsiteDTO> userWebsites = websitesService.getUserWebsites(user);
    List<Long> userWebsiteIds = userWebsites.stream()
        .map(WebsiteDTO::getId)
        .toList();

    // Добавляем кнопки только для сайтов, которые пользователь еще не выбрал
    for (WebsiteDTO website : defaultWebsites) {
      // Пропускаем уже выбранные сайты
      if (userWebsiteIds.contains(website.getId())) {
        continue;
      }

      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton websiteButton = new InlineKeyboardButton();
      websiteButton.setText(website.getName());
      websiteButton.setCallbackData("website_" + website.getId());
      rowInline.add(websiteButton);
      rowsInline.add(rowInline);
    }

    // Если нет новых сайтов для добавления
    if (rowsInline.isEmpty()) {
      if (userWebsites.isEmpty()) {
        sendMessage(chatId, "Источники новостей пока недоступны. Чтобы добавить свой источник, используйте команду " +
            ADD_WEBSITE_COMMAND + " НАЗВАНИЕ URL");
      } else {
        sendMessage(chatId, "Вы уже выбрали все доступные источники. Настройка завершена!");
      }
      return;
    }

    // Добавляем кнопку для создания своего сайта
    List<InlineKeyboardButton> customWebsiteRow = new ArrayList<>();
    InlineKeyboardButton customWebsiteButton = new InlineKeyboardButton();
    customWebsiteButton.setText("Добавить свой источник");
    customWebsiteButton.setCallbackData("add_custom_website");
    customWebsiteRow.add(customWebsiteButton);
    rowsInline.add(customWebsiteRow);

    // Если у пользователя уже есть сайты, добавляем кнопку для завершения настройки
    if (!userWebsites.isEmpty()) {
      List<InlineKeyboardButton> setupCompleteRow = new ArrayList<>();
      InlineKeyboardButton setupCompleteButton = new InlineKeyboardButton();
      setupCompleteButton.setText("Завершить настройку");
      setupCompleteButton.setCallbackData("setup_complete");
      setupCompleteRow.add(setupCompleteButton);
      rowsInline.add(setupCompleteRow);
    }

    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке клавиатуры сайтов: {}", e.getMessage());
    }
  }

  public void sendMessage(long chatId, String textToSend) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(textToSend);
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Error occurred: {}", e.getMessage());
    }
  }

  /**
   * Показывает главное меню с кнопками выбора категорий, сайтов и настроек
   */
  private void showMainMenu(long chatId) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("Главное меню:");

    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    // Кнопка выбора категорий
    List<InlineKeyboardButton> categoriesRow = new ArrayList<>();
    InlineKeyboardButton categoriesButton = new InlineKeyboardButton();
    categoriesButton.setText("Выбор категорий");
    categoriesButton.setCallbackData("open_categories");
    categoriesRow.add(categoriesButton);
    rowsInline.add(categoriesRow);

    // Кнопка выбора сайтов
    List<InlineKeyboardButton> websitesRow = new ArrayList<>();
    InlineKeyboardButton websitesButton = new InlineKeyboardButton();
    websitesButton.setText("Выбор источников новостей");
    websitesButton.setCallbackData("open_websites");
    websitesRow.add(websitesButton);
    rowsInline.add(websitesRow);

    // Кнопка настроек
    List<InlineKeyboardButton> settingsRow = new ArrayList<>();
    InlineKeyboardButton settingsButton = new InlineKeyboardButton();
    settingsButton.setText("Настройки");
    settingsButton.setCallbackData("open_settings");
    settingsRow.add(settingsButton);
    rowsInline.add(settingsRow);

    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке главного меню: {}", e.getMessage());
    }
  }

  /**
   * Показывает меню настроек пользователя
   */
  private void showSettingsMenu(long chatId, User user) {
    try {
      // Получаем активное расписание пользователя через сервис
      NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user);
      if (scheduleDTO == null) {
        sendMessage(chatId, "Ошибка при загрузке настроек");
        return;
      }

      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      String settings = "Настройки:\n\n" + String.format("Период получения уведомлений: с %d:00 до %d:00\n",
          scheduleDTO.getStartHour(), scheduleDTO.getEndHour()) +
          String.format("Статус уведомлений: %s\n\n",
              scheduleDTO.getIsActive() ? "Включены" : "Отключены");

      message.setText(settings);
      InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
      List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

      // Кнопка изменения времени получения уведомлений
      List<InlineKeyboardButton> timeSettingsRow = new ArrayList<>();
      InlineKeyboardButton timeSettingsButton = new InlineKeyboardButton();
      timeSettingsButton.setText("Изменить время уведомлений");
      timeSettingsButton.setCallbackData("change_notification_time");
      timeSettingsRow.add(timeSettingsButton);
      rowsInline.add(timeSettingsRow);

      // Кнопка включения/отключения уведомлений
      List<InlineKeyboardButton> toggleRow = new ArrayList<>();
      InlineKeyboardButton toggleButton = new InlineKeyboardButton();
      toggleButton.setText(Boolean.TRUE.equals(scheduleDTO.getIsActive()) ?
          "Отключить уведомления" : "Включить уведомления");
      toggleButton.setCallbackData("toggle_notifications");
      toggleRow.add(toggleButton);
      rowsInline.add(toggleRow);

      // Кнопка удаления аккаунта
      List<InlineKeyboardButton> deleteAccountRow = new ArrayList<>();
      InlineKeyboardButton deleteAccountButton = new InlineKeyboardButton();
      deleteAccountButton.setText("Удалить аккаунт");
      deleteAccountButton.setCallbackData("delete_account_confirm");
      deleteAccountRow.add(deleteAccountButton);
      rowsInline.add(deleteAccountRow);

      // Кнопка возврата в главное меню
      List<InlineKeyboardButton> backRow = new ArrayList<>();
      InlineKeyboardButton backButton = new InlineKeyboardButton();
      backButton.setText("↩️ Назад в главное меню");
      backButton.setCallbackData("back_to_main_menu");
      backRow.add(backButton);
      rowsInline.add(backRow);

      markupInline.setKeyboard(rowsInline);
      message.setReplyMarkup(markupInline);

      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке меню настроек: {}", e.getMessage());
    } catch (Exception e) {
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
      NotificationScheduleDTO scheduleDTO = notificationScheduleService.getActiveScheduleDTO(user);
      if (scheduleDTO == null) {
        sendMessage(chatId, "Ошибка при загрузке настроек");
        return;
      }

      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(title);
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
      message.setReplyMarkup(markupInline);

      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке меню выбора времени: {}", e.getMessage());
    } catch (Exception e) {
      sendMessage(chatId, "Произошла ошибка при загрузке настроек");
    }
  }

  /**
   * Показывает меню подтверждения удаления аккаунта
   */
  private void showDeleteConfirmation(long chatId) {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText("""
        Вы уверены, что хотите удалить свой аккаунт?
        Все ваши настройки, выбранные категории и источники будут удалены безвозвратно.
        """);

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
    message.setReplyMarkup(markupInline);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке подтверждения удаления: {}", e.getMessage());
    }
  }
}
