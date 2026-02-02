package com.app.config;

import com.app.models.Category;
import com.app.models.CategoryRepository;
import com.app.models.Product;
import com.app.models.ProductRepository;
import com.app.models.Store;
import com.app.models.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // T&T category ID to name mapping for fixing old data
    private static final Map<String, String> TNT_CATEGORY_ID_TO_NAME = Map.of(
            "2876", "Bakery",
            "2877", "Fruits",
            "2878", "Vegetables",
            "2879", "Meat",
            "2880", "Seafood",
            "2881", "Dairy & Eggs"
    );

    // Reverse mapping: name to ID
    private static final Map<String, String> TNT_CATEGORY_NAME_TO_ID = Map.of(
            "bakery", "2876",
            "fruits", "2877",
            "vegetables", "2878",
            "meat", "2879",
            "seafood", "2880",
            "dairy & eggs", "2881",
            "dairy-eggs", "2881",
            "dairy---eggs", "2881"
    );

    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams|lb|lbs|oz|ml|mls|l|litre|litres|liter|liters|pack|packs|pk|ct|count|pcs|pc|piece|pieces|ea|each|unit|units)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void run(String... args) {
        initializeStores();
        fixNumericCategories();
        fixProductSizeAndUnit();
    }

    private void initializeStores() {
        if (storeRepository.count() == 0) {
            log.info("Initializing store data...");

            List<Store> stores = List.of(
                Store.builder()
                    .name("T&T Supermarket")
                    .code("TNT")
                    .baseUrl("https://www.tntsupermarket.com")
                    .active(true)
                    .build(),
                Store.builder()
                    .name("Walmart Canada")
                    .code("WALMART")
                    .baseUrl("https://www.walmart.ca")
                    .active(true)
                    .build(),
                Store.builder()
                    .name("Real Canadian Superstore")
                    .code("RCSS")
                    .baseUrl("https://www.realcanadiansuperstore.ca")
                    .active(true)
                    .build(),
                Store.builder()
                    .name("PriceSmart Foods")
                    .code("PRICESMART")
                    .baseUrl("https://www.pricesmartfoods.ca")
                    .active(true)
                    .build()
            );

            storeRepository.saveAll(stores);
            log.info("Initialized {} stores", stores.size());
        } else {
            log.info("Store data already exists, skipping initialization");
        }
    }

    private void fixNumericCategories() {
        List<Category> categories = categoryRepository.findAll();
        int fixedCount = 0;

        for (Category category : categories) {
            boolean updated = false;
            String currentCode = category.getCode();
            String currentName = category.getName();

            // Case 1: Category name is numeric (like "2880") - fix the name
            if (currentName != null && currentName.matches("\\d+")) {
                String properName = TNT_CATEGORY_ID_TO_NAME.get(currentName);
                if (properName != null) {
                    log.info("Fixing category name: {} -> {}", currentName, properName);
                    category.setName(properName);
                    // Also fix code if it's the same as name
                    if (currentCode == null || currentCode.equals(currentName) || !currentCode.matches("\\d+")) {
                        category.setCode(currentName);
                    }
                    updated = true;
                }
            }

            // Case 2: Category code is not numeric (like "seafood") - fix the code
            if (currentCode != null && !currentCode.matches("\\d+")) {
                String properId = TNT_CATEGORY_NAME_TO_ID.get(currentCode.toLowerCase());
                if (properId != null) {
                    log.info("Fixing category code: {} -> {}", currentCode, properId);
                    category.setCode(properId);
                    // Also ensure name is proper
                    String properName = TNT_CATEGORY_ID_TO_NAME.get(properId);
                    if (properName != null && !category.getName().equals(properName)) {
                        category.setName(properName);
                    }
                    updated = true;
                }
            }

            if (updated) {
                categoryRepository.save(category);
                fixedCount++;
            }
        }

        if (fixedCount > 0) {
            log.info("Fixed {} categories", fixedCount);
        }
    }

    private void fixProductSizeAndUnit() {
        List<Product> products = productRepository.findAll();
        int fixedCount = 0;

        for (Product product : products) {
            boolean updated = false;
            String name = product.getName();

            if (name == null) continue;

            Matcher matcher = SIZE_PATTERN.matcher(name);
            if (matcher.find()) {
                String sizeValue = matcher.group(1);
                String unitValue = matcher.group(2).toLowerCase();

                // Fix size if it's null, empty, or contains non-numeric characters
                if (product.getSize() == null || product.getSize().isEmpty() ||
                    !product.getSize().matches("\\d+(\\.\\d+)?")) {
                    product.setSize(sizeValue);
                    updated = true;
                }

                // Fix unit if it's null or empty
                if (product.getUnit() == null || product.getUnit().isEmpty()) {
                    product.setUnit(unitValue);
                    updated = true;
                }
            }

            if (updated) {
                productRepository.save(product);
                fixedCount++;
            }
        }

        if (fixedCount > 0) {
            log.info("Fixed size/unit for {} products", fixedCount);
        }
    }
}
