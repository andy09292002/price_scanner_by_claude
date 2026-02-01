package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "telegram_subscriptions")
public class TelegramSubscription extends BaseEntity {

    @Indexed(unique = true)
    private Long chatId;

    private boolean active;

    // Notify when price drops by this percentage or more (e.g., 20 = notify when 20%+ drop)
    private int minDropPercentage;

    // Filter by specific store codes (null or empty means all stores)
    private List<String> storeFilters;

    // Filter by specific category codes (null or empty means all categories)
    private List<String> categoryFilters;

    private String username;

    private String firstName;
}
