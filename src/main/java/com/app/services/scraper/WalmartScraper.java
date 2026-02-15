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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final Pattern CATEGORY_ID_PATTERN = Pattern.compile("_(\\d{13})(?:\\?|$)");
    private static final Pattern WAS_PRICE_PATTERN = Pattern.compile(
            "Was\\s*\\$?([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NOW_PRICE_PATTERN = Pattern.compile(
            "Now\\s*\\$?([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CENTS_PRICE_PATTERN = Pattern.compile(
            "(\\d+)\\s*[\u00A2¢]"
    );

    // Category ID to name mapping
    private static final Map<String, String> CATEGORY_NAMES;
    static {
        Map<String, String> map = new HashMap<>();
        // Fruits & Vegetables
        map.put("6000194327411", "Fresh Fruits");
        map.put("6000194327412", "Fresh Vegetables");
        map.put("6000205319590", "Fresh Salads, Dressings & Toppings");
        map.put("6000205319266", "Pre-cut Fruits & Vegetables");
        map.put("6000205760085", "Fresh Juice & Kombucha");
        // Meat & Seafood
        map.put("6000195505355", "Plant-based Proteins & Tofu");
        map.put("6000194327409", "Fresh Chicken & Turkey");
        map.put("6000194327394", "Fresh Beef");
        map.put("6000194327395", "Fresh Pork");
        map.put("6000194327410", "Fresh Fish & Seafood");
        map.put("6000204985034", "Fresh Sausages");
        map.put("6000200207254", "Hot Dogs");
        // Dairy & Eggs
        map.put("6000194327399", "Milk");
        map.put("6000194327377", "Cheese");
        map.put("6000194327390", "Yogurt");
        map.put("6000194349396", "Cream & Creamers");
        map.put("6000194327387", "Butter & Margarine");
        map.put("6000194327389", "Eggs & Egg Substitutes");
        // Frozen
        map.put("6000194349404", "Frozen Pizza");
        map.put("6000194327413", "Frozen Meals & Sides");
        map.put("6000194327396", "Frozen Meat, Seafood & Alternatives");
        map.put("6000194349402", "Ice Cream & Treats");
        map.put("6000202265715", "Frozen Appetizers & Snacks");
        map.put("6000194327403", "Frozen Vegetables");
        // Pantry
        map.put("6000194329528", "Dry Pasta");
        map.put("6000194329559", "Rice & Grains");
        map.put("6000194328506", "Cereal & Breakfast");
        map.put("6000202444928", "Easy Meals & Sides");
        map.put("6000194328515", "Canned Food");
        map.put("6000194328507", "Condiments & Toppings");
        map.put("6000209233642", "Spices & Seasonings");
        map.put("6000194328505", "Baking Ingredients & Supplies");
        // Snacks
        map.put("6000194329540", "Chips");
        map.put("6000194329539", "Chocolate");
        map.put("6000194329541", "Cookies");
        map.put("6000195492961", "Granola Bars & Snack Bars");
        map.put("6000194329534", "Candy");
        map.put("6000194329538", "Crackers");
        // Deli
        map.put("6000194327391", "Deli Meat");
        map.put("6000202168102", "Fresh Meals & Sides");
        map.put("6000194327392", "Deli Cheese");
        map.put("6000194397071", "Hummus, Dips & Spreads");
        map.put("6000199407014", "Party Trays & Platters");
        map.put("6000199407012", "Lunch Kits & Snacks");
        // Drinks
        map.put("6000194327385", "Soft Drinks");
        map.put("6000194327382", "Juice");
        map.put("6000194327380", "Coffee");
        map.put("6000194327384", "Water");
        map.put("6000194327375", "Tea");
        // Bakery
        map.put("6000198840549", "Cakes & Cupcakes");
        map.put("6000194327386", "Sliced Bread");
        map.put("6000198840557", "Bakery Snacks & Treats");
        map.put("1058479704141", "Breakfast Bakery");
        map.put("6000194396755", "Tortillas & Flatbreads");
        // International
        map.put("6000195496070", "Indian & South Asian Food");
        map.put("6000195496072", "Chinese & East Asian Food");
        map.put("6000195496074", "Mexican & Latin American Food");
        map.put("6000195496077", "Italian & European Food");
        map.put("6000195496075", "Caribbean & African Food");
        map.put("6000208694125", "Thai & Filipino Food");
        CATEGORY_NAMES = java.util.Collections.unmodifiableMap(map);
    }

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
        // Fruits & Vegetables
        urls.add(baseUrl + "/en/browse/grocery/fruits-vegetables/fresh-fruits/10019_6000194327370_6000194327411");
        urls.add(baseUrl + "/en/browse/grocery/fruits-vegetables/fresh-vegetables/10019_6000194327370_6000194327412");
        // Meat & Seafood
        urls.add(baseUrl + "/en/browse/grocery/meat-seafood-alternatives/fresh-chicken-turkey/10019_6000194327357_6000194327409");
        urls.add(baseUrl + "/en/browse/grocery/meat-seafood-alternatives/fresh-beef/10019_6000194327357_6000194327394");
        urls.add(baseUrl + "/en/browse/grocery/meat-seafood-alternatives/fresh-pork/10019_6000194327357_6000194327395");
        urls.add(baseUrl + "/en/browse/grocery/meat-seafood-alternatives/fresh-fish-seafood/10019_6000194327357_6000194327410");
        // Dairy & Eggs
        urls.add(baseUrl + "/en/browse/grocery/dairy-eggs/dairy-milk/10019_6000194327369_6000194327399");
        urls.add(baseUrl + "/en/browse/grocery/dairy-eggs/yogurt/10019_6000194327369_6000194327390");
        urls.add(baseUrl + "/en/browse/grocery/dairy-eggs/butter-margarine/10019_6000194327369_6000194327387");
        urls.add(baseUrl + "/en/browse/grocery/dairy-eggs/eggs-egg-substitutes/10019_6000194327369_6000194327389");
        // Frozen
        urls.add(baseUrl + "/en/browse/grocery/frozen-food/frozen-meals-sides/10019_6000194326337_6000194327413");
        urls.add(baseUrl + "/en/browse/grocery/frozen-food/frozen-vegetables/10019_6000194326337_6000194327403");
        // Pantry
        urls.add(baseUrl + "/en/browse/grocery/pantry-food/cereal-breakfast/10019_6000194326346_6000194328506");
        urls.add(baseUrl + "/en/browse/grocery/pantry-food/canned-food/10019_6000194326346_6000194328515");
        // Bakery
        urls.add(baseUrl + "/en/browse/grocery/bread-bakery/sliced-bread/10019_6000194327359_6000194327386");

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

    private String extractCategoryFromUrl(String url) {
        Matcher matcher = CATEGORY_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            String categoryId = matcher.group(1);
            String categoryName = CATEGORY_NAMES.getOrDefault(categoryId, categoryId);
            return categoryId + ":" + categoryName;
        }
        return null;
    }

    private static final int WALMART_PAGE_SIZE = 40;

    @Override
    public List<ScrapedProduct> scrapeProducts(Store store, String categoryUrl) {
        List<ScrapedProduct> products = new ArrayList<>();
        int page = 1;
        int maxPages = 10;
        String category = extractCategoryFromUrl(categoryUrl);

        try {
            while (page <= maxPages) {
                String pageUrl = buildPageUrl(categoryUrl, page);
                log.debug("Fetching Walmart products from: {}", pageUrl);

                Document doc = fetchDocument(pageUrl);

                // Try to extract products from embedded JSON first
                boolean fromJson = false;
                List<ScrapedProduct> pageProducts = extractFromEmbeddedJson(doc, pageUrl, category);
                if (!pageProducts.isEmpty()) {
                    fromJson = true;
                }

                // If no embedded JSON found, fallback to HTML parsing
                if (pageProducts.isEmpty()) {
                    pageProducts = extractFromHtml(doc, pageUrl, category);
                }

                if (pageProducts.isEmpty()) {
                    log.debug("No products found on page {}, stopping pagination", page);
                    break;
                }

                products.addAll(pageProducts);
                log.debug("Found {} products on page {}", pageProducts.size(), page);

                // Determine if there's a next page
                if (fromJson) {
                    // JSON path: continue if we got a full page of products
                    if (pageProducts.size() < WALMART_PAGE_SIZE) {
                        break;
                    }
                } else {
                    // HTML path: check for pagination elements
                    if (!hasNextPage(doc)) {
                        break;
                    }
                }

                page++;

                // Random delay between pages to avoid being blocked
                long delayMs = ThreadLocalRandom.current().nextLong(3000, 6000);
                log.debug("Waiting {}ms before fetching next page", delayMs);
                Thread.sleep(delayMs);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Walmart scraping interrupted for URL: {}", categoryUrl);
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

    private List<ScrapedProduct> extractFromEmbeddedJson(Document doc, String sourceUrl, String category) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            // Try __NEXT_DATA__ script (Next.js apps)
            String html = doc.html();
            Matcher nextDataMatcher = NEXT_DATA_PATTERN.matcher(html);
            if (nextDataMatcher.find()) {
                String jsonStr = nextDataMatcher.group(1);
                products = parseNextDataJson(jsonStr, sourceUrl, category);
                if (!products.isEmpty()) {
                    return products;
                }
            }

            // Try JSON-LD structured data
            Matcher jsonLdMatcher = JSON_LD_PATTERN.matcher(html);
            while (jsonLdMatcher.find()) {
                String jsonStr = jsonLdMatcher.group(1);
                List<ScrapedProduct> ldProducts = parseJsonLd(jsonStr, sourceUrl, category);
                products.addAll(ldProducts);
            }

        } catch (Exception e) {
            log.debug("Could not extract embedded JSON: {}", e.getMessage());
        }

        return products;
    }

    private List<ScrapedProduct> parseNextDataJson(String jsonStr, String sourceUrl, String category) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode props = root.path("props").path("pageProps");

            // Navigate to products array (structure varies by page type)
            JsonNode itemsNode = findProductsArray(props);

            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    ScrapedProduct product = parseProductFromNextData(item, sourceUrl, category);
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

    private ScrapedProduct parseProductFromNextData(JsonNode item, String sourceUrl, String category) {
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
            BigDecimal unitPrice = null;

            JsonNode priceInfo = item.get("priceInfo");
            if (priceInfo != null) {
                // Walmart Canada uses string display prices:
                //   linePrice: "40¢" or "$5.44" (current price)
                //   wasPrice: "" or "$6.77" (original price, empty when not on sale)
                //   unitPrice: "17¢/100g"
                String linePriceStr = getTextOrNull(priceInfo, "linePrice", "linePriceDisplay");
                String wasPriceStr = getTextOrNull(priceInfo, "wasPrice");
                String unitPriceStr = getTextOrNull(priceInfo, "unitPrice");

                BigDecimal linePrice = parseWalmartPrice(linePriceStr);
                BigDecimal wasPrice = parseWalmartPrice(wasPriceStr);
                unitPrice = parseWalmartPrice(unitPriceStr);

                if (wasPrice != null && linePrice != null && wasPrice.compareTo(linePrice) > 0) {
                    // On sale: wasPrice is original, linePrice is discounted
                    regularPrice = wasPrice;
                    salePrice = linePrice;
                } else if (linePrice != null) {
                    regularPrice = linePrice;
                }
            }

            // Fallback: top-level numeric "price" field (e.g. "price": 0.4)
            if (regularPrice == null) {
                JsonNode priceNode = item.get("price");
                if (priceNode != null && priceNode.isNumber() && priceNode.asDouble() > 0) {
                    regularPrice = BigDecimal.valueOf(priceNode.asDouble());
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

            boolean inStock = true;
            // Check isOutOfStock boolean flag
            JsonNode oosNode = item.get("isOutOfStock");
            if (oosNode != null && oosNode.asBoolean()) {
                inStock = false;
            }
            // Check availabilityStatusV2.value (e.g. "IN_STOCK", "OUT_OF_STOCK")
            JsonNode availabilityV2 = item.get("availabilityStatusV2");
            if (availabilityV2 != null) {
                String status = getTextOrNull(availabilityV2, "value", "display");
                if (status != null) {
                    String upper = status.toUpperCase();
                    inStock = !upper.contains("OUT") && !upper.contains("UNAVAILABLE");
                }
            } else {
                JsonNode availabilityNode = item.get("availabilityStatus");
                if (availabilityNode != null) {
                    String status = availabilityNode.asText().toUpperCase();
                    inStock = !status.contains("OUT") && !status.contains("UNAVAILABLE");
                }
            }

            String promoDescription = getTextOrNull(item, "promoDescription", "badge", "flag");

            return new ScrapedProduct(
                    productId,
                    name,
                    brand,
                    size,
                    unit,
                    category,
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

    private List<ScrapedProduct> parseJsonLd(String jsonStr, String sourceUrl, String category) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonStr);

            // Handle array of JSON-LD objects
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (isProductType(item)) {
                        ScrapedProduct product = parseJsonLdProduct(item, sourceUrl, category);
                        if (product != null) {
                            products.add(product);
                        }
                    }
                }
            } else if (isProductType(root)) {
                ScrapedProduct product = parseJsonLdProduct(root, sourceUrl, category);
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

    private ScrapedProduct parseJsonLdProduct(JsonNode item, String sourceUrl, String category) {
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
                category,
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

    private List<ScrapedProduct> extractFromHtml(Document doc, String sourceUrl, String category) {
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
                ScrapedProduct product = parseHtmlProduct(element, sourceUrl, category);
                if (product != null) {
                    products.add(product);
                }
            } catch (Exception e) {
                log.warn("Error parsing Walmart HTML product element", e);
            }
        }

        return products;
    }

    private ScrapedProduct parseHtmlProduct(Element element, String sourceUrl, String category) {
        String productId = element.attr("data-item-id");
        if (productId.isEmpty()) {
            productId = element.attr("data-product-id");
        }

        String name = selectText(element,
                "[data-automation-id='product-title']",
                "[data-testid='product-title']",
                ".product-title",
                ".product-name"
        );

        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = selectText(element,
                "[data-automation-id='product-brand']",
                "[data-testid='product-brand']",
                ".product-brand");

        // Extract prices from the product-price container
        BigDecimal regularPrice = null;
        BigDecimal salePrice = null;
        BigDecimal unitPrice = null;

        Element priceContainer = element.selectFirst("[data-automation-id='product-price']");
        if (priceContainer != null) {
            // Screen reader span contains the most complete price info
            // Format: "current price 40¢" or "current price Now $5.44, Was $6.77"
            String accessibleText = null;
            for (Element span : priceContainer.select("span.w_q67L")) {
                String text = span.text().trim();
                if (text.toLowerCase().contains("current price")) {
                    accessibleText = text;
                    break;
                }
            }

            if (accessibleText != null) {
                Matcher wasMatcher = WAS_PRICE_PATTERN.matcher(accessibleText);
                Matcher nowMatcher = NOW_PRICE_PATTERN.matcher(accessibleText);

                if (wasMatcher.find() && nowMatcher.find()) {
                    // Sale item: "current price Now $5.44, Was $6.77"
                    salePrice = parseWalmartPrice(nowMatcher.group(1));
                    regularPrice = parseWalmartPrice(wasMatcher.group(1));
                } else {
                    // Regular item: "current price 40¢" or "current price $5.44"
                    String priceStr = accessibleText
                            .replaceFirst("(?i)current price\\s*", "").trim();
                    regularPrice = parseWalmartPrice(priceStr);
                }
            }

            // Fallback: get from visible price div (bold black text)
            if (regularPrice == null) {
                Element visiblePrice = priceContainer.selectFirst(".b.black");
                if (visiblePrice != null) {
                    regularPrice = parseWalmartPrice(visiblePrice.ownText().trim());
                }
            }

            // Unit price
            Element unitPriceEl = priceContainer.selectFirst(
                    "[data-testid='product-price-per-unit']");
            if (unitPriceEl != null) {
                unitPrice = parsePrice(unitPriceEl.text().trim());
            }
        }

        // Fallback to old selectors if no price found
        if (regularPrice == null) {
            String priceText = selectText(element,
                    "[data-testid='price']", ".price-main", ".price");
            regularPrice = parseWalmartPrice(priceText);
        }
        if (unitPrice == null) {
            String unitPriceText = selectText(element,
                    "[data-testid='unit-price']", ".unit-price");
            unitPrice = parsePrice(unitPriceText);
        }

        // Image
        String imageUrl = null;
        Element img = element.selectFirst(
                "[data-testid='productTileImage'], " +
                "img[data-testid='product-image'], " +
                ".product-image img, img");
        if (img != null) {
            imageUrl = img.attr("src");
            if (imageUrl.isEmpty()) {
                imageUrl = img.attr("data-src");
            }
        }

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        String promoDescription = selectText(element,
                "[data-testid='tag-leading-badge']",
                "[data-testid='promo-badge']",
                ".promo-flag");
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
                category,
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

    private BigDecimal parseWalmartPrice(String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return null;
        }
        // Handle cents format: "40¢", "65¢"
        Matcher centsMatcher = CENTS_PRICE_PATTERN.matcher(priceText);
        if (centsMatcher.find()) {
            try {
                int cents = Integer.parseInt(centsMatcher.group(1));
                return BigDecimal.valueOf(cents, 2);
            } catch (NumberFormatException e) {
                // fall through to dollar parsing
            }
        }
        // Handle dollar format: "$5.44", "5.44"
        return parsePrice(priceText);
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
                    if (priceValue == null) priceValue = fieldNode.get("value");
                    if (priceValue != null && priceValue.isNumber()) {
                        return BigDecimal.valueOf(priceValue.asDouble());
                    }
                    // Fallback: try parsing from priceString display text
                    JsonNode priceString = fieldNode.get("priceString");
                    if (priceString != null) {
                        BigDecimal parsed = parsePrice(priceString.asText());
                        if (parsed != null) return parsed;
                    }
                } else {
                    return parsePrice(fieldNode.asText());
                }
            }
        }
        return null;
    }

    private BigDecimal extractWasPriceFromNode(JsonNode priceInfo) {
        // Scan all string fields in priceInfo for "Was $X.XX" pattern
        var fields = priceInfo.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode value = entry.getValue();
            String text = null;
            if (value.isTextual()) {
                text = value.asText();
            } else if (value.isObject()) {
                JsonNode priceString = value.get("priceString");
                if (priceString != null) {
                    text = priceString.asText();
                }
            }
            if (text != null) {
                Matcher wasMatcher = WAS_PRICE_PATTERN.matcher(text);
                if (wasMatcher.find()) {
                    try {
                        return new BigDecimal(wasMatcher.group(1).replace(",", ""));
                    } catch (NumberFormatException e) {
                        log.debug("Could not parse was price from: {}", text);
                    }
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
