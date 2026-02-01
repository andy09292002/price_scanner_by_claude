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
public class PriceSmartScraper extends AbstractStoreScraper {

    public static final String STORE_CODE = "PRICESMART";

    public PriceSmartScraper(RateLimiterRegistry rateLimiterRegistry) {
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

        // Default category URLs for PriceSmart Foods
        urls.add(baseUrl + "/flyer");
        urls.add(baseUrl + "/produce");
        urls.add(baseUrl + "/dairy");
        urls.add(baseUrl + "/meat");
        urls.add(baseUrl + "/bakery");

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
            Elements productElements = doc.select(".product-item, .flyer-item, .product-card");

            for (Element productElement : productElements) {
                try {
                    ScrapedProduct product = parseProductElement(productElement, categoryUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing PriceSmart product element", e);
                }
            }

            // Handle pagination
            Element nextPage = doc.selectFirst(".pagination .next a, .load-more a");
            if (nextPage != null) {
                String nextUrl = nextPage.absUrl("href");
                if (nextUrl != null && !nextUrl.isEmpty()) {
                    log.debug("Following pagination to: {}", nextUrl);
                    products.addAll(scrapeProducts(store, nextUrl));
                }
            }

        } catch (Exception e) {
            log.error("Error scraping PriceSmart products from URL: {}", categoryUrl, e);
        }

        return products;
    }

    private ScrapedProduct parseProductElement(Element element, String sourceUrl) {
        String productId = element.attr("data-product-id");
        if (productId == null || productId.isEmpty()) {
            productId = element.attr("data-sku");
        }
        if (productId == null || productId.isEmpty()) {
            productId = String.valueOf(element.hashCode());
        }

        String name = extractText(element, ".product-name, .item-name, .product-title");
        if (name == null || name.isEmpty()) {
            return null;
        }

        String brand = extractText(element, ".product-brand, .brand");
        String priceText = extractText(element, ".product-price, .price, .regular-price");
        String salePriceText = extractText(element, ".sale-price, .special-price");
        String unitPriceText = extractText(element, ".unit-price, .price-per-unit");
        String imageUrl = extractImageUrl(element);

        BigDecimal regularPrice = parsePrice(priceText);
        BigDecimal salePrice = parsePrice(salePriceText);
        BigDecimal unitPrice = parsePrice(unitPriceText);

        boolean onSale = salePrice != null;
        if (!onSale && element.hasClass("on-sale")) {
            onSale = true;
        }

        // If sale price exists but regular price doesn't, swap them
        if (salePrice != null && regularPrice == null) {
            regularPrice = salePrice;
            salePrice = null;
            onSale = false;
        }

        String promoDescription = null;
        if (onSale) {
            promoDescription = extractText(element, ".promo-text, .deal-text, .special-text");
        }

        String sizeText = extractText(element, ".product-size, .size");
        String size = sizeText != null ? sizeText : extractSize(name);

        boolean inStock = !element.hasClass("out-of-stock") &&
                         element.selectFirst(".out-of-stock, .sold-out") == null;

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

    private String extractText(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element != null ? element.text().trim() : null;
    }

    private String extractImageUrl(Element parent) {
        Element img = parent.selectFirst("img.product-image, img.item-image, .product-image img");
        if (img != null) {
            String src = img.attr("src");
            if (src.isEmpty()) {
                src = img.attr("data-src");
            }
            if (src.isEmpty()) {
                src = img.attr("data-lazy");
            }
            return src.isEmpty() ? null : src;
        }
        return null;
    }
}
