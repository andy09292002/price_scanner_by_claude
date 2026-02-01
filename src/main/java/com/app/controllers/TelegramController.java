package com.app.controllers;

import com.app.models.TelegramSubscription;
import com.app.services.TelegramNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Endpoints for managing Telegram subscriptions")
public class TelegramController {

    private final TelegramNotificationService telegramNotificationService;

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe chat to alerts",
               description = "Subscribes a Telegram chat to receive price drop alerts.")
    public ResponseEntity<TelegramSubscription> subscribe(
            @Parameter(description = "Telegram chat ID")
            @RequestParam Long chatId,
            @Parameter(description = "Username (optional)")
            @RequestParam(required = false) String username) {

        log.info("Subscribing chat {} to alerts", chatId);
        TelegramSubscription subscription = telegramNotificationService.subscribe(chatId, username);
        return ResponseEntity.ok(subscription);
    }

    @PutMapping("/settings/{chatId}")
    @Operation(summary = "Update notification preferences",
               description = "Updates the notification settings for a subscribed chat.")
    public ResponseEntity<TelegramSubscription> updateSettings(
            @Parameter(description = "Telegram chat ID")
            @PathVariable Long chatId,
            @Parameter(description = "Minimum price drop percentage to trigger notification")
            @RequestParam(required = false) Integer minDropPercentage,
            @Parameter(description = "Store codes to filter (comma-separated)")
            @RequestParam(required = false) List<String> storeFilters,
            @Parameter(description = "Category codes to filter (comma-separated)")
            @RequestParam(required = false) List<String> categoryFilters) {

        log.info("Updating settings for chat {}", chatId);
        TelegramSubscription subscription = telegramNotificationService.updateSettings(
                chatId, minDropPercentage, storeFilters, categoryFilters);
        return ResponseEntity.ok(subscription);
    }

    @DeleteMapping("/unsubscribe/{chatId}")
    @Operation(summary = "Unsubscribe from alerts",
               description = "Removes a chat's subscription to price drop alerts.")
    public ResponseEntity<Void> unsubscribe(
            @Parameter(description = "Telegram chat ID")
            @PathVariable Long chatId) {

        log.info("Unsubscribing chat {} from alerts", chatId);
        telegramNotificationService.unsubscribe(chatId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test/{chatId}")
    @Operation(summary = "Send test message",
               description = "Sends a test message to verify the Telegram integration.")
    public ResponseEntity<Void> sendTestMessage(
            @Parameter(description = "Telegram chat ID")
            @PathVariable Long chatId) {

        log.info("Sending test message to chat {}", chatId);
        telegramNotificationService.sendMessage(chatId,
                "🧪 This is a test message from Vancouver Price Tracker. Your notifications are working!");
        return ResponseEntity.ok().build();
    }
}
