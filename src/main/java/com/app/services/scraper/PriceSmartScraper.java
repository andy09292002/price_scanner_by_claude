package com.app.services.scraper;

import com.app.models.Store;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PriceSmartScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "PRICESMART";
    private static final String STORE_ID = "2274"; // Richmond store

    // Category ID to name mapping
    private static final Map<String, String> CATEGORY_IDS;
    static {
        Map<String, String> map = new java.util.HashMap<>();
        // Fruits & Vegetables
        map.put("30682", "Fresh Fruit");
        map.put("30723", "Fresh Juice & Smoothies");
        map.put("30724", "Fresh Noodle, Tofu & Soy Products");
        map.put("30694", "Fresh Vegetables");
        map.put("30717", "Salad Kits & Greens");
        map.put("30713", "Trays, Baskets, Platters");
        map.put("30725", "Dried Snack Fruit & Nuts");
        map.put("30722", "Dressing & Dips");
        // Bakery
        map.put("30847", "Bagels & English Muffins");
        map.put("30850", "Breads");
        map.put("30888", "Cakes");
        map.put("30879", "Dessert & Pastries");
        map.put("30901", "Frozen Bakery");
        map.put("30894", "Pies & Tarts");
        map.put("30899", "Pitas, Flatbread & Wraps");
        map.put("30887", "Pizza Crust & Crumbs");
        map.put("30873", "Rolls & Buns");
        map.put("30900", "Roti & Naan Breads");
        // Dairy & Eggs
        map.put("30907", "Butter & Margarine");
        map.put("30910", "Cheese");
        map.put("30920", "Chilled Juice & Drinks");
        map.put("30929", "Dough Products");
        map.put("30919", "Eggs & Substitutes");
        map.put("30930", "Milk & Creams");
        map.put("30939", "Milk Substitutes");
        map.put("30943", "Pudding & Desserts");
        map.put("30944", "Sour Cream & Dips");
        map.put("30945", "Yogurt");
        // Deli & Ready Made Meals
        map.put("30748", "Deli Cheese");
        map.put("30772", "Dips, Spreads & Olives");
        map.put("30727", "Deli Meat");
        map.put("30790", "Party Platters");
        map.put("30776", "Quick Ready Meals & Sides");
        // Frozen
        map.put("30950", "Frozen Appetizers & Snacks");
        map.put("30956", "Frozen Bakery");
        map.put("30960", "Frozen Beverages & Ice");
        map.put("30967", "Frozen Breakfast");
        map.put("30971", "Frozen Fruit");
        map.put("30976", "Frozen Meals & Sides");
        map.put("30982", "Frozen Meat");
        map.put("30993", "Frozen Pizza");
        map.put("30999", "Frozen Seafood");
        map.put("31002", "Frozen Vegetables");
        map.put("31008", "Ice Cream & Desserts");
        // International Foods
        map.put("31406", "Asian");
        map.put("31439", "European");
        map.put("31415", "Indian & Middle Eastern");
        map.put("31432", "Latin & Mexican");
        map.put("31445", "Mediterranean");
        // Meat & Seafood
        map.put("30817", "Bacon");
        map.put("30792", "Beef & Veal");
        map.put("30798", "Chicken & Turkey");
        map.put("30827", "Fish");
        map.put("30830", "Frozen Meat");
        map.put("30842", "Frozen Seafood");
        map.put("30816", "Game & Specialty Meats");
        map.put("30818", "Hot Dogs & Sausages");
        map.put("30815", "Lamb");
        map.put("30821", "Meat Alternatives");
        map.put("30807", "Pork & Ham");
        map.put("30828", "Shrimp & Shell Fish");
        map.put("30829", "Smoked & Cured Fish");
        // Pantry
        map.put("30373", "Baking Goods");
        map.put("30481", "Breakfast");
        map.put("30527", "Canned & Packaged");
        map.put("30596", "Condiments & Toppings");
        map.put("30635", "Herbs, Spices & Seasonings");
        map.put("30614", "Marinates & Sauces");
        map.put("30625", "Oils & Vinegars");
        map.put("30652", "Pasta, Sauces & Grains");
        map.put("30385", "Beverages");
        map.put("31287", "Bulk");
        map.put("30504", "Candy");
        map.put("30511", "Snacks");
        CATEGORY_IDS = java.util.Collections.unmodifiableMap(map);
    }

    // Category URLs to scrape
    private static final List<String> CATEGORY_URLS = List.of(
            // Fruits & Vegetables
            "/sm/pickup/rsid/2274/categories/fruits-vegetables/fresh-fruit-id-30682",
            "/sm/pickup/rsid/2274/categories/fruits-vegetables/fresh-vegetables-id-30694",
            "/sm/pickup/rsid/2274/categories/fruits-vegetables/salad-kits-greens-essentials-id-30717",
            "/sm/pickup/rsid/2274/categories/fruits-vegetables/fresh-juice-smoothies-id-30723",
            "/sm/pickup/rsid/2274/categories/fruits-vegetables/fresh-noodle-tofu-soy-products-id-30724",
            // Bakery
            "/sm/pickup/rsid/2274/categories/bakery/breads-id-30850",
            "/sm/pickup/rsid/2274/categories/bakery/rolls-buns-id-30873",
            "/sm/pickup/rsid/2274/categories/bakery/bagels-english-muffins-id-30847",
            // Dairy & Eggs
            "/sm/pickup/rsid/2274/categories/dairy-eggs/milk-creams-id-30930",
            "/sm/pickup/rsid/2274/categories/dairy-eggs/eggs-substitutes-id-30919",
            "/sm/pickup/rsid/2274/categories/dairy-eggs/cheese-id-30910",
            "/sm/pickup/rsid/2274/categories/dairy-eggs/butter-margarine-id-30907",
            "/sm/pickup/rsid/2274/categories/dairy-eggs/yogurt-id-30945",
            // Meat & Seafood
            "/sm/pickup/rsid/2274/categories/meat-seafood/chicken-turkey-id-30798",
            "/sm/pickup/rsid/2274/categories/meat-seafood/beef-veal-id-30792",
            "/sm/pickup/rsid/2274/categories/meat-seafood/pork-ham-id-30807",
            "/sm/pickup/rsid/2274/categories/meat-seafood/fish-id-30827",
            "/sm/pickup/rsid/2274/categories/meat-seafood/bacon-id-30817",
            // Frozen
            "/sm/pickup/rsid/2274/categories/frozen/frozen-vegetables-id-31002",
            "/sm/pickup/rsid/2274/categories/frozen/frozen-fruit-id-30971",
            "/sm/pickup/rsid/2274/categories/frozen/frozen-meals-sides-id-30976",
            "/sm/pickup/rsid/2274/categories/frozen/ice-cream-desserts-id-31008",
            // Pantry
            "/sm/pickup/rsid/2274/categories/pantry/breakfast-id-30481",
            "/sm/pickup/rsid/2274/categories/pantry/canned-packaged-id-30527",
            "/sm/pickup/rsid/2274/categories/pantry/beverages-id-30385",
            "/sm/pickup/rsid/2274/categories/pantry/snacks-id-30511"
    );

    private Playwright playwright;
    private Browser browser;

    public PriceSmartScraper(RateLimiterRegistry rateLimiterRegistry) {
        super(rateLimiterRegistry);
    }

    private synchronized void initBrowser() {
        if (playwright == null) {
            log.info("Initializing Playwright browser...");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            log.info("Playwright browser initialized");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public String getStoreCode() {
        return STORE_CODE;
    }

    @Override
    protected List<String> getCategoryUrls(Store store) {
        List<String> urls = new ArrayList<>();
        String baseUrl = store.getBaseUrl();

        // Check for custom URLs from store config
        Map<String, Object> config = store.getScraperConfig();
        if (config != null && config.containsKey("categoryUrls")) {
            Object configUrls = config.get("categoryUrls");
            if (configUrls instanceof List<?>) {
                for (Object url : (List<?>) configUrls) {
                    urls.add(url.toString());
                }
                return urls;
            }
        }

        // Use default category URLs
        for (String categoryPath : CATEGORY_URLS) {
            urls.add(baseUrl + categoryPath);
        }

        return urls;
    }

    @Override
    public List<ScrapedProduct> scrapeProducts(Store store, String categoryUrl) {
        List<ScrapedProduct> products = new ArrayList<>();

        try {
            initBrowser();

            log.debug("Fetching PriceSmart products from: {}", categoryUrl);
            rateLimiter.acquirePermission();

            String html = fetchRenderedHtml(categoryUrl);
            Document doc = Jsoup.parse(html);

            // Select all product cards
            Elements productElements = doc.select("article[class*=ProductCardWrapper]");
            log.info("Found {} product elements on page", productElements.size());

            for (Element productElement : productElements) {
                try {
                    ScrapedProduct product = parseProductElement(productElement, categoryUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing PriceSmart product element: {}", e.getMessage());
                }
            }

            // Handle "Load More" or infinite scroll by scrolling down
            // For now, we just get the initial page load

        } catch (Exception e) {
            log.error("Error scraping PriceSmart products from URL: {}", categoryUrl, e);
        }

        return products;
    }

    private String fetchRenderedHtml(String url) {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();

            log.info("Navigating to: {}", url);

            // Navigate and wait for page load
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            log.info("Page loaded, waiting for content...");

            // Wait a bit for JavaScript to render
            page.waitForTimeout(5000);

            // Check page title
            String title = page.title();
            log.info("Page title: {}", title);

            // Try to dismiss any cookie/popup dialogs
            try {
                page.click("button:has-text('Accept')", new Page.ClickOptions().setTimeout(3000));
                log.info("Clicked Accept button");
            } catch (Exception e) {
                // No accept button, continue
            }

            // Wait for product cards to appear (longer timeout)
            try {
                log.info("Waiting for ProductCardWrapper...");
                page.waitForSelector("article[class*=ProductCardWrapper]",
                        new Page.WaitForSelectorOptions().setTimeout(30000));
                log.info("Found ProductCardWrapper elements");
            } catch (Exception e) {
                log.warn("Could not find ProductCardWrapper: {}", e.getMessage());

                // Debug: log what we can see on page
                String bodyText = page.locator("body").innerText();
                log.info("Page body preview (first 500 chars): {}",
                    bodyText.length() > 500 ? bodyText.substring(0, 500) : bodyText);

                // Save screenshot for debugging
                try {
                    byte[] screenshot = page.screenshot();
                    java.nio.file.Files.write(
                        java.nio.file.Paths.get("pricesmart-debug.png"),
                        screenshot
                    );
                    log.info("Saved debug screenshot to pricesmart-debug.png");
                } catch (Exception ex) {
                    log.warn("Could not save screenshot: {}", ex.getMessage());
                }
            }

            // Scroll to load more products and trigger lazy image loading
            for (int i = 0; i < 3; i++) {
                page.evaluate("window.scrollBy(0, window.innerHeight)");
                page.waitForTimeout(2000);
            }

            // Scroll back to top and wait for images to load
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(1000);

            // Force lazy images to load by scrolling through all product cards
            page.evaluate("() => {"
                    + "  const cards = document.querySelectorAll('article[class*=ProductCardWrapper]');"
                    + "  cards.forEach(card => card.scrollIntoView({behavior: 'instant'}));"
                    + "}");
            page.waitForTimeout(2000);

            // Wait for network to be mostly idle (images loading)
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(10000));
            } catch (Exception e) {
                log.debug("Network idle timeout, proceeding with available content");
            }

            String content = page.content();
            log.info("Fetched page content length: {} characters", content.length());
            return content;
        }
    }

    private ScrapedProduct parseProductElement(Element element, String sourceUrl) {
        // Extract product ID from data-testid
        String testId = element.attr("data-testid");
        String productId = testId.replace("ProductCardWrapper-", "");

        if (productId.isEmpty()) {
            return null;
        }

        // Extract product name
        Element titleElement = element.selectFirst("[class*=ProductCardTitle]");
        String fullName = titleElement != null ? titleElement.text().trim() : null;

        if (fullName == null || fullName.isEmpty()) {
            return null;
        }

        // Remove "Open product description" suffix
        fullName = fullName.replace("Open product description", "").trim();

        // Extract brand
        Element brandElement = element.selectFirst("[class*=ProductAQABrand]");
        String brand = brandElement != null ? brandElement.text().trim() : null;

        // Extract price
        Element priceElement = element.selectFirst("[class*=ProductCardPrice--]");
        String priceText = priceElement != null ? priceElement.text().trim() : null;
        BigDecimal regularPrice = parsePrice(priceText);

        // Extract unit price
        Element unitPriceElement = element.selectFirst("[class*=ProductCardPriceInfo]");
        String unitPriceText = unitPriceElement != null ? unitPriceElement.text().trim() : null;
        BigDecimal unitPrice = parsePrice(unitPriceText);

        // Extract image URL
        String imageUrl = null;
        Element imgContainer = element.selectFirst("[class*=ProductCardImage]");
        if (imgContainer != null) {
            // The container may be a <div>; find the actual <img> inside it
            Element imgElement = imgContainer.tagName().equalsIgnoreCase("img")
                    ? imgContainer
                    : imgContainer.selectFirst("img");
            if (imgElement != null) {
                imageUrl = imgElement.attr("src");
                // Fall back to data-src for lazy-loaded images
                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = imgElement.attr("data-src");
                }
                // Fall back to srcset (take the first URL)
                if (imageUrl == null || imageUrl.isBlank()) {
                    String srcset = imgElement.attr("srcset");
                    if (srcset != null && !srcset.isBlank()) {
                        imageUrl = srcset.split("[,\\s]")[0].trim();
                    }
                }
                // Skip blob: or data: placeholder URLs
                if (imageUrl != null && (imageUrl.startsWith("blob:") || imageUrl.startsWith("data:"))) {
                    imageUrl = null;
                }
            }
        }
        // Broader fallback: look for any img inside the product card with a valid URL
        if (imageUrl == null || imageUrl.isBlank()) {
            for (Element img : element.select("img")) {
                String src = img.attr("src");
                if (src != null && !src.isBlank() && src.startsWith("http") && !src.contains("placeholder")) {
                    imageUrl = src;
                    break;
                }
                String srcset = img.attr("srcset");
                if (srcset != null && !srcset.isBlank()) {
                    String candidate = srcset.split("[,\\s]")[0].trim();
                    if (candidate.startsWith("http")) {
                        imageUrl = candidate;
                        break;
                    }
                }
            }
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            log.debug("Extracted image URL for {}: {}", fullName, imageUrl);
        } else {
            // Log all img tags found for debugging
            Elements allImgs = element.select("img");
            log.debug("No image found for {}. Found {} img tags. First img attrs: {}",
                    fullName, allImgs.size(),
                    allImgs.isEmpty() ? "none" : allImgs.first().attributes());
        }

        // Extract product link
        Element linkElement = element.selectFirst("a[class*=ProductCardHiddenLink]");
        String productUrl = linkElement != null ? linkElement.attr("href") : sourceUrl;

        // Extract size and unit from name (e.g., "250 Gram")
        String size = extractSize(fullName);
        String unit = extractUnit(fullName);

        // Check for sale/promo badges and extract savings
        BigDecimal salePrice = null;
        boolean onSale = false;
        String promoDescription = null;

        // Look for promotion badges (e.g., "SAVE $2.00", "2 FOR $5.00")
        Elements badgeElements = element.select("[class*=Badge], [class*=PromotionBadge]");
        for (Element badge : badgeElements) {
            String badgeText = badge.text().trim();
            if (badgeText.isEmpty()) continue;

            promoDescription = badgeText;

            // Try to extract savings amount from badge text (e.g., "SAVE $2.00", "Save $1.50")
            Matcher saveMatcher = Pattern.compile("(?i)save\\s*\\$?([\\d]+\\.?\\d*)").matcher(badgeText);
            if (saveMatcher.find() && regularPrice != null) {
                BigDecimal savings = parsePrice(saveMatcher.group(1));
                if (savings != null && savings.compareTo(BigDecimal.ZERO) > 0) {
                    // Displayed price is the sale price; original is displayed + savings
                    salePrice = regularPrice;
                    regularPrice = regularPrice.add(savings);
                    onSale = true;
                }
            }

            // Try percentage off (e.g., "20% OFF")
            if (!onSale) {
                Matcher pctMatcher = Pattern.compile("(\\d+)\\s*%\\s*(?i)off").matcher(badgeText);
                if (pctMatcher.find() && regularPrice != null) {
                    // Badge says X% off, but displayed price is already the sale price
                    int pctOff = Integer.parseInt(pctMatcher.group(1));
                    if (pctOff > 0 && pctOff < 100) {
                        // originalPrice * (1 - pct/100) = displayedPrice => originalPrice = displayedPrice / (1 - pct/100)
                        BigDecimal originalPrice = regularPrice.multiply(new BigDecimal(100))
                                .divide(new BigDecimal(100 - pctOff), 2, java.math.RoundingMode.HALF_UP);
                        salePrice = regularPrice;
                        regularPrice = originalPrice;
                        onSale = true;
                    }
                }
            }

            if (onSale) break;
        }

        // Also check for "View Deal" indicator as a sale signal
        if (!onSale) {
            Element viewDeal = element.selectFirst("[class*=ViewDeal]");
            if (viewDeal != null) {
                onSale = true;
                if (promoDescription == null) {
                    promoDescription = viewDeal.text().trim();
                }
            }
        }

        // Look for a separate was/original price element (strikethrough price)
        if (!onSale || salePrice == null) {
            Elements allPriceElements = element.select("[class*=ProductCardPrice]");
            if (allPriceElements.size() > 1 && regularPrice != null) {
                // Multiple price elements likely means was-price + current price
                for (Element pe : allPriceElements) {
                    BigDecimal otherPrice = parsePrice(pe.text().trim());
                    if (otherPrice != null && otherPrice.compareTo(regularPrice) > 0) {
                        // Found a higher price - this is the original/was price
                        salePrice = regularPrice;
                        regularPrice = otherPrice;
                        onSale = true;
                        break;
                    }
                }
            }
        }

        // Check stock status
        boolean inStock = element.selectFirst("[class*=outOfStock], [class*=OutOfStock]") == null;

        // Extract category from URL
        String category = extractCategoryFromUrl(sourceUrl);

        return new ScrapedProduct(
                productId,
                fullName,
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
                productUrl
        );
    }

    private String extractCategoryFromUrl(String url) {
        // Extract category ID from URL like /categories/dairy-eggs-id-30906
        Pattern pattern = Pattern.compile("id-(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String categoryId = matcher.group(1);
            String categoryName = CATEGORY_IDS.getOrDefault(categoryId, categoryId);
            return categoryId + ":" + categoryName;
        }
        return null;
    }
}
