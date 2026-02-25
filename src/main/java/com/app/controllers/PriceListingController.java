package com.app.controllers;

import com.app.services.PriceAnalysisService;
import com.app.services.PriceAnalysisService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Listing", description = "Product price listing APIs")
public class PriceListingController {

    private final PriceAnalysisService priceAnalysisService;

    private static final List<Integer> VALID_PRICE_DROP_DAYS = List.of(7, 30);
    private static final List<Integer> VALID_PAGE_SIZES = List.of(10, 25, 50);

    @GetMapping("/listing")
    @Operation(summary = "Get product listing grouped by store or category")
    public ResponseEntity<?> getListing(
            @RequestParam(required = false) String storeIds,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(defaultValue = "false") boolean onSaleOnly,
            @RequestParam(defaultValue = "store") String groupBy,
            @RequestParam(required = false) Integer priceDropDays) {

        if (priceDropDays != null && !VALID_PRICE_DROP_DAYS.contains(priceDropDays)) {
            return ResponseEntity.badRequest().body("priceDropDays must be 7 or 30");
        }

        log.info("Getting product listing: storeIds={}, categoryIds={}, onSaleOnly={}, groupBy={}, priceDropDays={}",
                storeIds, categoryIds, onSaleOnly, groupBy, priceDropDays);

        List<String> storeIdList = parseCommaSeparated(storeIds);
        List<String> categoryIdList = parseCommaSeparated(categoryIds);

        if ("category".equalsIgnoreCase(groupBy)) {
            CategoryListingResponse response = priceAnalysisService
                    .getProductListingGroupedByCategory(storeIdList, categoryIdList, onSaleOnly, priceDropDays);
            return ResponseEntity.ok(response);
        }

        ProductListingResponse response = priceAnalysisService
                .getProductListingGroupedByStore(storeIdList, categoryIdList, onSaleOnly, priceDropDays);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/listing/flat")
    @Operation(summary = "Get flat paginated product listing")
    public ResponseEntity<?> getFlatListing(
            @RequestParam(required = false) String storeIds,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(defaultValue = "false") boolean onSaleOnly,
            @RequestParam(required = false) Integer priceDropDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search) {

        if (priceDropDays != null && !VALID_PRICE_DROP_DAYS.contains(priceDropDays)) {
            return ResponseEntity.badRequest().body("priceDropDays must be 7 or 30");
        }
        if (!VALID_PAGE_SIZES.contains(size)) {
            return ResponseEntity.badRequest().body("size must be 10, 25, or 50");
        }
        if (page < 0) {
            return ResponseEntity.badRequest().body("page must be >= 0");
        }

        log.info("Getting flat product listing: page={}, size={}, sortBy={}, sortDir={}, search={}",
                page, size, sortBy, sortDir, search);

        List<String> storeIdList = parseCommaSeparated(storeIds);
        List<String> categoryIdList = parseCommaSeparated(categoryIds);

        PriceAnalysisService.FlatListingResponse response = priceAnalysisService.getFlatProductListing(
                storeIdList, categoryIdList, onSaleOnly, priceDropDays,
                page, size, sortBy, sortDir, search);
        return ResponseEntity.ok(response);
    }

    static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
