package com.app.services.scraper;

import com.app.models.Store;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AbstractStoreScraperTest {

    private TestStoreScraper scraper;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        scraper = new TestStoreScraper(registry);
    }

    @Test
    void supports_MatchingStoreCode_ReturnsTrue() {
        Store store = Store.builder().code("TEST_SCRAPER").build();
        assertTrue(scraper.supports(store));
    }

    @Test
    void supports_DifferentStoreCode_ReturnsFalse() {
        Store store = Store.builder().code("OTHER").build();
        assertFalse(scraper.supports(store));
    }

    @Test
    void parsePrice_ValidDollarAmount_ReturnsBigDecimal() {
        BigDecimal result = scraper.testParsePrice("$9.99");
        assertEquals(new BigDecimal("9.99"), result);
    }

    @Test
    void parsePrice_WithCommas_ReturnsBigDecimal() {
        BigDecimal result = scraper.testParsePrice("$1,299.99");
        assertEquals(new BigDecimal("1299.99"), result);
    }

    @Test
    void parsePrice_NullInput_ReturnsNull() {
        assertNull(scraper.testParsePrice(null));
    }

    @Test
    void parsePrice_BlankInput_ReturnsNull() {
        assertNull(scraper.testParsePrice("   "));
    }

    @Test
    void parsePrice_InvalidFormat_ReturnsNull() {
        assertNull(scraper.testParsePrice("N/A"));
    }

    @Test
    void parsePrice_PlainNumber_ReturnsBigDecimal() {
        BigDecimal result = scraper.testParsePrice("12.50");
        assertEquals(new BigDecimal("12.50"), result);
    }

    @Test
    void normalizeProductName_Standard_ReturnsNormalized() {
        String result = scraper.testNormalizeProductName("Organic Bananas 1kg");
        assertEquals("organic bananas 1kg", result);
    }

    @Test
    void normalizeProductName_NullInput_ReturnsNull() {
        assertNull(scraper.testNormalizeProductName(null));
    }

    @Test
    void normalizeProductName_SpecialCharacters_RemovesThem() {
        String result = scraper.testNormalizeProductName("Product™ (Special) & More!");
        assertEquals("product special more", result);
    }

    @Test
    void normalizeProductName_ExtraSpaces_Trimmed() {
        String result = scraper.testNormalizeProductName("  Product   Name  ");
        assertEquals("product name", result);
    }

    @Test
    void extractSize_WithSize_ReturnsNumericPortion() {
        String result = scraper.testExtractSize("Organic Milk 2L");
        assertEquals("2", result);
    }

    @Test
    void extractSize_WithDecimalSize_ReturnsNumeric() {
        String result = scraper.testExtractSize("Chicken Breast 1.5kg");
        assertEquals("1.5", result);
    }

    @Test
    void extractSize_NoSize_ReturnsNull() {
        assertNull(scraper.testExtractSize("Plain Product"));
    }

    @Test
    void extractSize_NullInput_ReturnsNull() {
        assertNull(scraper.testExtractSize(null));
    }

    @Test
    void extractUnit_WithUnit_ReturnsLowercased() {
        String result = scraper.testExtractUnit("Organic Milk 2L");
        assertEquals("l", result);
    }

    @Test
    void extractUnit_KgUnit_ReturnsLowercased() {
        String result = scraper.testExtractUnit("Chicken 500g");
        assertEquals("g", result);
    }

    @Test
    void extractUnit_NullInput_ReturnsNull() {
        assertNull(scraper.testExtractUnit(null));
    }

    @Test
    void extractUnit_NoUnit_ReturnsNull() {
        assertNull(scraper.testExtractUnit("Plain Product"));
    }

    /**
     * Concrete subclass for testing the abstract class methods.
     */
    static class TestStoreScraper extends AbstractStoreScraper {

        protected TestStoreScraper(RateLimiterRegistry rateLimiterRegistry) {
            super(rateLimiterRegistry);
        }

        @Override
        public String getStoreCode() {
            return "TEST_SCRAPER";
        }

        @Override
        protected List<String> getCategoryUrls(Store store) {
            return List.of();
        }

        @Override
        public List<ScrapedProduct> scrapeProducts(Store store, String categoryUrl) {
            return List.of();
        }

        // Expose protected methods for testing
        public BigDecimal testParsePrice(String priceText) {
            return parsePrice(priceText);
        }

        public String testNormalizeProductName(String name) {
            return normalizeProductName(name);
        }

        public String testExtractSize(String text) {
            return extractSize(text);
        }

        public String testExtractUnit(String text) {
            return extractUnit(text);
        }
    }
}
