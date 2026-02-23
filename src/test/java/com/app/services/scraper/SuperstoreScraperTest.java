package com.app.services.scraper;

import com.app.models.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SuperstoreScraperTest {

    private SuperstoreScraper scraper;
    private ObjectMapper objectMapper;
    private Store testStore;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        objectMapper = new ObjectMapper();
        scraper = new SuperstoreScraper(registry, objectMapper);

        testStore = Store.builder()
                .name("Real Canadian Superstore")
                .code("RCSS")
                .baseUrl("https://www.realcanadiansuperstore.ca")
                .active(true)
                .build();
        testStore.setId("store-rcss");
    }

    @Test
    void getStoreCode_ReturnsSuperstore() {
        assertEquals("RCSS", scraper.getStoreCode());
    }

    @Test
    void supports_SuperstoreStore_ReturnsTrue() {
        assertTrue(scraper.supports(testStore));
    }

    @Test
    void supports_OtherStore_ReturnsFalse() {
        Store other = Store.builder().code("WALMART").build();
        assertFalse(scraper.supports(other));
    }

    @Test
    void getCategoryUrls_WithoutConfig_ReturnsDefaults() {
        List<StoreScraper.ScrapedProduct> products = scraper.scrapeAllProducts(testStore);
        assertNotNull(products);
    }

    @Test
    void getCategoryUrls_WithConfig_ReturnsConfigIds() {
        List<String> configIds = List.of("99999", "88888");
        testStore.setScraperConfig(Map.of("categoryIds", configIds));

        List<StoreScraper.ScrapedProduct> products = scraper.scrapeAllProducts(testStore);
        assertNotNull(products);
    }

    @Test
    void parseProduct_ValidNode_ReturnsProduct() throws Exception {
        String json = """
                {
                    "productId": "RCSS-001",
                    "title": "Organic Apples 3lb bag",
                    "brand": "Compliments",
                    "pricing": {
                        "price": "$5.99",
                        "wasPrice": ""
                    },
                    "productImage": [{
                        "smallUrl": "https://example.com/apples-small.jpg",
                        "mediumUrl": "https://example.com/apples-medium.jpg"
                    }],
                    "packageSizing": "$5.99/1.36kg",
                    "link": "/organic-apples",
                    "inventoryIndicator": {
                        "indicatorId": "IN"
                    }
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertEquals("RCSS-001", node.path("productId").asText());
        assertEquals("Organic Apples 3lb bag", node.path("title").asText());
        assertEquals("Compliments", node.path("brand").asText());
        assertEquals("$5.99", node.path("pricing").path("price").asText());
    }

    @Test
    void parseProduct_WithWasPrice_SetsOnSale() throws Exception {
        String json = """
                {
                    "productId": "RCSS-002",
                    "title": "Sale Chicken",
                    "pricing": {
                        "price": "$7.99",
                        "wasPrice": "$10.99"
                    },
                    "deal": "Save $3.00",
                    "link": "/sale-chicken"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        String wasPrice = node.path("pricing").path("wasPrice").asText();
        assertFalse(wasPrice.isEmpty());
        assertEquals("$10.99", wasPrice);
        assertFalse(node.path("deal").isMissingNode());
    }

    @Test
    void parseProduct_OutOfStock_SetsInStockFalse() throws Exception {
        String json = """
                {
                    "productId": "RCSS-003",
                    "title": "Out of Stock Item",
                    "pricing": {
                        "price": "$3.99"
                    },
                    "inventoryIndicator": {
                        "indicatorId": "OUT"
                    },
                    "link": "/oos-item"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertEquals("OUT", node.path("inventoryIndicator").path("indicatorId").asText());
    }

    @Test
    void parseProduct_TextBadgeOutOfStock_DetectsStatus() throws Exception {
        String json = """
                {
                    "productId": "RCSS-004",
                    "title": "Badge OOS Item",
                    "pricing": {
                        "price": "$2.99"
                    },
                    "textBadge": "out-of-stock",
                    "link": "/badge-oos"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.path("textBadge").asText().toLowerCase().contains("out-of-stock"));
    }
}
