package com.app.services.scraper;

import com.app.models.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real Canadian Superstore scraper using PC Express API.
 */
@Slf4j
@Component
public class SuperstoreScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "RCSS";
    private static final String API_BASE_URL = "https://api.pcexpress.ca/pcx-bff/api/v2/listingPage/";
    private static final int PAGE_SIZE = 48;

    // Category ID to name mapping
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "28000", "Fruits & Vegetables",
            "28003", "Dairy & Eggs",
            "28006", "Meat & Seafood",
            "28002", "Bakery",
            "28012", "Pantry"
    );

    private final ObjectMapper objectMapper;

    @Value("${scraper.superstore.api-key}")
    private String apiKey;

    public SuperstoreScraper(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        super(rateLimiterRegistry);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStoreCode() {
        return STORE_CODE;
    }

    @Override
    protected List<String> getCategoryUrls(Store store) {
        // Return category IDs for API calls
        List<String> categoryIds = new ArrayList<>();

        // Default category IDs for Real Canadian Superstore
        categoryIds.add("28000");  // Fruits & Vegetables
        categoryIds.add("28003");  // Dairy & Eggs
        categoryIds.add("28006");  // Meat & Seafood
        categoryIds.add("28002");  // Bakery
        categoryIds.add("28012");  // Pantry

        // Override with custom IDs from config if available
        Map<String, Object> config = store.getScraperConfig();
        if (config != null && config.containsKey("categoryIds")) {
            Object configIds = config.get("categoryIds");
            if (configIds instanceof List<?>) {
                categoryIds.clear();
                for (Object id : (List<?>) configIds) {
                    categoryIds.add(id.toString());
                }
            }
        }

        return categoryIds;
    }

    @Override
    public List<ScrapedProduct> scrapeProducts(Store store, String categoryId) {
        List<ScrapedProduct> products = new ArrayList<>();
        int page = 0;
        int totalPages = 1;

        try {
            do {
                String apiUrl = buildApiUrl(categoryId);
                String requestBody = buildRequestBody(page);
                log.debug("Fetching RCSS products from: {}", apiUrl);

                rateLimiter.acquirePermission();

                String response = Jsoup.connect(apiUrl)
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/json")
                        .header("Accept-Language", "en")
                        .header("x-apikey", apiKey)
                        .header("x-application-type", "web")
                        .header("x-loblaw-tenant-id", "ONLINE_GROCERIES")
                        .requestBody(requestBody)
                        .userAgent(userAgent)
                        .timeout(timeoutSeconds * 1000)
                        .ignoreContentType(true)
                        .execute()
                        .body();

                JsonNode root = objectMapper.readTree(response);

                // Get pagination info
                JsonNode pagination = root.get("pagination");
                if (pagination != null) {
                    totalPages = pagination.path("totalPages").asInt(1);
                }

                // Parse products from layout > sections > mainContentCollection > components
                JsonNode components = root.path("layout")
                        .path("sections")
                        .path("mainContentCollection")
                        .path("components");

                int productCount = 0;
                if (components.isArray()) {
                    for (JsonNode component : components) {
                        String componentId = component.path("componentId").asText("");
                        if (!"productCarouselComponent".equals(componentId)) {
                            continue;
                        }

                        // Get productTiles from the component (check both root and data)
                        JsonNode productTiles = component.path("productTiles");
                        if (productTiles.isMissingNode() || !productTiles.isArray()) {
                            productTiles = component.path("data").path("productTiles");
                        }

                        if (productTiles.isArray()) {
                            for (JsonNode item : productTiles) {
                                try {
                                    ScrapedProduct product = parseProduct(item, categoryId);
                                    if (product != null) {
                                        products.add(product);
                                        productCount++;
                                    }
                                } catch (Exception e) {
                                    log.warn("Error parsing RCSS product: {}", e.getMessage());
                                }
                            }
                        }
                    }
                }

                log.info("Fetched {} products from category {} page {}/{}",
                        productCount, categoryId, page + 1, totalPages);

                page++;

            } while (page < totalPages);

        } catch (Exception e) {
            log.error("Error scraping RCSS products for category {}: {}", categoryId, e.getMessage(), e);
        }

        return products;
    }

    private String buildApiUrl(String categoryId) {
        return API_BASE_URL + categoryId;
    }

    private String buildRequestBody(int page) {
        // Get current date in DDMMYYYY format
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateStr = String.format("%02d%02d%04d",
                today.getDayOfMonth(), today.getMonthValue(), today.getYear());

        String cartId = java.util.UUID.randomUUID().toString();
        String domainUserId = java.util.UUID.randomUUID().toString();
        String sessionId = java.util.UUID.randomUUID().toString();
        int from = page == 0 ? 1 : page * PAGE_SIZE;

        // Build JSON string matching exact Postman format
        return String.format("""
            {
                "cart": {"cartId": "%s"},
                "userData": {"domainUserId": "%s", "sessionId": "%s"},
                "fulfillmentInfo": {
                    "offerType": "OG",
                    "storeId": "1518",
                    "pickupType": "STORE",
                    "date": "%s",
                    "timeSlot": null
                },
                "banner": "superstore",
                "listingInfo": {
                    "filters": {},
                    "sort": {},
                    "pagination": {"from": %d},
                    "includeFiltersInResponse": true
                }
            }
            """, cartId, domainUserId, sessionId, dateStr, from);
    }

    private ScrapedProduct parseProduct(JsonNode item, String categoryId) {
        String productId = getTextValue(item, "productId");
        String name = getTextValue(item, "title");

        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = getTextValue(item, "brand");

        // Get image URL from productImage array
        String imageUrl = null;
        JsonNode imageNode = item.path("productImage");
        if (imageNode.isArray() && imageNode.size() > 0) {
            imageUrl = imageNode.get(0).path("smallUrl").asText(null);
            if (imageUrl == null) {
                imageUrl = imageNode.get(0).path("mediumUrl").asText(null);
            }
        }

        // Get pricing from pricing object
        BigDecimal regularPrice = null;
        BigDecimal salePrice = null;

        JsonNode pricingNode = item.path("pricing");
        if (pricingNode.isObject()) {
            regularPrice = getBigDecimalFromString(pricingNode.path("price").asText(null));
            BigDecimal wasPrice = getBigDecimalFromString(pricingNode.path("wasPrice").asText(null));
            if (wasPrice != null && wasPrice.compareTo(BigDecimal.ZERO) > 0) {
                salePrice = regularPrice;
                regularPrice = wasPrice;
            }
        }

        // Check for deal/sale
        boolean onSale = false;
        JsonNode dealNode = item.path("deal");
        if (!dealNode.isMissingNode() && !dealNode.isNull()) {
            onSale = true;
        }

        if (!onSale && salePrice != null && regularPrice != null) {
            onSale = salePrice.compareTo(regularPrice) < 0;
        }

        // Get stock status from inventoryIndicator
        boolean inStock = true;
        JsonNode inventoryNode = item.path("inventoryIndicator");
        if (inventoryNode.isObject()) {
            String indicatorId = inventoryNode.path("indicatorId").asText("");
            inStock = !"OUT".equalsIgnoreCase(indicatorId);
        }
        // Also check textBadge
        String textBadge = getTextValue(item, "textBadge");
        if (textBadge != null && textBadge.toLowerCase().contains("out-of-stock")) {
            inStock = false;
        }

        // Get size/unit from packageSizing or name first (prefer weight units over "ea")
        String packageSizing = getTextValue(item, "packageSizing");
        String size = packageSizing != null ? extractSize(packageSizing) : extractSize(name);

        // Try to extract unit from packageSizing or name first
        String unit = packageSizing != null ? extractUnit(packageSizing) : extractUnit(name);

        // Only fall back to pricingUnits.unit if we couldn't extract a weight/volume unit
        if (unit == null || unit.isEmpty()) {
            JsonNode pricingUnits = item.path("pricingUnits");
            if (pricingUnits.isObject()) {
                unit = pricingUnits.path("unit").asText(null);
            }
        }

        // Extract unit price from packageSizing (e.g., "$11.00/1kg")
        BigDecimal unitPrice = extractUnitPrice(packageSizing);

        // Get promo description from deal
        String promoDescription = null;
        if (onSale && !dealNode.isMissingNode() && !dealNode.isNull()) {
            promoDescription = dealNode.path("text").asText(null);
            if (promoDescription == null) {
                promoDescription = dealNode.asText(null);
            }
        }

        // Build category value with code:name format
        String categoryName = CATEGORY_NAMES.getOrDefault(categoryId, categoryId);
        String categoryValue = categoryId + ":" + categoryName;

        // Source URL
        String link = getTextValue(item, "link");
        String sourceUrl = link != null ? "https://www.realcanadiansuperstore.ca" + link : null;

        return new ScrapedProduct(
                productId,
                name,
                brand,
                size,
                unit,
                categoryValue,
                imageUrl,
                regularPrice,
                onSale && salePrice != null ? salePrice : regularPrice,
                unitPrice,
                onSale,
                promoDescription,
                inStock,
                sourceUrl
        );
    }

    private BigDecimal getBigDecimalFromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            // Remove currency symbols and whitespace
            String cleaned = value.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal extractUnitPrice(String packageSizing) {
        if (packageSizing == null || packageSizing.isEmpty()) {
            return null;
        }
        try {
            // Extract price from format like "$11.00/1kg"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$([0-9.]+)/");
            java.util.regex.Matcher matcher = pattern.matcher(packageSizing);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String getTextValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node == null) continue;
            JsonNode fieldNode = node.path(fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                String value = fieldNode.asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node == null) continue;
            JsonNode fieldNode = node.path(fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                if (fieldNode.isNumber()) {
                    return BigDecimal.valueOf(fieldNode.asDouble());
                } else if (fieldNode.isObject()) {
                    JsonNode valueNode = fieldNode.path("value");
                    if (!valueNode.isMissingNode() && valueNode.isNumber()) {
                        return BigDecimal.valueOf(valueNode.asDouble());
                    }
                } else {
                    try {
                        return new BigDecimal(fieldNode.asText());
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        return null;
    }
}
