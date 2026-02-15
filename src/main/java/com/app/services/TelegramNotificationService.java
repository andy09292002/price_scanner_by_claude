package com.app.services;

import com.app.models.TelegramSubscription;
import com.app.models.TelegramSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramNotificationService extends TelegramLongPollingBot {

    private final TelegramSubscriptionRepository subscriptionRepository;
    private final String botUsername;
    private final boolean notificationEnabled;
    private final int defaultMinDropPercentage;

    public TelegramNotificationService(
            TelegramSubscriptionRepository subscriptionRepository,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.notification.enabled:true}") boolean notificationEnabled,
            @Value("${telegram.default-min-drop-percentage:15}") int defaultMinDropPercentage) {
        super(botToken);
        this.subscriptionRepository = subscriptionRepository;
        this.botUsername = botUsername;
        this.notificationEnabled = notificationEnabled;
        this.defaultMinDropPercentage = defaultMinDropPercentage;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        String firstName = update.getMessage().getFrom().getFirstName();

        log.info("Received message from {}: {}", chatId, messageText);

        if (messageText.startsWith("/start")) {
            handleStartCommand(chatId, username, firstName);
        } else if (messageText.startsWith("/stop")) {
            handleStopCommand(chatId);
        } else if (messageText.startsWith("/settings")) {
            handleSettingsCommand(chatId);
        } else if (messageText.startsWith("/deals")) {
            handleDealsCommand(chatId);
        } else if (messageText.startsWith("/help")) {
            handleHelpCommand(chatId);
        }
    }

    private void handleStartCommand(Long chatId, String username, String firstName) {
        TelegramSubscription subscription = subscriptionRepository.findByChatId(chatId)
                .orElse(TelegramSubscription.builder()
                        .chatId(chatId)
                        .username(username)
                        .firstName(firstName)
                        .minDropPercentage(defaultMinDropPercentage)
                        .active(true)
                        .build());

        subscription.setActive(true);
        subscriptionRepository.save(subscription);

        String message = String.format(
                "🛒 Welcome to Vancouver Price Tracker!\n\n" +
                "You will now receive notifications when prices drop by %d%% or more.\n\n" +
                "Commands:\n" +
                "/settings - Configure alert preferences\n" +
                "/deals - Get current best deals\n" +
                "/stop - Unsubscribe from alerts\n" +
                "/help - Show help message",
                subscription.getMinDropPercentage()
        );

        sendMessage(chatId, message);
    }

    private void handleStopCommand(Long chatId) {
        subscriptionRepository.findByChatId(chatId).ifPresent(subscription -> {
            subscription.setActive(false);
            subscriptionRepository.save(subscription);
        });

        sendMessage(chatId, "👋 You've been unsubscribed from price alerts. Use /start to subscribe again.");
    }

    private void handleSettingsCommand(Long chatId) {
        TelegramSubscription subscription = subscriptionRepository.findByChatId(chatId)
                .orElse(null);

        if (subscription == null) {
            sendMessage(chatId, "You're not subscribed yet. Use /start to subscribe.");
            return;
        }

        String storeFilters = subscription.getStoreFilters() != null && !subscription.getStoreFilters().isEmpty()
                ? String.join(", ", subscription.getStoreFilters())
                : "All stores";

        String categoryFilters = subscription.getCategoryFilters() != null && !subscription.getCategoryFilters().isEmpty()
                ? String.join(", ", subscription.getCategoryFilters())
                : "All categories";

        String message = String.format(
                "⚙️ Your Settings:\n\n" +
                "Min price drop: %d%%\n" +
                "Stores: %s\n" +
                "Categories: %s\n\n" +
                "To change settings, use the web API:\n" +
                "PUT /api/telegram/settings/%d",
                subscription.getMinDropPercentage(),
                storeFilters,
                categoryFilters,
                chatId
        );

        sendMessage(chatId, message);
    }

    private void handleDealsCommand(Long chatId) {
        sendMessage(chatId, "🔍 Fetching current deals...\n\nVisit /api/reports/price-drops for the latest deals.");
    }

    private void handleHelpCommand(Long chatId) {
        String message = "🛒 Vancouver Price Tracker Help\n\n" +
                "I track prices from:\n" +
                "• Real Canadian Superstore\n" +
                "• Walmart Canada\n" +
                "• PriceSmart Foods\n" +
                "• T&T Supermarket\n\n" +
                "Commands:\n" +
                "/start - Subscribe to alerts\n" +
                "/stop - Unsubscribe\n" +
                "/settings - View your preferences\n" +
                "/deals - Get current deals\n" +
                "/help - Show this message";

        sendMessage(chatId, message);
    }

    public void sendPriceDropNotifications(List<PriceAnalysisService.PriceDrop> priceDrops) {
        if (!notificationEnabled) {
            log.info("Telegram notifications are disabled");
            return;
        }

        List<TelegramSubscription> activeSubscriptions = subscriptionRepository.findByActiveTrue();

        for (TelegramSubscription subscription : activeSubscriptions) {
            List<PriceAnalysisService.PriceDrop> filteredDrops = filterDropsForSubscription(priceDrops, subscription);

            if (!filteredDrops.isEmpty()) {
                String message = formatPriceDropMessage(filteredDrops);
                sendMessage(subscription.getChatId(), message);
            }
        }
    }

    private List<PriceAnalysisService.PriceDrop> filterDropsForSubscription(
            List<PriceAnalysisService.PriceDrop> priceDrops,
            TelegramSubscription subscription) {

        return priceDrops.stream()
                .filter(drop -> drop.dropPercentage() >= subscription.getMinDropPercentage())
                .filter(drop -> {
                    // Filter by store if specified
                    if (subscription.getStoreFilters() != null && !subscription.getStoreFilters().isEmpty()) {
                        return subscription.getStoreFilters().contains(drop.store().getCode());
                    }
                    return true;
                })
                .filter(drop -> {
                    // Filter by category if specified
                    if (subscription.getCategoryFilters() != null && !subscription.getCategoryFilters().isEmpty()) {
                        String categoryId = drop.product().getCategoryId();
                        return categoryId != null &&
                               subscription.getCategoryFilters().stream()
                                       .anyMatch(cat -> categoryId.contains(cat));
                    }
                    return true;
                })
                .limit(10) // Limit to avoid too long messages
                .collect(Collectors.toList());
    }

    private String formatPriceDropMessage(List<PriceAnalysisService.PriceDrop> drops) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏷️ Price Drop Alert!\n\n");

        for (PriceAnalysisService.PriceDrop drop : drops) {
            sb.append(String.format("%s - %s\n", drop.product().getName(), drop.store().getName()));
            sb.append(String.format("Was: $%s → Now: $%s\n",
                    drop.previousPrice().setScale(2, RoundingMode.HALF_UP),
                    drop.currentPrice().setScale(2, RoundingMode.HALF_UP)));
            sb.append(String.format("💰 Save %.0f%% ($%s)\n\n",
                    drop.dropPercentage(),
                    drop.dropAmount().setScale(2, RoundingMode.HALF_UP)));
        }

        return sb.toString();
    }

    public void sendMessage(Long chatId, String text) {
        if (!notificationEnabled) {
            log.debug("Telegram notifications disabled, skipping message to {}", chatId);
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");

        try {
            execute(message);
            log.debug("Sent message to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    public TelegramSubscription subscribe(Long chatId, String username) {
        TelegramSubscription subscription = subscriptionRepository.findByChatId(chatId)
                .orElse(TelegramSubscription.builder()
                        .chatId(chatId)
                        .username(username)
                        .minDropPercentage(defaultMinDropPercentage)
                        .build());

        subscription.setActive(true);
        return subscriptionRepository.save(subscription);
    }

    public void unsubscribe(Long chatId) {
        subscriptionRepository.deleteByChatId(chatId);
    }

    public TelegramSubscription updateSettings(Long chatId, Integer minDropPercentage,
                                                List<String> storeFilters, List<String> categoryFilters) {
        TelegramSubscription subscription = subscriptionRepository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found for chat: " + chatId));

        if (minDropPercentage != null) {
            subscription.setMinDropPercentage(minDropPercentage);
        }
        if (storeFilters != null) {
            subscription.setStoreFilters(storeFilters);
        }
        if (categoryFilters != null) {
            subscription.setCategoryFilters(categoryFilters);
        }

        return subscriptionRepository.save(subscription);
    }

    public int sendDiscountReport(Map<String, PriceAnalysisService.StoreDiscountGroup> discountsByStore) {
        if (!notificationEnabled) {
            log.info("Telegram notifications are disabled");
            return 0;
        }

        List<TelegramSubscription> activeSubscriptions = subscriptionRepository.findByActiveTrue();
        if (activeSubscriptions.isEmpty()) {
            log.info("No active Telegram subscriptions found");
            return 0;
        }

        String message = formatDiscountReport(discountsByStore);
        if (message.isEmpty()) {
            log.info("No discounted items to report");
            return 0;
        }

        int sentCount = 0;
        for (TelegramSubscription subscription : activeSubscriptions) {
            try {
                // Split message if too long (Telegram limit is 4096 characters)
                if (message.length() > 4000) {
                    List<String> chunks = splitMessage(message, 4000);
                    for (String chunk : chunks) {
                        sendMessage(subscription.getChatId(), chunk);
                    }
                } else {
                    sendMessage(subscription.getChatId(), message);
                }
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send discount report to chat {}", subscription.getChatId(), e);
            }
        }

        return sentCount;
    }

    private String formatDiscountReport(Map<String, PriceAnalysisService.StoreDiscountGroup> discountsByStore) {
        if (discountsByStore.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>🏷️ Discount Report</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        for (Map.Entry<String, PriceAnalysisService.StoreDiscountGroup> entry : discountsByStore.entrySet()) {
            String storeName = entry.getKey();
            PriceAnalysisService.StoreDiscountGroup group = entry.getValue();
            List<PriceAnalysisService.DiscountedItemDetail> items = group.items();

            sb.append(String.format("<b>🏪 %s</b> (%d items)\n", storeName, group.itemCount()));
            sb.append("──────────────────\n");

            // Limit to top 10 items per store
            int count = 0;
            for (PriceAnalysisService.DiscountedItemDetail item : items) {
                if (count >= 10) {
                    sb.append(String.format("   ... and %d more items\n", items.size() - 10));
                    break;
                }

                sb.append(String.format("• %s\n", truncate(item.product().getName(), 40)));
                sb.append(String.format("   <s>$%s</s> → <b>$%s</b> (-%,.0f%%)\n",
                        item.regularPrice().setScale(2, RoundingMode.HALF_UP),
                        item.salePrice().setScale(2, RoundingMode.HALF_UP),
                        item.discountPercentage()));

                if (item.promoDescription() != null && !item.promoDescription().isEmpty()) {
                    sb.append(String.format("   📢 %s\n", truncate(item.promoDescription(), 30)));
                }
                sb.append("\n");
                count++;
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private List<String> splitMessage(String message, int maxLength) {
        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + maxLength, message.length());
            // Try to break at a newline if possible
            if (end < message.length()) {
                int lastNewline = message.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline + 1;
                }
            }
            chunks.add(message.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
