package com.app.services;

import com.app.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAnalysisService {

    private final PriceRecordRepository priceRecordRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public record PriceDrop(
            Product product,
            Store store,
            BigDecimal previousPrice,
            BigDecimal currentPrice,
            BigDecimal dropAmount,
            double dropPercentage,
            LocalDateTime detectedAt
    ) {}

    public record PriceComparison(
            Product product,
            Map<String, StorePrice> storePrices,
            String lowestPriceStore,
            BigDecimal lowestPrice
    ) {}

    public record StorePrice(
            Store store,
            BigDecimal price,
            boolean onSale,
            String promoDescription,
            LocalDateTime lastUpdated
    ) {}

    public record PriceHistory(
            Product product,
            Store store,
            List<PricePoint> pricePoints
    ) {}

    public record PricePoint(
            BigDecimal price,
            boolean onSale,
            LocalDateTime timestamp
    ) {}

    public record DiscountedItem(
            Product product,
            Store store,
            BigDecimal regularPrice,
            BigDecimal salePrice,
            BigDecimal discountAmount,
            double discountPercentage,
            String promoDescription,
            LocalDateTime scrapedAt
    ) {}

    public record DiscountedItemDetail(
            Product product,
            BigDecimal regularPrice,
            BigDecimal salePrice,
            BigDecimal discountAmount,
            double discountPercentage,
            String promoDescription,
            LocalDateTime scrapedAt
    ) {}

    public record StoreDiscountGroup(
            Store store,
            int itemCount,
            List<DiscountedItemDetail> items
    ) {}

    public List<PriceDrop> detectPriceDrops(String storeId, List<PriceRecord> previousPrices) {
        List<PriceDrop> drops = new ArrayList<>();

        // Get current prices (scraped in last hour)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<PriceRecord> currentPrices = priceRecordRepository.findByStoreIdAndScrapedAtAfter(storeId, oneHourAgo);

        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            return drops;
        }

        // Build map of previous prices by product ID
        Map<String, BigDecimal> previousPriceMap = new HashMap<>();
        for (PriceRecord record : previousPrices) {
            // Use the lowest previous price for each product
            BigDecimal effectivePrice = record.isOnSale() && record.getSalePrice() != null
                    ? record.getSalePrice() : record.getRegularPrice();
            if (effectivePrice != null) {
                previousPriceMap.merge(record.getProductId(), effectivePrice, BigDecimal::min);
            }
        }

        for (PriceRecord current : currentPrices) {
            BigDecimal currentEffectivePrice = current.isOnSale() && current.getSalePrice() != null
                    ? current.getSalePrice() : current.getRegularPrice();

            if (currentEffectivePrice == null) {
                continue;
            }

            BigDecimal previousPrice = previousPriceMap.get(current.getProductId());
            if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Check if price dropped
            if (currentEffectivePrice.compareTo(previousPrice) < 0) {
                BigDecimal dropAmount = previousPrice.subtract(currentEffectivePrice);
                double dropPercentage = dropAmount.divide(previousPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

                Product product = productRepository.findById(current.getProductId()).orElse(null);
                if (product != null) {
                    drops.add(new PriceDrop(
                            product,
                            store,
                            previousPrice,
                            currentEffectivePrice,
                            dropAmount,
                            dropPercentage,
                            LocalDateTime.now()
                    ));
                }
            }
        }

        // Sort by drop percentage descending
        drops.sort((a, b) -> Double.compare(b.dropPercentage(), a.dropPercentage()));

        return drops;
    }

    public List<PriceDrop> getRecentPriceDrops(int minDropPercentage, int limit) {
        List<PriceDrop> allDrops = new ArrayList<>();

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);

        List<Store> stores = storeRepository.findByActiveTrue();

        for (Store store : stores) {
            List<PriceRecord> previousPrices = priceRecordRepository
                    .findByStoreIdAndScrapedAtAfter(store.getId(), twoDaysAgo)
                    .stream()
                    .filter(r -> r.getScrapedAt().isBefore(oneDayAgo))
                    .collect(Collectors.toList());

            List<PriceDrop> storeDrops = detectPriceDrops(store.getId(), previousPrices);
            allDrops.addAll(storeDrops);
        }

        return allDrops.stream()
                .filter(drop -> drop.dropPercentage() >= minDropPercentage)
                .sorted((a, b) -> Double.compare(b.dropPercentage(), a.dropPercentage()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public PriceComparison compareProductPrices(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Map<String, StorePrice> storePrices = new HashMap<>();
        String lowestPriceStore = null;
        BigDecimal lowestPrice = null;

        List<Store> stores = storeRepository.findByActiveTrue();

        for (Store store : stores) {
            Optional<PriceRecord> latestPrice = priceRecordRepository
                    .findTopByProductIdAndStoreIdOrderByScrapedAtDesc(productId, store.getId());

            if (latestPrice.isPresent()) {
                PriceRecord record = latestPrice.get();
                BigDecimal effectivePrice = record.isOnSale() && record.getSalePrice() != null
                        ? record.getSalePrice() : record.getRegularPrice();

                if (effectivePrice != null) {
                    StorePrice storePrice = new StorePrice(
                            store,
                            effectivePrice,
                            record.isOnSale(),
                            record.getPromoDescription(),
                            record.getScrapedAt()
                    );
                    storePrices.put(store.getCode(), storePrice);

                    if (lowestPrice == null || effectivePrice.compareTo(lowestPrice) < 0) {
                        lowestPrice = effectivePrice;
                        lowestPriceStore = store.getCode();
                    }
                }
            }
        }

        return new PriceComparison(product, storePrices, lowestPriceStore, lowestPrice);
    }

    public PriceHistory getProductPriceHistory(String productId, String storeId, int days) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<PriceRecord> records = priceRecordRepository
                .findByProductIdAndScrapedAtBetween(productId, startDate, LocalDateTime.now());

        // Filter by store if specified
        List<PriceRecord> filteredRecords = records.stream()
                .filter(r -> r.getStoreId().equals(storeId))
                .sorted(Comparator.comparing(PriceRecord::getScrapedAt))
                .collect(Collectors.toList());

        List<PricePoint> pricePoints = filteredRecords.stream()
                .map(r -> new PricePoint(
                        r.isOnSale() && r.getSalePrice() != null ? r.getSalePrice() : r.getRegularPrice(),
                        r.isOnSale(),
                        r.getScrapedAt()
                ))
                .collect(Collectors.toList());

        return new PriceHistory(product, store, pricePoints);
    }

    public Page<PriceRecord> getCurrentSales(Pageable pageable) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        return priceRecordRepository.findCurrentSales(oneDayAgo, pageable);
    }

    public List<PriceRecord> getCurrentSalesForStore(String storeCode, int limit) {
        Store store = storeRepository.findByCode(storeCode)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeCode));

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        return priceRecordRepository.findByStoreIdAndScrapedAtAfter(store.getId(), oneDayAgo)
                .stream()
                .filter(PriceRecord::isOnSale)
                .sorted((a, b) -> {
                    // Sort by discount percentage
                    BigDecimal discountA = calculateDiscountPercentage(a);
                    BigDecimal discountB = calculateDiscountPercentage(b);
                    return discountB.compareTo(discountA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateDiscountPercentage(PriceRecord record) {
        if (record.getRegularPrice() == null || record.getSalePrice() == null ||
            record.getRegularPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return record.getRegularPrice().subtract(record.getSalePrice())
                .divide(record.getRegularPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public Map<String, List<DiscountedItem>> getAllDiscountedItemsGroupedByStore(int minDiscountPercentage) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        List<Store> stores = storeRepository.findByActiveTrue();
        Map<String, Store> storeMap = stores.stream()
                .collect(Collectors.toMap(Store::getId, s -> s));

        // Get all recent sales
        List<PriceRecord> allSales = priceRecordRepository.findByScrapedAtAfter(oneDayAgo)
                .stream()
                .filter(PriceRecord::isOnSale)
                .filter(r -> r.getRegularPrice() != null && r.getSalePrice() != null)
                .collect(Collectors.toList());

        // Group by productId+storeId to get unique latest records
        Map<String, PriceRecord> latestRecords = new HashMap<>();
        for (PriceRecord record : allSales) {
            String key = record.getProductId() + "_" + record.getStoreId();
            PriceRecord existing = latestRecords.get(key);
            if (existing == null || record.getScrapedAt().isAfter(existing.getScrapedAt())) {
                latestRecords.put(key, record);
            }
        }

        // Convert to DiscountedItem and group by store
        Map<String, List<DiscountedItem>> result = new LinkedHashMap<>();

        for (PriceRecord record : latestRecords.values()) {
            BigDecimal discountPct = calculateDiscountPercentage(record);
            if (discountPct.doubleValue() < minDiscountPercentage) {
                continue;
            }

            Product product = productRepository.findById(record.getProductId()).orElse(null);
            Store store = storeMap.get(record.getStoreId());

            if (product == null || store == null) {
                continue;
            }

            BigDecimal discountAmount = record.getRegularPrice().subtract(record.getSalePrice());

            DiscountedItem item = new DiscountedItem(
                    product,
                    store,
                    record.getRegularPrice(),
                    record.getSalePrice(),
                    discountAmount,
                    discountPct.doubleValue(),
                    record.getPromoDescription(),
                    record.getScrapedAt()
            );

            result.computeIfAbsent(store.getName(), k -> new ArrayList<>()).add(item);
        }

        // Sort items within each store by discount percentage descending
        for (List<DiscountedItem> items : result.values()) {
            items.sort((a, b) -> Double.compare(b.discountPercentage(), a.discountPercentage()));
        }

        return result;
    }

    public List<DiscountedItem> getAllDiscountedItems(int minDiscountPercentage, int limit) {
        Map<String, List<DiscountedItem>> grouped = getAllDiscountedItemsGroupedByStore(minDiscountPercentage);

        return grouped.values().stream()
                .flatMap(List::stream)
                .sorted((a, b) -> Double.compare(b.discountPercentage(), a.discountPercentage()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, StoreDiscountGroup> getDiscountReportGroupedByStore(int minDiscountPercentage) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        List<Store> stores = storeRepository.findByActiveTrue();
        Map<String, Store> storeMap = stores.stream()
                .collect(Collectors.toMap(Store::getId, s -> s));

        // Get all recent sales
        List<PriceRecord> allSales = priceRecordRepository.findByScrapedAtAfter(oneDayAgo)
                .stream()
                .filter(PriceRecord::isOnSale)
                .filter(r -> r.getRegularPrice() != null && r.getSalePrice() != null)
                .collect(Collectors.toList());

        // Group by productId+storeId to get unique latest records
        Map<String, PriceRecord> latestRecords = new HashMap<>();
        for (PriceRecord record : allSales) {
            String key = record.getProductId() + "_" + record.getStoreId();
            PriceRecord existing = latestRecords.get(key);
            if (existing == null || record.getScrapedAt().isAfter(existing.getScrapedAt())) {
                latestRecords.put(key, record);
            }
        }

        // Group by store
        Map<String, List<PriceRecord>> recordsByStore = new HashMap<>();
        for (PriceRecord record : latestRecords.values()) {
            BigDecimal discountPct = calculateDiscountPercentage(record);
            if (discountPct.doubleValue() < minDiscountPercentage) {
                continue;
            }
            recordsByStore.computeIfAbsent(record.getStoreId(), k -> new ArrayList<>()).add(record);
        }

        // Build result
        Map<String, StoreDiscountGroup> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<PriceRecord>> entry : recordsByStore.entrySet()) {
            Store store = storeMap.get(entry.getKey());
            if (store == null) continue;

            List<DiscountedItemDetail> items = new ArrayList<>();
            for (PriceRecord record : entry.getValue()) {
                Product product = productRepository.findById(record.getProductId()).orElse(null);
                if (product == null) continue;

                BigDecimal discountPct = calculateDiscountPercentage(record);
                BigDecimal discountAmount = record.getRegularPrice().subtract(record.getSalePrice());

                items.add(new DiscountedItemDetail(
                        product,
                        record.getRegularPrice(),
                        record.getSalePrice(),
                        discountAmount,
                        discountPct.doubleValue(),
                        record.getPromoDescription(),
                        record.getScrapedAt()
                ));
            }

            // Sort by discount percentage descending
            items.sort((a, b) -> Double.compare(b.discountPercentage(), a.discountPercentage()));

            result.put(store.getName(), new StoreDiscountGroup(store, items.size(), items));
        }

        return result;
    }
}
