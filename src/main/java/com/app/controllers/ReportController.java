package com.app.controllers;

import com.app.models.PriceRecord;
import com.app.services.PriceAnalysisService;
import com.app.services.ReportGenerationService;
import com.app.services.TelegramNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reports", description = "Endpoints for price analysis and reports")
public class ReportController {

    private final PriceAnalysisService priceAnalysisService;
    private final TelegramNotificationService telegramNotificationService;
    private final ReportGenerationService reportGenerationService;

    @GetMapping("/price-drops")
    @Operation(summary = "Get items with biggest price reductions",
               description = "Returns products with the largest price drops in the last 24 hours.")
    public ResponseEntity<List<PriceAnalysisService.PriceDrop>> getPriceDrops(
            @Parameter(description = "Minimum drop percentage to include")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "minDropPercentage must be at least 0") @Max(value = 100, message = "minDropPercentage must be at most 100") int minDropPercentage,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 500, message = "Limit must be at most 500") int limit) {

        log.debug("Getting price drops with min {}% drop, limit {}", minDropPercentage, limit);
        List<PriceAnalysisService.PriceDrop> drops =
                priceAnalysisService.getRecentPriceDrops(minDropPercentage, limit);
        return ResponseEntity.ok(drops);
    }

    @GetMapping("/compare/{productId}")
    @Operation(summary = "Compare prices across stores",
               description = "Returns current prices for a product at all tracked stores.")
    public ResponseEntity<PriceAnalysisService.PriceComparison> comparePrices(
            @Parameter(description = "Product ID")
            @PathVariable @NotBlank(message = "Product ID must not be blank") String productId) {

        log.debug("Comparing prices for product: {}", productId);
        PriceAnalysisService.PriceComparison comparison =
                priceAnalysisService.compareProductPrices(productId);
        return ResponseEntity.ok(comparison);
    }

    @GetMapping("/history/{productId}")
    @Operation(summary = "Get price history",
               description = "Returns price history for a product at a specific store.")
    public ResponseEntity<PriceAnalysisService.PriceHistory> getPriceHistory(
            @Parameter(description = "Product ID")
            @PathVariable @NotBlank(message = "Product ID must not be blank") String productId,
            @Parameter(description = "Store ID")
            @RequestParam @NotBlank(message = "Store ID is required") String storeId,
            @Parameter(description = "Number of days of history")
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "Days must be at least 1") @Max(value = 365, message = "Days must be at most 365") int days) {

        log.debug("Getting price history for product {} at store {} for {} days",
                productId, storeId, days);
        PriceAnalysisService.PriceHistory history =
                priceAnalysisService.getProductPriceHistory(productId, storeId, days);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/sales")
    @Operation(summary = "Get current sales/promotions",
               description = "Returns all products currently on sale.")
    public ResponseEntity<Page<PriceRecord>> getCurrentSales(
            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page number must be at least 0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "Page size must be at least 1") @Max(value = 100, message = "Page size must be at most 100") int size) {

        log.debug("Getting current sales, page {} size {}", page, size);
        Page<PriceRecord> sales = priceAnalysisService.getCurrentSales(PageRequest.of(page, size));
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/sales/{storeCode}")
    @Operation(summary = "Get current sales for a specific store",
               description = "Returns products currently on sale at a specific store.")
    public ResponseEntity<List<PriceRecord>> getCurrentSalesForStore(
            @Parameter(description = "Store code (RCSS, WALMART, PRICESMART, TNT)")
            @PathVariable @NotBlank(message = "Store code must not be blank") String storeCode,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 500, message = "Limit must be at most 500") int limit) {

        log.debug("Getting current sales for store {}, limit {}", storeCode, limit);
        List<PriceRecord> sales =
                priceAnalysisService.getCurrentSalesForStore(storeCode.toUpperCase(), limit);
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/discounts")
    @Operation(summary = "Get all discounted items grouped by store",
               description = "Returns all products currently on discount, grouped by store. Store info appears once per group.")
    public ResponseEntity<Map<String, PriceAnalysisService.StoreDiscountGroup>> getDiscountedItems(
            @Parameter(description = "Minimum discount percentage to include")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "minDiscountPercentage must be at least 0") @Max(value = 100, message = "minDiscountPercentage must be at most 100") int minDiscountPercentage,
            @Parameter(description = "Number of days to look back for scraped data")
            @RequestParam(defaultValue = "7") @Min(value = 1, message = "lookbackDays must be at least 1") @Max(value = 30, message = "lookbackDays must be at most 30") int lookbackDays) {

        log.debug("Getting discounted items with min {}% discount, lookback {} days", minDiscountPercentage, lookbackDays);
        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts =
                priceAnalysisService.getDiscountReportGroupedByStore(minDiscountPercentage, lookbackDays);
        return ResponseEntity.ok(discounts);
    }

    @GetMapping("/sales/pdf")
    @Operation(summary = "Download discount report as PDF",
               description = "Generates a PDF report of all discounted products with images, pricing details, and summary stats.")
    public ResponseEntity<byte[]> downloadDiscountReportPdf(
            @Parameter(description = "Filter by store code (e.g., WALMART, RCSS)")
            @RequestParam(required = false) String store,
            @Parameter(description = "Filter by category name (e.g., produce, dairy)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Minimum discount percentage to include")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "minDiscountPercentage must be at least 0") @Max(value = 100, message = "minDiscountPercentage must be at most 100") int minDiscountPercentage,
            @Parameter(description = "Number of days to look back for scraped data")
            @RequestParam(defaultValue = "7") @Min(value = 1, message = "lookbackDays must be at least 1") @Max(value = 30, message = "lookbackDays must be at most 30") int lookbackDays,
            @Parameter(description = "Include product images in PDF (slower)")
            @RequestParam(defaultValue = "true") boolean includeImages,
            @Parameter(description = "Maximum number of items to show per store (0 = unlimited)")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "itemsPerStore must be at least 0") @Max(value = 500, message = "itemsPerStore must be at most 500") int itemsPerStore) throws IOException {

        log.info("Generating PDF discount report - store: {}, category: {}, minDiscount: {}%, lookbackDays: {}, itemsPerStore: {}",
                store, category, minDiscountPercentage, lookbackDays, itemsPerStore);

        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts =
                priceAnalysisService.getDiscountReportGroupedByStore(minDiscountPercentage, lookbackDays);

        int totalItems = discounts.values().stream()
                .mapToInt(PriceAnalysisService.StoreDiscountGroup::itemCount).sum();
        log.info("Found {} discount items across {} stores", totalItems, discounts.size());

        // Apply store filter in controller
        if (store != null && !store.isBlank()) {
            String storeUpper = store.toUpperCase();
            discounts = discounts.entrySet().stream()
                    .filter(e -> e.getValue().store().getCode().equalsIgnoreCase(storeUpper))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        // Limit items per store
        if (itemsPerStore > 0) {
            discounts = limitItemsPerStore(discounts, itemsPerStore);
        }

        byte[] pdfBytes = reportGenerationService.generateDiscountReportPdf(
                discounts, store, category, minDiscountPercentage, includeImages);

        log.info("PDF generated successfully, size: {} bytes", pdfBytes.length);

        String filename = "discount-report-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @PostMapping("/discounts/telegram")
    @Operation(summary = "Send discount report to Telegram",
               description = "Generates a discount report and sends it to all active Telegram subscribers.")
    public ResponseEntity<DiscountReportResponse> sendDiscountReportToTelegram(
            @Parameter(description = "Minimum discount percentage to include")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "minDiscountPercentage must be at least 0") @Max(value = 100, message = "minDiscountPercentage must be at most 100") int minDiscountPercentage) {

        log.info("Generating discount report with min {}% discount and sending to Telegram", minDiscountPercentage);

        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts =
                priceAnalysisService.getDiscountReportGroupedByStore(minDiscountPercentage);

        int totalItems = discounts.values().stream().mapToInt(PriceAnalysisService.StoreDiscountGroup::itemCount).sum();
        int storeCount = discounts.size();
        int subscribersSent = telegramNotificationService.sendDiscountReport(discounts);

        log.info("Discount report sent: {} items from {} stores to {} subscribers",
                totalItems, storeCount, subscribersSent);

        return ResponseEntity.ok(new DiscountReportResponse(
                true,
                String.format("Report sent to %d subscribers", subscribersSent),
                totalItems,
                storeCount,
                subscribersSent
        ));
    }

    private Map<String, PriceAnalysisService.StoreDiscountGroup> limitItemsPerStore(
            Map<String, PriceAnalysisService.StoreDiscountGroup> discounts, int limit) {
        return discounts.entrySet().stream()
                .map(entry -> {
                    PriceAnalysisService.StoreDiscountGroup group = entry.getValue();
                    List<PriceAnalysisService.DiscountedItemDetail> limited = group.items().stream()
                            .limit(limit)
                            .toList();
                    return Map.entry(entry.getKey(),
                            new PriceAnalysisService.StoreDiscountGroup(group.store(), limited.size(), limited));
                })
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public record DiscountReportResponse(
            boolean success,
            String message,
            int totalItems,
            int storeCount,
            int subscribersSent
    ) {}
}
