package com.app.services.scraper;

import com.app.models.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walmart Canada scraper using JSoup HTTP client.
 * Extracts product data from HTML and embedded JSON.
 */
@Slf4j
@Component
public class WalmartScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "WALMART";
    private static final Pattern JSON_LD_PATTERN = Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NEXT_DATA_PATTERN = Pattern.compile(
            "<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper;

    public WalmartScraper(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        super(rateLimiterRegistry);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStoreCode() {
        return STORE_CODE;
    }

    @Override
    protected List<String> getCategoryUrls(Store store) {
        List<String> urls = new ArrayList<>();
        String baseUrl = store.getBaseUrl();

        // Default category URLs for Walmart Canada
        urls.add(baseUrl + "/browse/grocery/fruits-vegetables/10019-6000195580213");
        urls.add(baseUrl + "/browse/grocery/dairy-eggs/10019-6000195580337");
        urls.add(baseUrl + "/browse/grocery/meat-seafood/10019-6000195580395");
        urls.add(baseUrl + "/browse/grocery/bakery/10019-6000195580205");
        urls.add(baseUrl + "/browse/grocery/pantry/10019-6000195580281");

        Map<String, Object> config = store.getScraperConfig();
        if (config != null && config.containsKey("categoryUrls")) {
            Object configUrls = config.get("categoryUrls");
            if (configUrls instanceof List<?>) {
                urls.clear();
                for (Object url : (List<?>) configUrls) {
                    urls.add(url.toString());
                }
            }
        }

        return urls;
    }

    @Override
    public List<ScrapedProduct> scrapeProducts(Store store, String categoryUrl) {
        List<ScrapedProduct> products = new ArrayList<>();
        int page = 1;
        int maxPages = 10;

        try {
            while (page <= maxPages) {
                String pageUrl = buildPageUrl(categoryUrl, page);
                log.debug("Fetching Walmart products from: {}", pageUrl);

                Document doc = fetchDocument(pageUrl);

                // Try to extract products from embedded JSON first
                List<ScrapedProduct> pageProducts = extractFromEmbeddedJson(doc, pageUrl);

                // If no embedded JSON found, fallback to HTML parsing
                if (pageProducts.isEmpty()) {
                    pageProducts = extractFromHtml(doc, pageUrl);
                }

                if (pageProducts.isEmpty()) {
                    log.debug("No products found on page {}, stopping pagination", page);
                    break;
                }

                products.addAll(pageProducts);
                log.debug("Found {} products on page {}", pageProducts.size(), page);

                // Check if there's a next page
                if (!hasNextPage(doc)) {
                    break;
                }

                page++;
            }

        } catch (Exception e) {
            log.error("Error scraping Walmart products from URL: {}", categoryUrl, e);
        }

        return products;
    }

    private String buildPageUrl(String categoryUrl, int page) {
        if (page == 1) {
            return categoryUrl;
        }
        if (categoryUrl.contains("?")) {
            return categoryUrl + "&page=" + page;
        }
        return categoryUrl + "?page=" + page;
    }

    private List<ScrapedProduct> extractFromEmbeddedJson(Document doc, String sourceUrl) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            // Try __NEXT_DATA__ script (Next.js apps)
            String html = doc.html();
            Matcher nextDataMatcher = NEXT_DATA_PATTERN.matcher(html);
            if (nextDataMatcher.find()) {
                String jsonStr = nextDataMatcher.group(1);
                products = parseNextDataJson(jsonStr, sourceUrl);
                if (!products.isEmpty()) {
                    return products;
                }
            }

            // Try JSON-LD structured data
            Matcher jsonLdMatcher = JSON_LD_PATTERN.matcher(html);
            while (jsonLdMatcher.find()) {
                String jsonStr = jsonLdMatcher.group(1);
                List<ScrapedProduct> ldProducts = parseJsonLd(jsonStr, sourceUrl);
                products.addAll(ldProducts);
            }

        } catch (Exception e) {
            log.debug("Could not extract embedded JSON: {}", e.getMessage());
        }

        return products;
    }

    private List<ScrapedProduct> parseNextDataJson(String jsonStr, String sourceUrl) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode props = root.path("props").path("pageProps");

            // Navigate to products array (structure varies by page type)
            JsonNode itemsNode = findProductsArray(props);

            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    ScrapedProduct product = parseProductFromNextData(item, sourceUrl);
                    if (product != null) {
                        products.add(product);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing __NEXT_DATA__: {}", e.getMessage());
        }

        return products;
    }

    private JsonNode findProductsArray(JsonNode props) {
        // Try common paths for product data
        String[] possiblePaths = {
            "initialData/searchResult/itemStacks/0/items",
            "initialData/items",
            "products",
            "searchResult/items",
            "itemStacks/0/items"
        };

        for (String path : possiblePaths) {
            JsonNode node = props;
            for (String segment : path.split("/")) {
                if (node == null) break;
                if (segment.matches("\\d+")) {
                    node = node.get(Integer.parseInt(segment));
                } else {
                    node = node.get(segment);
                }
            }
            if (node != null && node.isArray() && !node.isEmpty()) {
                return node;
            }
        }

        return null;
    }

    private ScrapedProduct parseProductFromNextData(JsonNode item, String sourceUrl) {
        try {
            String productId = getTextOrNull(item, "usItemId", "id", "productId", "sku");
            String name = getTextOrNull(item, "name", "title", "productName");

            if (name == null || name.isEmpty()) {
                return null;
            }

            String brand = getTextOrNull(item, "brand", "brandName");
            String imageUrl = extractImageUrl(item);

            // Parse pricing
            BigDecimal regularPrice = null;
            BigDecimal salePrice = null;

            JsonNode priceInfo = item.get("priceInfo");
            if (priceInfo != null) {
                regularPrice = parsePriceNode(priceInfo, "wasPrice", "listPrice", "linePrice");
                salePrice = parsePriceNode(priceInfo, "currentPrice", "price", "salePrice");
            } else {
                regularPrice = parsePriceNode(item, "price", "regularPrice", "listPrice");
                salePrice = parsePriceNode(item, "salePrice", "currentPrice");
            }

            if (regularPrice == null && salePrice != null) {
                regularPrice = salePrice;
                salePrice = null;
            }

            boolean onSale = salePrice != null && regularPrice != null &&
                             salePrice.compareTo(regularPrice) < 0;

            BigDecimal unitPrice = parsePriceNode(item, "unitPrice", "pricePerUnit");
            String size = extractSize(name);
            String unit = extractUnit(name);

            boolean inStock = true;
            JsonNode availabilityNode = item.get("availabilityStatus");
            if (availabilityNode != null) {
                String status = availabilityNode.asText().toUpperCase();
                inStock = !status.contains("OUT") && !status.contains("UNAVAILABLE");
            }

            String promoDescription = getTextOrNull(item, "promoDescription", "badge", "flag");

            return new ScrapedProduct(
                    productId,
                    name,
                    brand,
                    size,
                    unit,
                    null,
                    imageUrl,
                    regularPrice,
                    onSale && salePrice != null ? salePrice : regularPrice,
                    unitPrice,
                    onSale,
                    promoDescription,
                    inStock,
                    sourceUrl
            );
        } catch (Exception e) {
            log.warn("Error parsing Walmart product from JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<ScrapedProduct> parseJsonLd(String jsonStr, String sourceUrl) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonStr);

            // Handle array of JSON-LD objects
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (isProductType(item)) {
                        ScrapedProduct product = parseJsonLdProduct(item, sourceUrl);
                        if (product != null) {
                            products.add(product);
                        }
                    }
                }
            } else if (isProductType(root)) {
                ScrapedProduct product = parseJsonLdProduct(root, sourceUrl);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing JSON-LD: {}", e.getMessage());
        }

        return products;
    }

    private boolean isProductType(JsonNode node) {
        JsonNode typeNode = node.get("@type");
        if (typeNode == null) return false;
        String type = typeNode.asText();
        return "Product".equalsIgnoreCase(type) || "ItemList".equalsIgnoreCase(type);
    }

    private ScrapedProduct parseJsonLdProduct(JsonNode item, String sourceUrl) {
        String productId = getTextOrNull(item, "sku", "productID", "identifier");
        String name = getTextOrNull(item, "name");

        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = null;
        JsonNode brandNode = item.get("brand");
        if (brandNode != null) {
            brand = brandNode.isObject() ? getTextOrNull(brandNode, "name") : brandNode.asText();
        }

        String imageUrl = null;
        JsonNode imageNode = item.get("image");
        if (imageNode != null) {
            imageUrl = imageNode.isArray() ? imageNode.get(0).asText() : imageNode.asText();
        }

        BigDecimal regularPrice = null;
        BigDecimal salePrice = null;

        JsonNode offersNode = item.get("offers");
        if (offersNode != null) {
            JsonNode offer = offersNode.isArray() ? offersNode.get(0) : offersNode;
            if (offer != null) {
                regularPrice = parsePriceNode(offer, "price", "highPrice");
                salePrice = parsePriceNode(offer, "lowPrice");
            }
        }

        if (regularPrice == null && salePrice != null) {
            regularPrice = salePrice;
            salePrice = null;
        }

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        String size = extractSize(name);
        String unit = extractUnit(name);

        return new ScrapedProduct(
                productId,
                name,
                brand,
                size,
                unit,
                null,
                imageUrl,
                regularPrice,
                onSale && salePrice != null ? salePrice : regularPrice,
                null,
                onSale,
                null,
                true,
                sourceUrl
        );
    }

    private List<ScrapedProduct> extractFromHtml(Document doc, String sourceUrl) {
        List<ScrapedProduct> products = new ArrayList<>();

        // Try multiple CSS selector patterns
        Elements productElements = doc.select(
                "[data-testid='product-card'], " +
                ".product-card, " +
                ".search-result-gridview-item, " +
                "[data-item-id], " +
                ".product-tile"
        );

        for (Element element : productElements) {
            try {
                ScrapedProduct product = parseHtmlProduct(element, sourceUrl);
                if (product != null) {
                    products.add(product);
                }
            } catch (Exception e) {
                log.warn("Error parsing Walmart HTML product element", e);
            }
        }

        return products;
    }

    private ScrapedProduct parseHtmlProduct(Element element, String sourceUrl) {
        String productId = element.attr("data-product-id");
        if (productId.isEmpty()) {
            productId = element.attr("data-item-id");
        }

        String name = selectText(element,
                "[data-testid='product-title']",
                ".product-title",
                ".product-name",
                "a[data-testid='product-title']"
        );

        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = selectText(element, "[data-testid='product-brand']", ".product-brand");
        String priceText = selectText(element, "[data-testid='price']", ".price-main", ".price");
        String salePriceText = selectText(element, "[data-testid='sale-price']", ".price-reduced");
        String unitPriceText = selectText(element, "[data-testid='unit-price']", ".unit-price");

        String imageUrl = null;
        Element img = element.selectFirst("img[data-testid='product-image'], .product-image img, img");
        if (img != null) {
            imageUrl = img.attr("src");
            if (imageUrl.isEmpty()) {
                imageUrl = img.attr("data-src");
            }
        }

        BigDecimal regularPrice = parsePrice(priceText);
        BigDecimal salePrice = parsePrice(salePriceText);
        BigDecimal unitPrice = parsePrice(unitPriceText);

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        String promoDescription = selectText(element, "[data-testid='promo-badge']", ".promo-flag");
        String size = extractSize(name);
        String unit = extractUnit(name);

        boolean inStock = !element.hasClass("out-of-stock") &&
                         element.select(".out-of-stock-badge, [data-testid='oos-badge']").isEmpty();

        return new ScrapedProduct(
                productId.isEmpty() ? null : productId,
                name,
                brand,
                size,
                unit,
                null,
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

    private boolean hasNextPage(Document doc) {
        Elements nextButtons = doc.select(
                "[data-testid='pagination-next']:not([disabled]), " +
                ".paginator-btn-next:not(.disabled), " +
                "a[rel='next']"
        );
        return !nextButtons.isEmpty();
    }

    private String selectText(Element parent, String... selectors) {
        for (String selector : selectors) {
            Element el = parent.selectFirst(selector);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String getTextOrNull(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode != null && !fieldNode.isNull() && !fieldNode.asText().isEmpty()) {
                return fieldNode.asText();
            }
        }
        return null;
    }

    private BigDecimal parsePriceNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode != null && !fieldNode.isNull()) {
                if (fieldNode.isNumber()) {
                    return BigDecimal.valueOf(fieldNode.asDouble());
                } else if (fieldNode.isObject()) {
                    JsonNode priceValue = fieldNode.get("price");
                    if (priceValue == null) priceValue = fieldNode.get("amount");
                    if (priceValue != null) {
                        return BigDecimal.valueOf(priceValue.asDouble());
                    }
                } else {
                    return parsePrice(fieldNode.asText());
                }
            }
        }
        return null;
    }

    private String extractImageUrl(JsonNode item) {
        JsonNode imageNode = item.get("image");
        if (imageNode == null) imageNode = item.get("imageUrl");
        if (imageNode == null) imageNode = item.get("thumbnailUrl");

        if (imageNode != null) {
            if (imageNode.isArray() && !imageNode.isEmpty()) {
                return imageNode.get(0).asText();
            } else if (imageNode.isObject()) {
                JsonNode srcNode = imageNode.get("src");
                if (srcNode == null) srcNode = imageNode.get("url");
                if (srcNode != null) return srcNode.asText();
            } else {
                return imageNode.asText();
            }
        }

        return null;
    }
}
