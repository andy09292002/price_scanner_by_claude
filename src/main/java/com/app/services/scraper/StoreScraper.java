package com.app.services.scraper;

import com.app.models.PriceRecord;
import com.app.models.Product;
import com.app.models.Store;

import java.util.List;

public interface StoreScraper {

    String getStoreCode();

    boolean supports(Store store);

    List<ScrapedProduct> scrapeProducts(Store store, String categoryUrl);

    List<ScrapedProduct> scrapeAllProducts(Store store);

    record ScrapedProduct(
            String storeProductId,
            String name,
            String brand,
            String size,
            String unit,
            String category,
            String imageUrl,
            java.math.BigDecimal regularPrice,
            java.math.BigDecimal salePrice,
            java.math.BigDecimal unitPrice,
            boolean onSale,
            String promoDescription,
            boolean inStock,
            String sourceUrl
    ) {}
}
