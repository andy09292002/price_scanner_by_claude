package com.app.services;

import com.app.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private TelegramSubscriptionRepository subscriptionRepository;

    private TelegramNotificationService service;
    private TelegramNotificationService disabledService;

    private TelegramSubscription testSubscription;
    private Product testProduct;
    private Store testStore;

    @BeforeEach
    void setUp() throws Exception {
        service = spy(new TelegramNotificationService(
                subscriptionRepository, "test-token", "test-bot", true, 15));

        disabledService = spy(new TelegramNotificationService(
                subscriptionRepository, "test-token", "test-bot", false, 15));

        testSubscription = TelegramSubscription.builder()
                .chatId(12345L)
                .active(true)
                .minDropPercentage(15)
                .username("testuser")
                .build();
        testSubscription.setId("sub-123");

        testProduct = Product.builder()
                .name("Test Product")
                .brand("Test Brand")
                .categoryId("cat-123")
                .build();
        testProduct.setId("prod-123");

        testStore = Store.builder()
                .name("Test Store")
                .code("TEST")
                .active(true)
                .build();
        testStore.setId("store-123");
    }

    @Test
    void subscribe_NewSubscription_CreatesAndReturns() {
        when(subscriptionRepository.findByChatId(12345L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(TelegramSubscription.class))).thenAnswer(i -> i.getArgument(0));

        TelegramSubscription result = service.subscribe(12345L, "testuser");

        assertNotNull(result);
        assertTrue(result.isActive());
        verify(subscriptionRepository).save(any(TelegramSubscription.class));
    }

    @Test
    void subscribe_ExistingSubscription_ReactivatesAndReturns() {
        TelegramSubscription existing = TelegramSubscription.builder()
                .chatId(12345L)
                .active(false)
                .minDropPercentage(20)
                .build();
        when(subscriptionRepository.findByChatId(12345L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(TelegramSubscription.class))).thenAnswer(i -> i.getArgument(0));

        TelegramSubscription result = service.subscribe(12345L, "testuser");

        assertTrue(result.isActive());
    }

    @Test
    void unsubscribe_Success_DeletesSubscription() {
        doNothing().when(subscriptionRepository).deleteByChatId(12345L);

        service.unsubscribe(12345L);

        verify(subscriptionRepository).deleteByChatId(12345L);
    }

    @Test
    void updateSettings_Success_UpdatesNonNullFields() {
        when(subscriptionRepository.findByChatId(12345L)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(TelegramSubscription.class))).thenAnswer(i -> i.getArgument(0));

        TelegramSubscription result = service.updateSettings(12345L, 25,
                List.of("WALMART"), List.of("2877"));

        assertEquals(25, result.getMinDropPercentage());
        assertEquals(List.of("WALMART"), result.getStoreFilters());
        assertEquals(List.of("2877"), result.getCategoryFilters());
    }

    @Test
    void updateSettings_NotFound_ThrowsIllegalArgument() {
        when(subscriptionRepository.findByChatId(99999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(99999L, 25, null, null));
    }

    @Test
    void updateSettings_OnlyMinDrop_UpdatesOnlyThatField() {
        when(subscriptionRepository.findByChatId(12345L)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(TelegramSubscription.class))).thenAnswer(i -> i.getArgument(0));

        TelegramSubscription result = service.updateSettings(12345L, 30, null, null);

        assertEquals(30, result.getMinDropPercentage());
        assertNull(result.getStoreFilters());
    }

    @Test
    void sendMessage_WhenEnabled_SendsMessage() throws Exception {
        doReturn(null).when(service).execute(any(SendMessage.class));

        service.sendMessage(12345L, "Hello");

        verify(service).execute(any(SendMessage.class));
    }

    @Test
    void sendMessage_WhenDisabled_DoesNothing() throws Exception {
        disabledService.sendMessage(12345L, "Hello");

        verify(disabledService, never()).execute(any(SendMessage.class));
    }

    @Test
    void sendPriceDropNotifications_WhenDisabled_DoesNothing() {
        List<PriceAnalysisService.PriceDrop> drops = List.of(
                new PriceAnalysisService.PriceDrop(testProduct, testStore,
                        new BigDecimal("10.00"), new BigDecimal("7.00"),
                        new BigDecimal("3.00"), 30.0, LocalDateTime.now())
        );

        disabledService.sendPriceDropNotifications(drops);

        verify(subscriptionRepository, never()).findByActiveTrue();
    }

    @Test
    void sendPriceDropNotifications_FiltersByMinDropPercentage() throws Exception {
        testSubscription.setMinDropPercentage(50);
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        List<PriceAnalysisService.PriceDrop> drops = List.of(
                new PriceAnalysisService.PriceDrop(testProduct, testStore,
                        new BigDecimal("10.00"), new BigDecimal("8.00"),
                        new BigDecimal("2.00"), 20.0, LocalDateTime.now())
        );

        service.sendPriceDropNotifications(drops);

        // Drop is 20% but min is 50%, so no message should be sent via execute
        verify(service, never()).execute(any(SendMessage.class));
    }

    @Test
    void sendPriceDropNotifications_FiltersByStoreFilters() throws Exception {
        testSubscription.setStoreFilters(List.of("WALMART"));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        List<PriceAnalysisService.PriceDrop> drops = List.of(
                new PriceAnalysisService.PriceDrop(testProduct, testStore,
                        new BigDecimal("10.00"), new BigDecimal("5.00"),
                        new BigDecimal("5.00"), 50.0, LocalDateTime.now())
        );

        service.sendPriceDropNotifications(drops);

        // Store is TEST but filter is WALMART, so no message
        verify(service, never()).execute(any(SendMessage.class));
    }

    @Test
    void sendPriceDropNotifications_FiltersByCategoryFilters() throws Exception {
        testSubscription.setCategoryFilters(List.of("cat-999"));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        List<PriceAnalysisService.PriceDrop> drops = List.of(
                new PriceAnalysisService.PriceDrop(testProduct, testStore,
                        new BigDecimal("10.00"), new BigDecimal("5.00"),
                        new BigDecimal("5.00"), 50.0, LocalDateTime.now())
        );

        service.sendPriceDropNotifications(drops);

        // Product category is cat-123 but filter is cat-999
        verify(service, never()).execute(any(SendMessage.class));
    }

    @Test
    void sendPriceDropNotifications_LimitsTo10DropsPerSubscription() throws Exception {
        doReturn(null).when(service).execute(any(SendMessage.class));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        List<PriceAnalysisService.PriceDrop> drops = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Product p = Product.builder().name("Product " + i).categoryId("cat-123").build();
            p.setId("prod-" + i);
            drops.add(new PriceAnalysisService.PriceDrop(p, testStore,
                    new BigDecimal("10.00"), new BigDecimal("5.00"),
                    new BigDecimal("5.00"), 50.0, LocalDateTime.now()));
        }

        service.sendPriceDropNotifications(drops);

        // Should send one message (with max 10 drops)
        verify(service, times(1)).execute(any(SendMessage.class));
    }

    @Test
    void sendDiscountReport_WhenDisabled_ReturnsZero() {
        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts = Map.of(
                "Test Store", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of()));

        int result = disabledService.sendDiscountReport(discounts);

        assertEquals(0, result);
    }

    @Test
    void sendDiscountReport_EmptyData_ReturnsZero() {
        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts = Map.of();
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        int result = service.sendDiscountReport(discounts);

        assertEquals(0, result);
    }

    @Test
    void sendDiscountReport_SplitsLongMessages() throws Exception {
        doReturn(null).when(service).execute(any(SendMessage.class));

        List<PriceAnalysisService.DiscountedItemDetail> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Product p = Product.builder().name("Very Long Product Name " + i + " with lots of description").build();
            p.setId("prod-" + i);
            items.add(new PriceAnalysisService.DiscountedItemDetail(
                    p, new BigDecimal("20.00"), new BigDecimal("10.00"),
                    new BigDecimal("10.00"), 50.0, "Buy one get one free promo description",
                    LocalDateTime.now()));
        }

        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts = Map.of(
                "Test Store", new PriceAnalysisService.StoreDiscountGroup(testStore, items.size(), items));

        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(testSubscription));

        int result = service.sendDiscountReport(discounts);

        assertEquals(1, result);
        verify(service, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void onUpdateReceived_StartCommand_CreatesSubscription() throws Exception {
        doReturn(null).when(service).execute(any(SendMessage.class));

        Update update = createMockUpdate("/start", 12345L, "testuser", "Test");

        when(subscriptionRepository.findByChatId(12345L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(TelegramSubscription.class))).thenAnswer(i -> {
            TelegramSubscription sub = i.getArgument(0);
            sub.setId("new-sub");
            return sub;
        });

        service.onUpdateReceived(update);

        verify(subscriptionRepository).save(any(TelegramSubscription.class));
    }

    @Test
    void onUpdateReceived_NoMessage_DoesNothing() {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        service.onUpdateReceived(update);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void onUpdateReceived_NoText_DoesNothing() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(false);

        service.onUpdateReceived(update);

        verify(subscriptionRepository, never()).save(any());
    }

    private Update createMockUpdate(String text, Long chatId, String username, String firstName) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);
        when(user.getFirstName()).thenReturn(firstName);

        return update;
    }
}
