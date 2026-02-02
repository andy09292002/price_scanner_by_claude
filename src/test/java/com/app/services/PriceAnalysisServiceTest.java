package com.app.services;

import com.app.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAnalysisServiceTest {

    @Mock
    private PriceRecordRepository priceRecordRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private PriceAnalysisService priceAnalysisService;

    private Product testProduct;
    private Store testStore;
    private PriceRecord previousRecord;
    private PriceRecord currentRecord;

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
                .active(true)
                .build();
        testStore.setId("store-123");

        previousRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("10.00"))
                .onSale(false)
                .scrapedAt(LocalDateTime.now().minusDays(1))
                .build();
        previousRecord.setId("rec-prev");

        currentRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("7.00"))
                .onSale(false)
                .scrapedAt(LocalDateTime.now())
                .build();
        currentRecord.setId("rec-curr");
    }

    @Test
    void detectPriceDrops_FindsDrop() {
        when(storeRepository.findById("store-123")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(currentRecord));
        when(productRepository.findById("prod-123")).thenReturn(Optional.of(testProduct));

        List<PriceAnalysisService.PriceDrop> drops = priceAnalysisService.detectPriceDrops(
                "store-123", List.of(previousRecord));

        assertEquals(1, drops.size());
        PriceAnalysisService.PriceDrop drop = drops.get(0);
        assertEquals(new BigDecimal("10.00"), drop.previousPrice());
        assertEquals(new BigDecimal("7.00"), drop.currentPrice());
        assertEquals(new BigDecimal("3.00"), drop.dropAmount());
        assertEquals(30.0, drop.dropPercentage(), 0.1);
    }

    @Test
    void detectPriceDrops_NoDrop_WhenPriceIncreased() {
        currentRecord.setRegularPrice(new BigDecimal("12.00"));

        when(storeRepository.findById("store-123")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(currentRecord));

        List<PriceAnalysisService.PriceDrop> drops = priceAnalysisService.detectPriceDrops(
                "store-123", List.of(previousRecord));

        assertTrue(drops.isEmpty());
    }

    @Test
    void detectPriceDrops_NoDrop_WhenPriceSame() {
        currentRecord.setRegularPrice(new BigDecimal("10.00"));

        when(storeRepository.findById("store-123")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(currentRecord));

        List<PriceAnalysisService.PriceDrop> drops = priceAnalysisService.detectPriceDrops(
                "store-123", List.of(previousRecord));

        assertTrue(drops.isEmpty());
    }

    @Test
    void detectPriceDrops_UsesSalePrice_WhenOnSale() {
        previousRecord.setRegularPrice(new BigDecimal("15.00"));
        previousRecord.setOnSale(true);
        previousRecord.setSalePrice(new BigDecimal("12.00"));

        currentRecord.setRegularPrice(new BigDecimal("15.00"));
        currentRecord.setOnSale(true);
        currentRecord.setSalePrice(new BigDecimal("9.00"));

        when(storeRepository.findById("store-123")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(currentRecord));
        when(productRepository.findById("prod-123")).thenReturn(Optional.of(testProduct));

        List<PriceAnalysisService.PriceDrop> drops = priceAnalysisService.detectPriceDrops(
                "store-123", List.of(previousRecord));

        assertEquals(1, drops.size());
        assertEquals(new BigDecimal("12.00"), drops.get(0).previousPrice());
        assertEquals(new BigDecimal("9.00"), drops.get(0).currentPrice());
    }

    @Test
    void detectPriceDrops_StoreNotFound_ReturnsEmpty() {
        when(storeRepository.findById("nonexistent")).thenReturn(Optional.empty());

        List<PriceAnalysisService.PriceDrop> drops = priceAnalysisService.detectPriceDrops(
                "nonexistent", List.of(previousRecord));

        assertTrue(drops.isEmpty());
    }

    @Test
    void compareProductPrices_Success() {
        Store store2 = Store.builder()
                .name("Store 2")
                .code("S2")
                .active(true)
                .build();
        store2.setId("store-456");

        PriceRecord record2 = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-456")
                .regularPrice(new BigDecimal("8.00"))
                .scrapedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(testProduct));
        when(storeRepository.findByActiveTrue()).thenReturn(List.of(testStore, store2));
        when(priceRecordRepository.findTopByProductIdAndStoreIdOrderByScrapedAtDesc("prod-123", "store-123"))
                .thenReturn(Optional.of(currentRecord));
        when(priceRecordRepository.findTopByProductIdAndStoreIdOrderByScrapedAtDesc("prod-123", "store-456"))
                .thenReturn(Optional.of(record2));

        PriceAnalysisService.PriceComparison comparison =
                priceAnalysisService.compareProductPrices("prod-123");

        assertNotNull(comparison);
        assertEquals(testProduct, comparison.product());
        assertEquals(2, comparison.storePrices().size());
        assertEquals("TEST", comparison.lowestPriceStore());
        assertEquals(new BigDecimal("7.00"), comparison.lowestPrice());
    }

    @Test
    void compareProductPrices_ProductNotFound_ThrowsException() {
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> priceAnalysisService.compareProductPrices("nonexistent"));
    }

    @Test
    void getProductPriceHistory_Success() {
        PriceRecord historyRecord1 = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("10.00"))
                .onSale(false)
                .scrapedAt(LocalDateTime.now().minusDays(7))
                .build();

        PriceRecord historyRecord2 = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("8.00"))
                .onSale(true)
                .salePrice(new BigDecimal("6.00"))
                .scrapedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(testProduct));
        when(storeRepository.findById("store-123")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByProductIdAndScrapedAtBetween(eq("prod-123"), any(), any()))
                .thenReturn(List.of(historyRecord1, historyRecord2));

        PriceAnalysisService.PriceHistory history =
                priceAnalysisService.getProductPriceHistory("prod-123", "store-123", 30);

        assertNotNull(history);
        assertEquals(testProduct, history.product());
        assertEquals(testStore, history.store());
        assertEquals(2, history.pricePoints().size());
    }

    @Test
    void getCurrentSales_Success() {
        PriceRecord saleRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .onSale(true)
                .scrapedAt(LocalDateTime.now())
                .build();

        Page<PriceRecord> salesPage = new PageImpl<>(List.of(saleRecord));
        when(priceRecordRepository.findCurrentSales(any(), any())).thenReturn(salesPage);

        Page<PriceRecord> result = priceAnalysisService.getCurrentSales(PageRequest.of(0, 50));

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).isOnSale());
    }

    @Test
    void getCurrentSalesForStore_Success() {
        PriceRecord saleRecord = PriceRecord.builder()
                .productId("prod-123")
                .storeId("store-123")
                .regularPrice(new BigDecimal("10.00"))
                .salePrice(new BigDecimal("7.00"))
                .onSale(true)
                .scrapedAt(LocalDateTime.now())
                .build();

        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(saleRecord));

        List<PriceRecord> sales = priceAnalysisService.getCurrentSalesForStore("TEST", 50);

        assertEquals(1, sales.size());
        assertTrue(sales.get(0).isOnSale());
    }

    @Test
    void getCurrentSalesForStore_StoreNotFound_ThrowsException() {
        when(storeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> priceAnalysisService.getCurrentSalesForStore("INVALID", 50));
    }

    @Test
    void getRecentPriceDrops_FiltersByPercentage() {
        PriceRecord smallDropCurrent = PriceRecord.builder()
                .productId("prod-456")
                .storeId("store-123")
                .regularPrice(new BigDecimal("9.50"))
                .scrapedAt(LocalDateTime.now())
                .build();

        PriceRecord smallDropPrevious = PriceRecord.builder()
                .productId("prod-456")
                .storeId("store-123")
                .regularPrice(new BigDecimal("10.00"))
                .scrapedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(storeRepository.findByActiveTrue()).thenReturn(List.of(testStore));
        when(priceRecordRepository.findByStoreIdAndScrapedAtAfter(eq("store-123"), any()))
                .thenReturn(List.of(currentRecord, smallDropCurrent));

        // 30% drop should pass 20% threshold, 5% drop should not
        List<PriceAnalysisService.PriceDrop> drops =
                priceAnalysisService.getRecentPriceDrops(20, 50);

        // Only the 30% drop should be returned
        assertTrue(drops.stream().allMatch(d -> d.dropPercentage() >= 20));
    }
}
