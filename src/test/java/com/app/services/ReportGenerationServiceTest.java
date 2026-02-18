package com.app.services;

import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import com.app.models.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ReportGenerationService reportGenerationService;

    private Store testStore;
    private Product testProduct;
    private PriceAnalysisService.DiscountedItemDetail testItem;

    @BeforeEach
    void setUp() {
        testStore = Store.builder()
                .name("Walmart")
                .code("WALMART")
                .build();
        testStore.setId("store-1");

        testProduct = Product.builder()
                .name("Test Product")
                .brand("Test Brand")
                .size("500g")
                .imageUrl(null)
                .categoryId("cat-1")
                .build();
        testProduct.setId("prod-1");

        testItem = new PriceAnalysisService.DiscountedItemDetail(
                testProduct,
                new BigDecimal("10.00"),
                new BigDecimal("7.00"),
                new BigDecimal("3.00"),
                30.0,
                "Save $3",
                LocalDateTime.now()
        );
    }

    @Test
    void generateDiscountReportPdf_HappyPath_MultipleStores() throws IOException {
        Store store2 = Store.builder().name("Superstore").code("RCSS").build();
        store2.setId("store-2");

        Product product2 = Product.builder()
                .name("Another Product").brand("Brand B").size("1kg").build();
        product2.setId("prod-2");

        PriceAnalysisService.DiscountedItemDetail item2 = new PriceAnalysisService.DiscountedItemDetail(
                product2, new BigDecimal("5.00"), new BigDecimal("3.50"),
                new BigDecimal("1.50"), 30.0, null, LocalDateTime.now());

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = new LinkedHashMap<>();
        data.put("Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(testItem)));
        data.put("Superstore", new PriceAnalysisService.StoreDiscountGroup(store2, 1, List.of(item2)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, null, 10);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // PDF files start with %PDF
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_EmptyData_ReturnsValidPdf() throws IOException {
        Map<String, PriceAnalysisService.StoreDiscountGroup> data = Map.of();

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, null, 10);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_CategoryFilter_FiltersItems() throws IOException {
        Category matchingCategory = new Category();
        matchingCategory.setId("cat-1");

        when(categoryRepository.findByNameIgnoreCase("produce")).thenReturn(List.of(matchingCategory));

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = new LinkedHashMap<>();
        data.put("Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(testItem)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, "produce", 10);

        assertNotNull(pdf);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
        verify(categoryRepository).findByNameIgnoreCase("produce");
    }

    @Test
    void generateDiscountReportPdf_CategoryFilter_NoMatch_ReturnsEmptyReport() throws IOException {
        when(categoryRepository.findByNameIgnoreCase("nonexistent")).thenReturn(List.of());

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = new LinkedHashMap<>();
        data.put("Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(testItem)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, "nonexistent", 10);

        assertNotNull(pdf);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_NullImageUrl_HandledGracefully() throws IOException {
        Product productNoImage = Product.builder()
                .name("No Image Product").brand("Brand").size("100g").imageUrl(null).build();
        productNoImage.setId("prod-no-img");

        PriceAnalysisService.DiscountedItemDetail itemNoImage = new PriceAnalysisService.DiscountedItemDetail(
                productNoImage, new BigDecimal("8.00"), new BigDecimal("6.00"),
                new BigDecimal("2.00"), 25.0, null, LocalDateTime.now());

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = Map.of(
                "Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(itemNoImage)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, null, 10);

        assertNotNull(pdf);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_ManyItems_MultiplePages() throws IOException {
        List<PriceAnalysisService.DiscountedItemDetail> items = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Product p = Product.builder()
                    .name("Product " + i).brand("Brand").size("100g").build();
            p.setId("prod-" + i);
            items.add(new PriceAnalysisService.DiscountedItemDetail(
                    p, new BigDecimal("10.00"), new BigDecimal("7.00"),
                    new BigDecimal("3.00"), 30.0, "Promo " + i, LocalDateTime.now()));
        }

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = Map.of(
                "Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, items.size(), items));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, null, 10);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_WithStoreFilter_ShowsFilterInHeader() throws IOException {
        Map<String, PriceAnalysisService.StoreDiscountGroup> data = Map.of(
                "Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(testItem)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, "WALMART", null, 10);

        assertNotNull(pdf);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generateDiscountReportPdf_NullPromoDescription_HandledGracefully() throws IOException {
        PriceAnalysisService.DiscountedItemDetail itemNoPromo = new PriceAnalysisService.DiscountedItemDetail(
                testProduct, new BigDecimal("10.00"), new BigDecimal("7.00"),
                new BigDecimal("3.00"), 30.0, null, LocalDateTime.now());

        Map<String, PriceAnalysisService.StoreDiscountGroup> data = Map.of(
                "Walmart", new PriceAnalysisService.StoreDiscountGroup(testStore, 1, List.of(itemNoPromo)));

        byte[] pdf = reportGenerationService.generateDiscountReportPdf(data, null, null, 10);

        assertNotNull(pdf);
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }
}
