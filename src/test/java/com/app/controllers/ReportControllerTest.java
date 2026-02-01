package com.app.controllers;

import com.app.models.PriceRecord;
import com.app.models.Product;
import com.app.models.Store;
import com.app.services.PriceAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private PriceAnalysisService priceAnalysisService;

    @InjectMocks
    private ReportController reportController;

    private Product testProduct;
    private Store testStore;
    private PriceAnalysisService.PriceDrop testPriceDrop;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .name("Test Product")
                .brand("Test Brand")
                .build();
        testProduct.setId("prod-123");

        testStore = Store.builder()
                .name("Test Store")
                .code("TEST")
                .build();
        testStore.setId("store-123");

        testPriceDrop = new PriceAnalysisService.PriceDrop(
                testProduct,
                testStore,
                new BigDecimal("10.00"),
                new BigDecimal("7.00"),
                new BigDecimal("3.00"),
                30.0,
                LocalDateTime.now()
        );
    }

    @Test
    void getPriceDrops_Success() {
        List<PriceAnalysisService.PriceDrop> drops = List.of(testPriceDrop);
        when(priceAnalysisService.getRecentPriceDrops(10, 50)).thenReturn(drops);

        ResponseEntity<List<PriceAnalysisService.PriceDrop>> response =
                reportController.getPriceDrops(10, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(30.0, response.getBody().get(0).dropPercentage());
    }

    @Test
    void getPriceDrops_DefaultParameters() {
        when(priceAnalysisService.getRecentPriceDrops(10, 50)).thenReturn(List.of());

        reportController.getPriceDrops(10, 50);

        verify(priceAnalysisService).getRecentPriceDrops(10, 50);
    }

    @Test
    void getPriceDrops_EmptyResults() {
        when(priceAnalysisService.getRecentPriceDrops(anyInt(), anyInt())).thenReturn(List.of());

        ResponseEntity<List<PriceAnalysisService.PriceDrop>> response =
                reportController.getPriceDrops(50, 100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void comparePrices_Success() {
        Map<String, PriceAnalysisService.StorePrice> storePrices = new HashMap<>();
        storePrices.put("TEST", new PriceAnalysisService.StorePrice(
                testStore,
                new BigDecimal("9.99"),
                false,
                null,
                LocalDateTime.now()
        ));

        PriceAnalysisService.PriceComparison comparison = new PriceAnalysisService.PriceComparison(
                testProduct,
                storePrices,
                "TEST",
                new BigDecimal("9.99")
        );

        when(priceAnalysisService.compareProductPrices("prod-123")).thenReturn(comparison);

        ResponseEntity<PriceAnalysisService.PriceComparison> response =
                reportController.comparePrices("prod-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TEST", response.getBody().lowestPriceStore());
    }

    @Test
    void comparePrices_ProductNotFound_ThrowsException() {
        when(priceAnalysisService.compareProductPrices("nonexistent"))
                .thenThrow(new IllegalArgumentException("Product not found"));

        assertThrows(IllegalArgumentException.class,
                () -> reportController.comparePrices("nonexistent"));
    }

    @Test
    void getPriceHistory_Success() {
        List<PriceAnalysisService.PricePoint> pricePoints = Arrays.asList(
                new PriceAnalysisService.PricePoint(
                        new BigDecimal("10.00"), false, LocalDateTime.now().minusDays(7)),
                new PriceAnalysisService.PricePoint(
                        new BigDecimal("8.00"), true, LocalDateTime.now())
        );

        PriceAnalysisService.PriceHistory history = new PriceAnalysisService.PriceHistory(
                testProduct, testStore, pricePoints);

        when(priceAnalysisService.getProductPriceHistory("prod-123", "store-123", 30))
                .thenReturn(history);

        ResponseEntity<PriceAnalysisService.PriceHistory> response =
                reportController.getPriceHistory("prod-123", "store-123", 30);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().pricePoints().size());
    }

    @Test
    void getCurrentSales_Success() {
        PriceRecord saleRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("10.00"))
                .salePrice(new BigDecimal("7.00"))
                .onSale(true)
                .build();

        Page<PriceRecord> salesPage = new PageImpl<>(List.of(saleRecord));
        when(priceAnalysisService.getCurrentSales(any(PageRequest.class))).thenReturn(salesPage);

        ResponseEntity<Page<PriceRecord>> response = reportController.getCurrentSales(0, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getCurrentSalesForStore_Success() {
        PriceRecord saleRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .onSale(true)
                .build();

        when(priceAnalysisService.getCurrentSalesForStore("RCSS", 50))
                .thenReturn(List.of(saleRecord));

        ResponseEntity<List<PriceRecord>> response =
                reportController.getCurrentSalesForStore("RCSS", 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getCurrentSalesForStore_LowercaseCode_ConvertsToUppercase() {
        when(priceAnalysisService.getCurrentSalesForStore("WALMART", 50))
                .thenReturn(List.of());

        reportController.getCurrentSalesForStore("walmart", 50);

        verify(priceAnalysisService).getCurrentSalesForStore("WALMART", 50);
    }

    @Test
    void getCurrentSalesForStore_StoreNotFound_ThrowsException() {
        when(priceAnalysisService.getCurrentSalesForStore("INVALID", 50))
                .thenThrow(new IllegalArgumentException("Store not found"));

        assertThrows(IllegalArgumentException.class,
                () -> reportController.getCurrentSalesForStore("INVALID", 50));
    }
}
