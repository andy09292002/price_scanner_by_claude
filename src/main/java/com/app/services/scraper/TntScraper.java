package com.app.services.scraper;

import com.app.models.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T&T Supermarket scraper using GraphQL API.
 */
@Slf4j
@Component
public class TntScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "TNT";
    private static final String GRAPHQL_URL = "https://www.tntsupermarket.com/graphql";
    private static final int PAGE_SIZE = 35;

    // Category ID to Name mapping
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "2876", "Bakery",
            "2877", "Fruits",
            "2878", "Vegetables",
            "2879", "Meat",
            "2880", "Seafood",
            "2881", "Dairy & Eggs"
    );

    private final ObjectMapper objectMapper;

    // GraphQL query for fetching products
    private static final String PRODUCTS_QUERY = """
        query GetCategories($id:Int!$pageSize:Int!$currentPage:Int!$filters:ProductAttributeFilterInput!$sort:ProductAttributeSortInput){
          category(id:$id){
            id
            name
            __typename
          }
          products(pageSize:$pageSize currentPage:$currentPage filter:$filters sort:$sort){
            items{
              id
              uid
              sku
              name
              type_id
              price{
                regularPrice{
                  amount{
                    currency
                    value
                    __typename
                  }
                  __typename
                }
                __typename
              }
              price_range{
                minimum_price{
                  final_price{
                    currency
                    value
                    __typename
                  }
                  __typename
                }
                __typename
              }
              was_price
              uom_type
              weight_uom
              small_image{
                url
                __typename
              }
              stock_status
              url_key
              url_suffix
              __typename
            }
            page_info{
              total_pages
              current_page
              __typename
            }
            total_count
            __typename
          }
        }
        """;

    public TntScraper(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        super(rateLimiterRegistry);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStoreCode() {
        return STORE_CODE;
    }

    @Override
    protected List<String> getCategoryUrls(Store store) {
        // Return category IDs instead of URLs for GraphQL API
        List<String> categoryIds = new ArrayList<>();

        // Default category IDs for T&T Supermarket
        // These IDs are used in the GraphQL query
        categoryIds.add("2876");  // Bakery
        categoryIds.add("2877");  // Fruits
        categoryIds.add("2878");  // Vegetables
        categoryIds.add("2879");  // Meat
        categoryIds.add("2880");  // Seafood
        categoryIds.add("2881");  // Dairy & Eggs

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
        int currentPage = 1;
        int totalPages = 1;

        try {
            do {
                log.debug("Fetching T&T products for category {} page {}", categoryId, currentPage);

                rateLimiter.acquirePermission();

                String requestBody = buildGraphQLRequest(categoryId, currentPage);

                String response = Jsoup.connect(GRAPHQL_URL)
                        .method(Connection.Method.POST)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .userAgent(userAgent)
                        .timeout(timeoutSeconds * 1000)
                        .requestBody(requestBody)
                        .ignoreContentType(true)
                        .execute()
                        .body();

                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.get("data");

                if (data == null) {
                    log.warn("No data in GraphQL response for category {}", categoryId);
                    break;
                }

                JsonNode productsNode = data.get("products");
                if (productsNode == null) {
                    log.warn("No products in GraphQL response for category {}", categoryId);
                    break;
                }

                // Get pagination info
                JsonNode pageInfo = productsNode.get("page_info");
                if (pageInfo != null) {
                    totalPages = pageInfo.get("total_pages").asInt(1);
                }

                // Parse products
                JsonNode items = productsNode.get("items");
                if (items != null && items.isArray()) {
                    for (JsonNode item : items) {
                        try {
                            ScrapedProduct product = parseProduct(item, categoryId);
                            if (product != null) {
                                products.add(product);
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing T&T product: {}", e.getMessage());
                        }
                    }
                }

                log.info("Fetched {} products from category {} page {}/{}",
                        items != null ? items.size() : 0, categoryId, currentPage, totalPages);

                currentPage++;

            } while (currentPage <= totalPages);

        } catch (Exception e) {
            log.error("Error scraping T&T products for category {}: {}", categoryId, e.getMessage(), e);
        }

        return products;
    }

    private String buildGraphQLRequest(String categoryId, int page) throws Exception {
        Map<String, Object> variables = Map.of(
                "id", Integer.parseInt(categoryId),
                "pageSize", PAGE_SIZE,
                "currentPage", page,
                "filters", Map.of("category_id", Map.of("eq", categoryId)),
                "sort", Map.of("position", "DESC")
        );

        Map<String, Object> request = Map.of(
                "operationName", "GetCategories",
                "query", PRODUCTS_QUERY,
                "variables", variables
        );

        return objectMapper.writeValueAsString(request);
    }

    private ScrapedProduct parseProduct(JsonNode item, String categoryId) {
        String productId = getTextValue(item, "sku");
        if (productId == null) {
            productId = getTextValue(item, "id");
        }

        String name = getTextValue(item, "name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Get image URL
        String imageUrl = null;
        JsonNode smallImage = item.get("small_image");
        if (smallImage != null) {
            imageUrl = getTextValue(smallImage, "url");
        }

        // Get regular price
        BigDecimal regularPrice = null;
        JsonNode priceNode = item.get("price");
        if (priceNode != null) {
            JsonNode regularPriceNode = priceNode.get("regularPrice");
            if (regularPriceNode != null) {
                JsonNode amountNode = regularPriceNode.get("amount");
                if (amountNode != null) {
                    regularPrice = getBigDecimalValue(amountNode, "value");
                }
            }
        }

        // Get sale price from price_range
        BigDecimal salePrice = null;
        JsonNode priceRange = item.get("price_range");
        if (priceRange != null) {
            JsonNode minPrice = priceRange.get("minimum_price");
            if (minPrice != null) {
                JsonNode finalPrice = minPrice.get("final_price");
                if (finalPrice != null) {
                    salePrice = getBigDecimalValue(finalPrice, "value");
                }
            }
        }

        // Check was_price for original price
        BigDecimal wasPrice = getBigDecimalValue(item, "was_price");
        if (wasPrice != null && wasPrice.compareTo(BigDecimal.ZERO) > 0) {
            regularPrice = wasPrice;
        }

        // If no regular price, use sale price as regular
        if (regularPrice == null && salePrice != null) {
            regularPrice = salePrice;
            salePrice = null;
        }

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        // Get stock status
        String stockStatus = getTextValue(item, "stock_status");
        boolean inStock = !"OUT_OF_STOCK".equals(stockStatus);

        // Get size and unit info
        String size = extractSize(name);
        String unit = getTextValue(item, "weight_uom");
        // If no unit from API, extract from product name
        if (unit == null || unit.isEmpty()) {
            unit = extractUnit(name);
        }

        // Build product URL
        String urlKey = getTextValue(item, "url_key");
        String urlSuffix = getTextValue(item, "url_suffix");
        String sourceUrl = "https://www.tntsupermarket.com/" + urlKey + (urlSuffix != null ? urlSuffix : "");

        // Get category name from ID, pass as "code:name" format
        String categoryName = CATEGORY_NAMES.getOrDefault(categoryId, categoryId);
        String categoryValue = categoryId + ":" + categoryName;

        return new ScrapedProduct(
                productId,
                name,
                null,  // brand not available in response
                size,
                unit,
                categoryValue,
                imageUrl,
                regularPrice,
                onSale && salePrice != null ? salePrice : regularPrice,
                null,
                onSale,
                null,
                inStock,
                sourceUrl
        );
    }

    private String getTextValue(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        return field.asText();
    }

    private BigDecimal getBigDecimalValue(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (field.isNumber()) {
            return BigDecimal.valueOf(field.asDouble());
        }
        try {
            return new BigDecimal(field.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
