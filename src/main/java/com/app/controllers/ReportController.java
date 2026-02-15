package com.app.controllers;

import com.app.models.PriceRecord;
import com.app.services.PriceAnalysisService;
import com.app.services.TelegramNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Endpoints for price analysis and reports")
public class ReportController {

    private final PriceAnalysisService priceAnalysisService;
    private final TelegramNotificationService telegramNotificationService;

    @GetMapping("/price-drops")
    @Operation(summary = "Get items with biggest price reductions",
               description = "Returns products with the largest price drops in the last 24 hours.")
    public ResponseEntity<List<PriceAnalysisService.PriceDrop>> getPriceDrops(
            @Parameter(description = "Minimum drop percentage to include")
            @RequestParam(defaultValue = "10") int minDropPercentage,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "50") int limit) {

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
            @PathVariable String productId) {

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
            @PathVariable String productId,
            @Parameter(description = "Store ID")
            @RequestParam String storeId,
            @Parameter(description = "Number of days of history")
            @RequestParam(defaultValue = "30") int days) {

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
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Getting current sales, page {} size {}", page, size);
        Page<PriceRecord> sales = priceAnalysisService.getCurrentSales(PageRequest.of(page, size));
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/sales/{storeCode}")
    @Operation(summary = "Get current sales for a specific store",
               description = "Returns products currently on sale at a specific store.")
    public ResponseEntity<List<PriceRecord>> getCurrentSalesForStore(
            @Parameter(description = "Store code (RCSS, WALMART, PRICESMART, TNT)")
            @PathVariable String storeCode,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "50") int limit) {

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
            @RequestParam(defaultValue = "10") int minDiscountPercentage) {

        log.debug("Getting discounted items with min {}% discount", minDiscountPercentage);
        Map<String, PriceAnalysisService.StoreDiscountGroup> discounts =
                priceAnalysisService.getDiscountReportGroupedByStore(minDiscountPercentage);
        return ResponseEntity.ok(discounts);
    }

    @PostMapping("/discounts/telegram")
    @Operation(summary = "Send discount report to Telegram",
               description = "Generates a discount report and sends it to all active Telegram subscribers.")
    public ResponseEntity<DiscountReportResponse> sendDiscountReportToTelegram(
            @Parameter(description = "Minimum discount percentage to include")
            @RequestParam(defaultValue = "10") int minDiscountPercentage) {

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

    public record DiscountReportResponse(
            boolean success,
            String message,
            int totalItems,
            int storeCount,
            int subscribersSent
    ) {}
}
