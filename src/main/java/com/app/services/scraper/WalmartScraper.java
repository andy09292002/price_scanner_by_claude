package com.app.services.scraper;

import com.app.models.Store;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WalmartScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "WALMART";
    private final ObjectProvider<WebDriver> webDriverProvider;

    public WalmartScraper(RateLimiterRegistry rateLimiterRegistry,
                          ObjectProvider<WebDriver> webDriverProvider) {
        super(rateLimiterRegistry);
        this.webDriverProvider = webDriverProvider;
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
        WebDriver driver = null;

        try {
            rateLimiter.acquirePermission();
            driver = webDriverProvider.getObject();
            driver.get(categoryUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            // Wait for products to load
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='product-card'], .product-card, .search-result-gridview-item")));

            // Scroll to load lazy-loaded products
            scrollToLoadAll(driver);

            List<WebElement> productElements = driver.findElements(
                    By.cssSelector("[data-testid='product-card'], .product-card, .search-result-gridview-item"));

            for (WebElement productElement : productElements) {
                try {
                    ScrapedProduct product = parseProductElement(productElement, categoryUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing Walmart product element", e);
                }
            }

            // Handle pagination
            List<WebElement> nextButtons = driver.findElements(
                    By.cssSelector("[data-testid='pagination-next']:not([disabled]), .paginator-btn-next:not(.disabled)"));
            if (!nextButtons.isEmpty() && nextButtons.get(0).isEnabled()) {
                String nextUrl = nextButtons.get(0).getAttribute("href");
                if (nextUrl != null && !nextUrl.isEmpty()) {
                    driver.quit();
                    driver = null;
                    products.addAll(scrapeProducts(store, nextUrl));
                }
            }

        } catch (Exception e) {
            log.error("Error scraping Walmart products from URL: {}", categoryUrl, e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        return products;
    }

    private void scrollToLoadAll(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

        int maxScrolls = 10;
        int scrollCount = 0;

        while (scrollCount < maxScrolls) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");

            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) {
                break;
            }
            lastHeight = newHeight;
            scrollCount++;
        }

        // Scroll back to top
        js.executeScript("window.scrollTo(0, 0);");
    }

    private ScrapedProduct parseProductElement(WebElement element, String sourceUrl) {
        String productId = element.getAttribute("data-product-id");
        if (productId == null || productId.isEmpty()) {
            productId = element.getAttribute("data-item-id");
        }

        String name = extractText(element, "[data-testid='product-title'], .product-title, .product-name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = extractText(element, "[data-testid='product-brand'], .product-brand");
        String priceText = extractText(element, "[data-testid='price'], .price-main, .price");
        String salePriceText = extractText(element, "[data-testid='sale-price'], .price-reduced");
        String unitPriceText = extractText(element, "[data-testid='unit-price'], .unit-price");
        String imageUrl = extractImageUrl(element);

        BigDecimal regularPrice = parsePrice(priceText);
        BigDecimal salePrice = parsePrice(salePriceText);
        BigDecimal unitPrice = parsePrice(unitPriceText);

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        String promoDescription = null;
        if (onSale) {
            promoDescription = extractText(element, "[data-testid='promo-badge'], .promo-flag");
        }

        String size = extractSize(name);

        boolean inStock = !hasClass(element, "out-of-stock") &&
                         element.findElements(By.cssSelector(".out-of-stock-badge, [data-testid='oos-badge']")).isEmpty();

        return new ScrapedProduct(
                productId,
                name,
                brand,
                size,
                null,
                null,
                imageUrl,
                regularPrice,
                onSale ? salePrice : regularPrice,
                unitPrice,
                onSale,
                promoDescription,
                inStock,
                sourceUrl
        );
    }

    private String extractText(WebElement parent, String selector) {
        try {
            List<WebElement> elements = parent.findElements(By.cssSelector(selector));
            if (!elements.isEmpty()) {
                return elements.get(0).getText().trim();
            }
        } catch (Exception e) {
            // Element not found
        }
        return null;
    }

    private String extractImageUrl(WebElement parent) {
        try {
            List<WebElement> images = parent.findElements(By.cssSelector("img[data-testid='product-image'], .product-image img"));
            if (!images.isEmpty()) {
                String src = images.get(0).getAttribute("src");
                if (src == null || src.isEmpty()) {
                    src = images.get(0).getAttribute("data-src");
                }
                return src;
            }
        } catch (Exception e) {
            // Element not found
        }
        return null;
    }

    private boolean hasClass(WebElement element, String className) {
        String classes = element.getAttribute("class");
        return classes != null && classes.contains(className);
    }
}
