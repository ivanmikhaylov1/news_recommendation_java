package com.example.demo.service;

import com.example.demo.bot.TelegramBot;
import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.User;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jvnet.hk2.annotations.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final String NOTIFY_TEXT = """
            <b>${title}</b>
            
            ${description}
            
            Дата публикации: <i>${date}</i>
            
            <a href="${url}">Читать подробнее</a>""";

    private final TelegramBot bot;

    private final UserRepository userRepository;
    private final ArticlesRepository articlesRepository;

    @Scheduled(initialDelayString = "${parser.fixedRateMain}", fixedRateString = "${parser.fixedRateMain}")
    private void sendNotification() {
        Long minId = userRepository.getMinLastSubmittedArticleId();
        List<Article> articles = articlesRepository.findByMinId(minId);

        for (Article article : articles) {
            Integer currentHour = LocalTime.now().getHour();

            List<User> users = userRepository.findUsersForArticle(article, currentHour);

            for (User user : users) {
                sendNotification(article, user.);
            }
        }
    }

    private void sendNotification(Article article, Long userId) throws TelegramApiException {
        String message = NOTIFY_TEXT
                .replace("${title}", article.getName())
                .replace("${description}", article.getDescription())
                .replace("${date}", article.getSiteDate())
                .replace("${url}", article.getUrl());

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(userId);
        sendMessage.setText(message);
        sendMessage.setParseMode("HTML");

        bot.execute(sendMessage);
    }
}
