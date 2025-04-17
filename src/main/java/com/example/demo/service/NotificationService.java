package com.example.demo.service;

import com.example.demo.bot.TelegramBot;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    private static final String NOTIFY_TEXT = """
            <b>${title}</b>
            
            ${description}
            
            Дата публикации: <i>${date}</i>
            
            <a href="${url}">Читать подробнее</a>
            
            ${tags}""";

    private final TelegramBot bot;

    private final UserRepository userRepository;
    private final ArticlesRepository articlesRepository;

    @Scheduled(fixedRateString = "${parser.fixedRateMain}")
    @Transactional
    protected void sendNotification() {
        Long minId = userRepository.getMaxLastSubmittedArticleId();
        List<Article> articles = articlesRepository.findByMinId(minId);

        for (Article article : articles) {
            List<User> users = userRepository.findUsersForArticle(article);

            for (User user : users) {
                user.setLastSubmittedArticleId(article.getId());
                userRepository.save(user);
                sendNotification(article, user.getId());
            }
        }
    }

    private void sendNotification(Article article, Long userId) {
        String message = NOTIFY_TEXT
                .replace("${title}", article.getName())
                .replace("${description}", article.getDescription())
                .replace("${date}", article.getSiteDate())
                .replace("${url}", article.getUrl())
                .replace("${tags}", getArticleTags(article));

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(userId);
        sendMessage.setText(message);
        sendMessage.setParseMode("HTML");

        try {
            bot.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправки статьи: {}", e.getMessage());
        }
    }

    private String getArticleTags(Article article) {
        StringBuilder tags = new StringBuilder();

        for (Category category : article.getCategories()) {
            tags.append("#").append(category.getName().toLowerCase()
                    .replace(" ", ""));
        }
        return tags.toString();
    }
}
