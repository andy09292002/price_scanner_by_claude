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
public class TntScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "TNT";
    private final ObjectProvider<WebDriver> webDriverProvider;

    public TntScraper(RateLimiterRegistry rateLimiterRegistry,
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

        // Default category URLs for T&T Supermarket
        urls.add(baseUrl + "/collections/fresh-produce");
        urls.add(baseUrl + "/collections/dairy-eggs");
        urls.add(baseUrl + "/collections/meat-poultry");
        urls.add(baseUrl + "/collections/seafood");
        urls.add(baseUrl + "/collections/bakery");
        urls.add(baseUrl + "/collections/asian-grocery");

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
                    By.cssSelector(".product-card, .product-item, .collection-product")));

            // Scroll to load lazy-loaded products
            scrollToLoadAll(driver);

            List<WebElement> productElements = driver.findElements(
                    By.cssSelector(".product-card, .product-item, .collection-product"));

            for (WebElement productElement : productElements) {
                try {
                    ScrapedProduct product = parseProductElement(productElement, categoryUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing T&T product element", e);
                }
            }

            // Handle pagination - T&T often uses infinite scroll or load more
            List<WebElement> loadMoreButtons = driver.findElements(
                    By.cssSelector(".load-more-btn, .pagination-next, [data-action='load-more']"));

            if (!loadMoreButtons.isEmpty()) {
                WebElement loadMore = loadMoreButtons.get(0);
                if (loadMore.isDisplayed() && loadMore.isEnabled()) {
                    loadMore.click();
                    Thread.sleep(2000);
                    // Recursively get more products
                    List<WebElement> newProducts = driver.findElements(
                            By.cssSelector(".product-card, .product-item, .collection-product"));
                    if (newProducts.size() > productElements.size()) {
                        // More products loaded, continue scraping
                        for (int i = productElements.size(); i < newProducts.size(); i++) {
                            try {
                                ScrapedProduct product = parseProductElement(newProducts.get(i), categoryUrl);
                                if (product != null) {
                                    products.add(product);
                                }
                            } catch (Exception e) {
                                log.warn("Error parsing additional T&T product element", e);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error scraping T&T products from URL: {}", categoryUrl, e);
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

        js.executeScript("window.scrollTo(0, 0);");
    }

    private ScrapedProduct parseProductElement(WebElement element, String sourceUrl) {
        String productId = element.getAttribute("data-product-id");
        if (productId == null || productId.isEmpty()) {
            productId = element.getAttribute("data-id");
        }
        if (productId == null || productId.isEmpty()) {
            // Generate a pseudo-ID from the element
            productId = "tnt-" + Math.abs(element.toString().hashCode());
        }

        String name = extractText(element, ".product-title, .product-name, .product-card__title");
        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = extractText(element, ".product-vendor, .product-brand");
        String priceText = extractText(element, ".product-price, .price, .money");
        String salePriceText = extractText(element, ".product-price--sale, .sale-price, .compare-at-price");
        String unitPriceText = extractText(element, ".unit-price");
        String imageUrl = extractImageUrl(element);

        BigDecimal regularPrice = parsePrice(priceText);
        BigDecimal salePrice = parsePrice(salePriceText);
        BigDecimal unitPrice = parsePrice(unitPriceText);

        // T&T's pricing structure may show compare-at-price as the original
        // and the main price as the sale price
        boolean onSale = false;
        if (salePrice != null && regularPrice != null) {
            if (salePrice.compareTo(regularPrice) > 0) {
                // salePrice is actually the original price
                BigDecimal temp = regularPrice;
                regularPrice = salePrice;
                salePrice = temp;
            }
            onSale = salePrice.compareTo(regularPrice) < 0;
        }

        String promoDescription = null;
        if (onSale) {
            promoDescription = extractText(element, ".product-badge, .sale-badge, .promo-label");
        }

        String size = extractSize(name);

        boolean inStock = !hasClass(element, "sold-out") &&
                         element.findElements(By.cssSelector(".sold-out, .out-of-stock")).isEmpty();

        return new ScrapedProduct(
                productId,
                name,
                brand,
                size,
                null,
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
            List<WebElement> images = parent.findElements(By.cssSelector("img.product-image, .product-card__image img, img"));
            if (!images.isEmpty()) {
                String src = images.get(0).getAttribute("src");
                if (src == null || src.isEmpty()) {
                    src = images.get(0).getAttribute("data-src");
                }
                if (src == null || src.isEmpty()) {
                    src = images.get(0).getAttribute("data-srcset");
                    if (src != null && src.contains(" ")) {
                        src = src.split(" ")[0];
                    }
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
