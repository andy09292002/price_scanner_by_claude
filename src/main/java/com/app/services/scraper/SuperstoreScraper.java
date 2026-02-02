package com.app.services.scraper;

import com.app.models.Store;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SuperstoreScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "RCSS";

    public SuperstoreScraper(RateLimiterRegistry rateLimiterRegistry) {
        super(rateLimiterRegistry);
    }

    @Override
    public String getStoreCode() {
        return STORE_CODE;
    }

    @Override
    protected List<String> getCategoryUrls(Store store) {
        List<String> urls = new ArrayList<>();
        String baseUrl = store.getBaseUrl();

        // Default category URLs for Real Canadian Superstore
        urls.add(baseUrl + "/food/fruits-vegetables/c/28000");
        urls.add(baseUrl + "/food/dairy-eggs/c/28003");
        urls.add(baseUrl + "/food/meat-seafood/c/28006");
        urls.add(baseUrl + "/food/bakery/c/28002");
        urls.add(baseUrl + "/food/pantry/c/28012");

        // Override with custom URLs from config if available
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

        try {
            Document doc = fetchDocument(categoryUrl);
            Elements productElements = doc.select(".product-tile, [data-testid='product-tile']");

            for (Element productElement : productElements) {
                try {
                    ScrapedProduct product = parseProductElement(productElement, categoryUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing product element", e);
                }
            }

            // Handle pagination
            Element nextPage = doc.selectFirst(".pagination__next:not(.disabled) a, [data-testid='pagination-next']");
            if (nextPage != null) {
                String nextUrl = nextPage.absUrl("href");
                if (nextUrl != null && !nextUrl.isEmpty()) {
                    log.debug("Following pagination to: {}", nextUrl);
                    products.addAll(scrapeProducts(store, nextUrl));
                }
            }

        } catch (Exception e) {
            log.error("Error scraping products from URL: {}", categoryUrl, e);
        }

        return products;
    }

    private ScrapedProduct parseProductElement(Element element, String sourceUrl) {
        String productId = element.attr("data-product-id");
        if (productId == null || productId.isEmpty()) {
            productId = element.attr("data-code");
        }

        String name = extractText(element, ".product-name, [data-testid='product-title']");
        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = extractText(element, ".product-brand, [data-testid='product-brand']");
        String priceText = extractText(element, ".price__value, [data-testid='product-price']");
        String salePriceText = extractText(element, ".price__value--sale, .sale-price, [data-testid='sale-price']");
        String unitPriceText = extractText(element, ".unit-price, [data-testid='unit-price']");
        String imageUrl = extractImageUrl(element, ".product-image img, [data-testid='product-image'] img");

        BigDecimal regularPrice = parsePrice(priceText);
        BigDecimal salePrice = parsePrice(salePriceText);
        BigDecimal unitPrice = parsePrice(unitPriceText);

        boolean onSale = salePrice != null && regularPrice != null &&
                         salePrice.compareTo(regularPrice) < 0;

        String promoDescription = null;
        if (onSale) {
            promoDescription = extractText(element, ".promo-badge, [data-testid='promo-badge']");
        }

        String sizeText = extractText(element, ".product-size, [data-testid='product-size']");
        String size = sizeText != null ? extractSize(sizeText) : extractSize(name);
        String unit = sizeText != null ? extractUnit(sizeText) : extractUnit(name);

        boolean inStock = !element.hasClass("out-of-stock") &&
                         element.selectFirst(".out-of-stock-badge") == null;

        return new ScrapedProduct(
                productId,
                name,
                brand,
                size,
                unit,
                null, // category determined later
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

    private String extractText(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element != null ? element.text().trim() : null;
    }

    private String extractImageUrl(Element parent, String selector) {
        Element img = parent.selectFirst(selector);
        if (img != null) {
            String src = img.attr("src");
            if (src.isEmpty()) {
                src = img.attr("data-src");
            }
            return src.isEmpty() ? null : src;
        }
        return null;
    }
}
