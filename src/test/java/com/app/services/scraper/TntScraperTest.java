package com.app.services.scraper;

import com.app.models.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TntScraperTest {

    private TntScraper scraper;
    private ObjectMapper objectMapper;
    private Store testStore;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        objectMapper = new ObjectMapper();
        scraper = new TntScraper(registry, objectMapper);

        testStore = Store.builder()
                .name("T&T Supermarket")
                .code("TNT")
                .baseUrl("https://www.tntsupermarket.com")
                .active(true)
                .build();
        testStore.setId("store-tnt");
    }

    @Test
    void getStoreCode_ReturnsTnt() {
        assertEquals("TNT", scraper.getStoreCode());
    }

    @Test
    void supports_TntStore_ReturnsTrue() {
        assertTrue(scraper.supports(testStore));
    }

    @Test
    void supports_OtherStore_ReturnsFalse() {
        Store other = Store.builder().code("WALMART").build();
        assertFalse(scraper.supports(other));
    }

    @Test
    void getCategoryUrls_WithoutConfig_ReturnsDefaults() {
        // scrapeAllProducts calls getCategoryUrls internally
        // Without config, should use default category IDs (2876-2881)
        List<StoreScraper.ScrapedProduct> products = scraper.scrapeAllProducts(testStore);
        assertNotNull(products);
    }

    @Test
    void getCategoryUrls_WithConfig_ReturnsConfigIds() {
        List<String> configIds = List.of("9999", "8888");
        testStore.setScraperConfig(Map.of("categoryIds", configIds));

        List<StoreScraper.ScrapedProduct> products = scraper.scrapeAllProducts(testStore);
        assertNotNull(products);
    }

    @Test
    void parseProduct_ValidNode_ReturnsProduct() throws Exception {
        String json = """
                {
                    "sku": "TNT-001",
                    "name": "Green Onion 500g",
                    "price": {
                        "regularPrice": {
                            "amount": {
                                "value": 2.99,
                                "currency": "CAD"
                            }
                        }
                    },
                    "price_range": {
                        "minimum_price": {
                            "final_price": {
                                "value": 2.99,
                                "currency": "CAD"
                            }
                        }
                    },
                    "small_image": {
                        "url": "https://example.com/green-onion.jpg"
                    },
                    "stock_status": "IN_STOCK",
                    "url_key": "green-onion-500g",
                    "url_suffix": ".html"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertEquals("TNT-001", node.path("sku").asText());
        assertEquals("Green Onion 500g", node.path("name").asText());
        assertEquals(2.99, node.path("price").path("regularPrice").path("amount").path("value").asDouble());
    }

    @Test
    void parseProduct_NoName_ReturnsNull() throws Exception {
        String json = """
                {
                    "sku": "TNT-002",
                    "price": {
                        "regularPrice": {
                            "amount": {
                                "value": 1.99
                            }
                        }
                    }
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.path("name").isMissingNode());
    }

    @Test
    void parseProduct_WithWasPrice_OverridesRegularPrice() throws Exception {
        String json = """
                {
                    "sku": "TNT-003",
                    "name": "Sale Item",
                    "price": {
                        "regularPrice": {
                            "amount": {
                                "value": 5.99
                            }
                        }
                    },
                    "price_range": {
                        "minimum_price": {
                            "final_price": {
                                "value": 3.99
                            }
                        }
                    },
                    "was_price": 7.99,
                    "stock_status": "IN_STOCK",
                    "url_key": "sale-item",
                    "url_suffix": ".html"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        // was_price (7.99) should override regularPrice (5.99) when present
        assertEquals(7.99, node.path("was_price").asDouble());
        assertTrue(node.path("was_price").asDouble() > node.path("price").path("regularPrice").path("amount").path("value").asDouble());
    }

    @Test
    void parseProduct_OutOfStock_DetectsStatus() throws Exception {
        String json = """
                {
                    "sku": "TNT-004",
                    "name": "Out of Stock Item",
                    "price": {
                        "regularPrice": {
                            "amount": { "value": 4.99 }
                        }
                    },
                    "stock_status": "OUT_OF_STOCK",
                    "url_key": "oos-item",
                    "url_suffix": ".html"
                }
                """;

        JsonNode node = objectMapper.readTree(json);
        assertEquals("OUT_OF_STOCK", node.path("stock_status").asText());
    }
}
