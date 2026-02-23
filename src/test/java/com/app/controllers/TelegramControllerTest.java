package com.app.controllers;

import com.app.models.TelegramSubscription;
import com.app.services.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramControllerTest {

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @InjectMocks
    private TelegramController telegramController;

    private TelegramSubscription testSubscription;

    @BeforeEach
    void setUp() {
        testSubscription = TelegramSubscription.builder()
                .chatId(12345L)
                .active(true)
                .minDropPercentage(15)
                .username("testuser")
                .build();
        testSubscription.setId("sub-123");
    }

    @Test
    void subscribe_Success_ReturnsSubscription() {
        when(telegramNotificationService.subscribe(12345L, null)).thenReturn(testSubscription);

        ResponseEntity<TelegramSubscription> response = telegramController.subscribe(12345L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(12345L, response.getBody().getChatId());
        assertTrue(response.getBody().isActive());
    }

    @Test
    void subscribe_WithUsername_ReturnsSubscription() {
        when(telegramNotificationService.subscribe(12345L, "testuser")).thenReturn(testSubscription);

        ResponseEntity<TelegramSubscription> response = telegramController.subscribe(12345L, "testuser");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("testuser", response.getBody().getUsername());
    }

    @Test
    void updateSettings_Success_ReturnsUpdatedSubscription() {
        testSubscription.setMinDropPercentage(25);
        when(telegramNotificationService.updateSettings(eq(12345L), eq(25), isNull(), isNull()))
                .thenReturn(testSubscription);

        ResponseEntity<TelegramSubscription> response = telegramController.updateSettings(
                12345L, 25, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(25, response.getBody().getMinDropPercentage());
    }

    @Test
    void updateSettings_WithAllParams_ReturnsUpdatedSubscription() {
        List<String> storeFilters = List.of("WALMART", "RCSS");
        List<String> categoryFilters = List.of("2877");
        testSubscription.setMinDropPercentage(30);
        testSubscription.setStoreFilters(storeFilters);
        testSubscription.setCategoryFilters(categoryFilters);

        when(telegramNotificationService.updateSettings(12345L, 30, storeFilters, categoryFilters))
                .thenReturn(testSubscription);

        ResponseEntity<TelegramSubscription> response = telegramController.updateSettings(
                12345L, 30, storeFilters, categoryFilters);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(30, response.getBody().getMinDropPercentage());
        assertEquals(storeFilters, response.getBody().getStoreFilters());
        assertEquals(categoryFilters, response.getBody().getCategoryFilters());
    }

    @Test
    void updateSettings_WithNullOptionalParams_Success() {
        when(telegramNotificationService.updateSettings(12345L, null, null, null))
                .thenReturn(testSubscription);

        ResponseEntity<TelegramSubscription> response = telegramController.updateSettings(
                12345L, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void updateSettings_SubscriptionNotFound_ThrowsException() {
        when(telegramNotificationService.updateSettings(eq(99999L), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Subscription not found for chat: 99999"));

        assertThrows(IllegalArgumentException.class,
                () -> telegramController.updateSettings(99999L, 15, null, null));
    }

    @Test
    void unsubscribe_Success_Returns204() {
        doNothing().when(telegramNotificationService).unsubscribe(12345L);

        ResponseEntity<Void> response = telegramController.unsubscribe(12345L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(telegramNotificationService).unsubscribe(12345L);
    }

    @Test
    void sendTestMessage_Success_Returns200() {
        doNothing().when(telegramNotificationService).sendMessage(eq(12345L), anyString());

        ResponseEntity<Void> response = telegramController.sendTestMessage(12345L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(telegramNotificationService).sendMessage(eq(12345L), contains("test message"));
    }

    @Test
    void sendTestMessage_CallsServiceWithCorrectChatId() {
        doNothing().when(telegramNotificationService).sendMessage(eq(67890L), anyString());

        telegramController.sendTestMessage(67890L);

        verify(telegramNotificationService).sendMessage(eq(67890L), anyString());
    }
}
