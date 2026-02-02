package com.app.services;

import com.app.models.*;
import com.app.services.scraper.StoreScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductMatchingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductMatchingService productMatchingService;

    private Store testStore;
    private StoreScraper.ScrapedProduct scrapedProduct;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        testStore = Store.builder()
                .name("Test Store")
                .code("TEST")
                .build();
        testStore.setId("store-123");

        scrapedProduct = new StoreScraper.ScrapedProduct(
                "sku-12345",
                "Organic Bananas 1kg",
                "Dole",
                "1kg",
                "kg",
                "Fruits",
                "http://example.com/banana.jpg",
                new BigDecimal("2.99"),
                new BigDecimal("1.99"),
                new BigDecimal("1.99"),
                true,
                "Weekly Special",
                true,
                "http://example.com/products/bananas"
        );

        Map<String, String> storeProductIds = new HashMap<>();
        storeProductIds.put("TEST", "sku-12345");

        existingProduct = Product.builder()
                .name("Organic Bananas 1kg")
                .normalizedName("organic bananas 1kg")
                .brand("Dole")
                .size("1kg")
                .imageUrl("http://example.com/banana.jpg")
                .storeProductIds(storeProductIds)
                .build();
        existingProduct.setId("prod-123");
    }

    @Test
    void findOrCreateProduct_FindsByStoreProductId() {
        when(productRepository.findByStoreCodeAndStoreProductId("TEST", "sku-12345"))
                .thenReturn(Optional.of(existingProduct));

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertEquals(existingProduct.getId(), result.getId());
        verify(productRepository, never()).save(any());
    }

    @Test
    void findOrCreateProduct_FindsByNormalizedName() {
        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName("organic bananas 1kg"))
                .thenReturn(Optional.of(existingProduct));

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertEquals(existingProduct.getId(), result.getId());
    }

    @Test
    void findOrCreateProduct_CreatesNewProduct_WhenNotFound() {
        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName(anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByCode("fruits"))
                .thenReturn(Optional.empty());

        Category newCategory = Category.builder()
                .name("Fruits")
                .code("fruits")
                .build();
        newCategory.setId("cat-123");
        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);

        Product newProduct = Product.builder()
                .name("Organic Bananas 1kg")
                .normalizedName("organic bananas 1kg")
                .build();
        newProduct.setId("prod-new");
        when(productRepository.save(any(Product.class))).thenReturn(newProduct);

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertNotNull(result);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void findOrCreateProduct_AddsStoreMapping_WhenFoundByName() {
        existingProduct.setStoreProductIds(new HashMap<>());

        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName("organic bananas 1kg"))
                .thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertTrue(result.getStoreProductIds().containsKey("TEST"));
        assertEquals("sku-12345", result.getStoreProductIds().get("TEST"));
    }

    @Test
    void findOrCreateProduct_UpdatesImageUrl_WhenMissing() {
        existingProduct.setImageUrl(null);

        when(productRepository.findByStoreCodeAndStoreProductId("TEST", "sku-12345"))
                .thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertEquals("http://example.com/banana.jpg", result.getImageUrl());
        verify(productRepository).save(existingProduct);
    }

    @Test
    void findOrCreateProduct_UpdatesBrand_WhenMissing() {
        existingProduct.setBrand(null);

        when(productRepository.findByStoreCodeAndStoreProductId("TEST", "sku-12345"))
                .thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        assertEquals("Dole", result.getBrand());
        verify(productRepository).save(existingProduct);
    }

    @Test
    void normalizeProductName_RemovesSpecialCharacters() {
        String result = productMatchingService.normalizeProductName("Organic Bananas (1kg)!");

        assertEquals("organic bananas 1kg", result);
    }

    @Test
    void normalizeProductName_TrimsWhitespace() {
        String result = productMatchingService.normalizeProductName("  Organic   Bananas  ");

        assertEquals("organic bananas", result);
    }

    @Test
    void normalizeProductName_HandlesNull() {
        String result = productMatchingService.normalizeProductName(null);

        assertNull(result);
    }

    @Test
    void calculateSimilarity_ExactMatch() {
        double similarity = productMatchingService.calculateSimilarity(
                "Organic Bananas 1kg",
                "Organic Bananas 1kg"
        );

        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void calculateSimilarity_PartialMatch() {
        double similarity = productMatchingService.calculateSimilarity(
                "Organic Bananas 1kg",
                "Organic Apples 1kg"
        );

        // "organic", "1kg" match, "bananas" and "apples" don't
        assertTrue(similarity > 0.0 && similarity < 1.0);
    }

    @Test
    void calculateSimilarity_NoMatch() {
        double similarity = productMatchingService.calculateSimilarity(
                "Organic Bananas",
                "Whole Milk 2L"
        );

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void calculateSimilarity_HandleNullFirst() {
        double similarity = productMatchingService.calculateSimilarity(null, "Bananas");

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void calculateSimilarity_HandleNullSecond() {
        double similarity = productMatchingService.calculateSimilarity("Bananas", null);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void findOrCreateProduct_CreatesCategory_WhenNotExists() {
        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName(anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByCode("fruits"))
                .thenReturn(Optional.empty());

        Category newCategory = Category.builder()
                .name("Fruits")
                .code("fruits")
                .build();
        newCategory.setId("cat-new");

        when(categoryRepository.save(any(Category.class))).thenReturn(newCategory);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("prod-new");
            return p;
        });

        productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertEquals("Fruits", categoryCaptor.getValue().getName());
        assertEquals("fruits", categoryCaptor.getValue().getCode());
    }

    @Test
    void findOrCreateProduct_ReusesExistingCategory() {
        Category existingCategory = Category.builder()
                .name("Fruits")
                .code("fruits")
                .build();
        existingCategory.setId("cat-existing");

        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName(anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByCode("fruits"))
                .thenReturn(Optional.of(existingCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("prod-new");
            return p;
        });

        productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findOrCreateProduct_SetsCorrectStoreProductId() {
        when(productRepository.findByStoreCodeAndStoreProductId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(productRepository.findByNormalizedName(anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByCode(anyString()))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId("cat-id");
            return c;
        });

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(productCaptor.capture())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("prod-new");
            return p;
        });

        productMatchingService.findOrCreateProduct(scrapedProduct, testStore);

        Product savedProduct = productCaptor.getValue();
        assertNotNull(savedProduct.getStoreProductIds());
        assertEquals("sku-12345", savedProduct.getStoreProductIds().get("TEST"));
    }
}
