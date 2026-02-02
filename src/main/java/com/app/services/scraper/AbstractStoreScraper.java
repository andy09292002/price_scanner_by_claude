package com.app.services.scraper;

import com.app.models.Store;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractStoreScraper implements StoreScraper {

    protected final RateLimiter rateLimiter;

    @Value("${scraper.user-agent}")
    protected String userAgent;

    @Value("${scraper.timeout-seconds:30}")
    protected int timeoutSeconds;

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$?([\\d,]+\\.?\\d*)");

    protected AbstractStoreScraper(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiter = rateLimiterRegistry.rateLimiter("scraper");
    }

    @Override
    public boolean supports(Store store) {
        return getStoreCode().equals(store.getCode());
    }

    @Override
    public List<ScrapedProduct> scrapeAllProducts(Store store) {
        List<ScrapedProduct> allProducts = new ArrayList<>();
        List<String> categoryUrls = getCategoryUrls(store);

        for (String categoryUrl : categoryUrls) {
            try {
                List<ScrapedProduct> products = scrapeProducts(store, categoryUrl);
                allProducts.addAll(products);
                log.info("Scraped {} products from {}", products.size(), categoryUrl);
            } catch (Exception e) {
                log.error("Error scraping category URL: {}", categoryUrl, e);
            }
        }

        return allProducts;
    }

    protected abstract List<String> getCategoryUrls(Store store);

    protected Document fetchDocument(String url) throws IOException {
        rateLimiter.acquirePermission();

        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutSeconds * 1000)
                .get();
    }

    protected BigDecimal parsePrice(String priceText) {
        if (priceText == null || priceText.isBlank()) {
            return null;
        }

        Matcher matcher = PRICE_PATTERN.matcher(priceText.replace(",", ""));
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Could not parse price: {}", priceText);
            }
        }
        return null;
    }

    protected String normalizeProductName(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams|lb|lbs|oz|ml|mls|l|litre|litres|liter|liters|pack|packs|pk|ct|count|pcs|pc|piece|pieces|ea|each|unit|units)",
            Pattern.CASE_INSENSITIVE
    );

    protected String extractSize(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SIZE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);  // Return only the numeric part
        }
        return null;
    }

    protected String extractUnit(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SIZE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(2).toLowerCase();  // Return only the unit part
        }
        return null;
    }
}
