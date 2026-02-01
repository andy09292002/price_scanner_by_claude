package com.app.config;

import com.app.services.TelegramNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class TelegramBotConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.notification.enabled:true}")
    private boolean notificationEnabled;

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramNotificationService telegramNotificationService) {
        if (!notificationEnabled) {
            log.info("Telegram notifications are disabled");
            return null;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramNotificationService);
            log.info("Telegram bot registered successfully: {}", botUsername);
            return botsApi;
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
            return null;
        }
    }

    public String getBotToken() {
        return botToken;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }
}
