package com.app.services;

import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import com.app.models.ProductRepository;
import com.app.models.Store;
import com.app.services.scraper.StoreScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMatchingService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public Product findOrCreateProduct(StoreScraper.ScrapedProduct scrapedProduct, Store store) {
        // First, try to find by store's product ID
        Optional<Product> existingByStoreId = productRepository
                .findByStoreCodeAndStoreProductId(store.getCode(), scrapedProduct.storeProductId());

        if (existingByStoreId.isPresent()) {
            Product product = existingByStoreId.get();
            updateProductIfNeeded(product, scrapedProduct, store);
            return product;
        }

        // Try to find by normalized name
        String normalizedName = normalizeProductName(scrapedProduct.name());
        Optional<Product> existingByName = productRepository.findByNormalizedName(normalizedName);

        if (existingByName.isPresent()) {
            Product product = existingByName.get();
            // Add this store's product ID mapping
            addStoreProductMapping(product, store.getCode(), scrapedProduct.storeProductId());
            updateProductIfNeeded(product, scrapedProduct, store);
            return product;
        }

        // Create new product
        return createProduct(scrapedProduct, store);
    }

    private Product createProduct(StoreScraper.ScrapedProduct scrapedProduct, Store store) {
        Map<String, String> storeProductIds = new HashMap<>();
        storeProductIds.put(store.getCode(), scrapedProduct.storeProductId());

        String categoryId = findOrCreateCategory(scrapedProduct.category());

        Product product = Product.builder()
                .name(scrapedProduct.name())
                .normalizedName(normalizeProductName(scrapedProduct.name()))
                .brand(scrapedProduct.brand())
                .size(scrapedProduct.size())
                .unit(scrapedProduct.unit())
                .categoryId(categoryId)
                .imageUrl(scrapedProduct.imageUrl())
                .storeProductIds(storeProductIds)
                .build();

        return productRepository.save(product);
    }

    private void updateProductIfNeeded(Product product, StoreScraper.ScrapedProduct scrapedProduct, Store store) {
        boolean updated = false;

        // Update image if we don't have one
        if (product.getImageUrl() == null && scrapedProduct.imageUrl() != null) {
            product.setImageUrl(scrapedProduct.imageUrl());
            updated = true;
        }

        // Update brand if we don't have one
        if (product.getBrand() == null && scrapedProduct.brand() != null) {
            product.setBrand(scrapedProduct.brand());
            updated = true;
        }

        // Update size if we don't have one
        if (product.getSize() == null && scrapedProduct.size() != null) {
            product.setSize(scrapedProduct.size());
            updated = true;
        }

        // Ensure store mapping exists
        if (product.getStoreProductIds() == null) {
            product.setStoreProductIds(new HashMap<>());
        }
        if (!product.getStoreProductIds().containsKey(store.getCode())) {
            product.getStoreProductIds().put(store.getCode(), scrapedProduct.storeProductId());
            updated = true;
        }

        if (updated) {
            productRepository.save(product);
        }
    }

    private void addStoreProductMapping(Product product, String storeCode, String storeProductId) {
        if (product.getStoreProductIds() == null) {
            product.setStoreProductIds(new HashMap<>());
        }
        if (!product.getStoreProductIds().containsKey(storeCode)) {
            product.getStoreProductIds().put(storeCode, storeProductId);
            productRepository.save(product);
        }
    }

    private String findOrCreateCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }

        String categoryCode = normalizeCategoryCode(categoryName);

        Optional<Category> existing = categoryRepository.findByCode(categoryCode);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        Category category = Category.builder()
                .name(categoryName)
                .code(categoryCode)
                .build();

        return categoryRepository.save(category).getId();
    }

    public String normalizeProductName(String name) {
        if (name == null) {
            return null;
        }

        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeCategoryCode(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public double calculateSimilarity(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return 0.0;
        }

        String norm1 = normalizeProductName(name1);
        String norm2 = normalizeProductName(name2);

        if (norm1.equals(norm2)) {
            return 1.0;
        }

        // Calculate Jaccard similarity on words
        String[] words1 = norm1.split("\\s+");
        String[] words2 = norm2.split("\\s+");

        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(words1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(words2));

        java.util.Set<String> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);

        java.util.Set<String> union = new java.util.HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}
