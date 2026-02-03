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

    // Superstore category IDs (28xxx range)
    private static final Map<String, String> SUPERSTORE_CATEGORY_ID_TO_NAME = Map.of(
            "28000", "Fruits & Vegetables",
            "28002", "Bakery",
            "28003", "Dairy & Eggs",
            "28006", "Meat & Seafood",
            "28012", "Pantry"
    );

    // PriceSmart category IDs (30xxx-31xxx range)
    private static final Map<String, String> PRICESMART_CATEGORY_ID_TO_NAME;
    static {
        Map<String, String> map = new java.util.HashMap<>();
        // Fruits & Vegetables
        map.put("30682", "Fresh Fruit");
        map.put("30694", "Fresh Vegetables");
        map.put("30717", "Salad Kits & Greens");
        map.put("30723", "Fresh Juice & Smoothies");
        map.put("30724", "Fresh Noodle, Tofu & Soy Products");
        // Bakery
        map.put("30847", "Bagels & English Muffins");
        map.put("30850", "Breads");
        map.put("30873", "Rolls & Buns");
        map.put("30888", "Cakes");
        map.put("30879", "Dessert & Pastries");
        // Dairy & Eggs
        map.put("30907", "Butter & Margarine");
        map.put("30910", "Cheese");
        map.put("30919", "Eggs & Substitutes");
        map.put("30930", "Milk & Creams");
        map.put("30945", "Yogurt");
        // Meat & Seafood
        map.put("30792", "Beef & Veal");
        map.put("30798", "Chicken & Turkey");
        map.put("30807", "Pork & Ham");
        map.put("30817", "Bacon");
        map.put("30827", "Fish");
        // Frozen
        map.put("30971", "Frozen Fruit");
        map.put("30976", "Frozen Meals & Sides");
        map.put("31002", "Frozen Vegetables");
        map.put("31008", "Ice Cream & Desserts");
        // Pantry
        map.put("30385", "Beverages");
        map.put("30481", "Breakfast");
        map.put("30511", "Snacks");
        map.put("30527", "Canned & Packaged");
        PRICESMART_CATEGORY_ID_TO_NAME = java.util.Collections.unmodifiableMap(map);
    }

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
                    .baseUrl("https://www.pricesmartfoods.com")
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

        // Get stores for assigning storeId
        Store tntStore = storeRepository.findByCode("TNT").orElse(null);
        Store superstoreStore = storeRepository.findByCode("RCSS").orElse(null);
        Store pricesmartStore = storeRepository.findByCode("PRICESMART").orElse(null);

        for (Category category : categories) {
            boolean updated = false;
            String currentCode = category.getCode();
            String currentName = category.getName();

            // Skip if already has storeId
            if (category.getStoreId() != null) {
                continue;
            }

            // Try to identify store by category code pattern and assign storeId
            if (currentCode != null) {
                // T&T categories: 2876-2881 range
                if (TNT_CATEGORY_ID_TO_NAME.containsKey(currentCode) && tntStore != null) {
                    category.setStoreId(tntStore.getId());
                    // Fix name if needed
                    String properName = TNT_CATEGORY_ID_TO_NAME.get(currentCode);
                    if (properName != null && (currentName == null || currentName.matches("\\d+"))) {
                        category.setName(properName);
                    }
                    updated = true;
                    log.info("Assigned category {} to T&T store", currentCode);
                }
                // Superstore categories: 28xxx range
                else if (SUPERSTORE_CATEGORY_ID_TO_NAME.containsKey(currentCode) && superstoreStore != null) {
                    category.setStoreId(superstoreStore.getId());
                    // Fix name if needed
                    String properName = SUPERSTORE_CATEGORY_ID_TO_NAME.get(currentCode);
                    if (properName != null && (currentName == null || currentName.matches("\\d+"))) {
                        category.setName(properName);
                    }
                    updated = true;
                    log.info("Assigned category {} to Superstore", currentCode);
                }
                // PriceSmart categories: 30xxx range
                else if (PRICESMART_CATEGORY_ID_TO_NAME.containsKey(currentCode) && pricesmartStore != null) {
                    category.setStoreId(pricesmartStore.getId());
                    // Fix name if needed
                    String properName = PRICESMART_CATEGORY_ID_TO_NAME.get(currentCode);
                    if (properName != null && (currentName == null || currentName.matches("\\d+"))) {
                        category.setName(properName);
                    }
                    updated = true;
                    log.info("Assigned category {} to PriceSmart", currentCode);
                }
            }

            // Handle T&T name-based codes (like "seafood" instead of "2880")
            if (!updated && currentCode != null && !currentCode.matches("\\d+")) {
                String properId = TNT_CATEGORY_NAME_TO_ID.get(currentCode.toLowerCase());
                if (properId != null && tntStore != null) {
                    log.info("Fixing category code: {} -> {}", currentCode, properId);
                    category.setCode(properId);
                    category.setStoreId(tntStore.getId());
                    String properName = TNT_CATEGORY_ID_TO_NAME.get(properId);
                    if (properName != null) {
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
