# PDF Discount Report Generation

## API Endpoint

```
GET /api/reports/sales/pdf
```

### Query Parameters

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `store` | String | _(none)_ | - | Filter by store code (e.g., `WALMART`, `RCSS`, `PRICESMART`, `TNT`) |
| `category` | String | _(none)_ | - | Filter by category name (e.g., `produce`, `dairy`) |
| `minDiscountPercentage` | int | `10` | 0-100 | Minimum discount % to include an item |
| `lookbackDays` | int | `7` | 1-30 | How many days back to search for scraped data |
| `includeImages` | boolean | `true` | - | Whether to fetch and render product images |
| `itemsPerStore` | int | `10` | 0-500 | Max items shown per store (0 = unlimited) |

### Response

- Content-Type: `application/pdf`
- Filename: `discount-report-YYYY-MM-DD.pdf`

---

## Data Flow

```
Browser Request
    |
    v
ReportController.downloadDiscountReportPdf()
    |
    |-- 1. PriceAnalysisService.getDiscountReportGroupedByStore(minDiscount, lookbackDays)
    |       |
    |       |-- Queries PriceRecordRepository.findByScrapedAtAfter(cutoff)
    |       |-- Filters: onSale == true, regularPrice != null, salePrice != null
    |       |-- Deduplicates by productId + storeId (keeps latest)
    |       |-- Calculates discount % = (regular - sale) / regular * 100
    |       |-- Filters by minDiscountPercentage
    |       |-- Groups by store, sorts items by discount % descending
    |       |-- Returns Map<storeName, StoreDiscountGroup>
    |
    |-- 2. Apply store filter (if provided)
    |       Keeps only entries matching store code
    |
    |-- 3. Apply itemsPerStore limit
    |       Truncates each store's item list (already sorted by highest discount)
    |
    |-- 4. ReportGenerationService.generateDiscountReportPdf()
    |       |
    |       |-- Apply category filter (if provided)
    |       |-- Render PDF using Apache PDFBox
    |       |-- Return byte[]
    |
    v
ResponseEntity<byte[]> with PDF content
```

---

## PDF Layout (ReportGenerationService)

Uses **Apache PDFBox** with `PDRectangle.LETTER` (8.5 x 11 inches).

### Page Constants

| Constant | Value | Description |
|----------|-------|-------------|
| MARGIN | 50pt | All sides |
| LINE_HEIGHT | 14pt | Text line spacing |
| SECTION_GAP | 20pt | Gap between store sections |
| IMAGE_SIZE | 40pt | Product thumbnail dimensions |
| ROW_HEIGHT | 50pt | Height per product row |

### Structure

```
+--------------------------------------------------+
|  Discount Report                    (18pt bold)   |
|  Generated: 2026-02-21              (10pt)        |
|  Filters: min 10% off              (10pt)        |
|  Total: 40 items across 4 stores   (10pt bold)   |
|  ============================================     |
|                                                   |
|  Walmart (WALMART) - 10 items on sale  (13pt)     |
|  --------------------------------------------     |
|  [img] Product   Regular  Sale  Discount Savings  |
|  [img] Product   Regular  Sale  Discount Savings  |
|  ...                                              |
|                                                   |
|  Superstore (RCSS) - 10 items on sale  (13pt)     |
|  --------------------------------------------     |
|  ...                                              |
+--------------------------------------------------+
```

### Header Section (`drawHeader`)

1. Title: "Discount Report" (Helvetica Bold 18pt)
2. Generation date (Helvetica 10pt)
3. Applied filters summary
4. Summary stats: total items, store count, total potential savings
5. Horizontal separator line

### Store Section (per store)

1. Store name with code and item count (Helvetica Bold 13pt)
2. Separator line
3. Column headers: Product, Regular, Sale, Discount, Savings (Bold 8pt)
4. Product rows

### Product Row (`drawProductRow`)

Each row is 50pt tall and contains:

| Column | X Offset | Content |
|--------|----------|---------|
| Image | MARGIN (50) | 40x40 product thumbnail or placeholder |
| Product info | MARGIN + 45 | Name (Bold 9pt), Brand/Size (8pt), Promo (7pt) |
| Regular price | MARGIN + 250 | e.g., "$10.00" (9pt) |
| Sale price | MARGIN + 310 | e.g., "$7.00" (Bold 9pt) |
| Discount | MARGIN + 365 | e.g., "30% off" (Bold 9pt) |
| Savings | MARGIN + 430 | e.g., "-$3.00" (9pt) |

Followed by a light separator line (0.25pt).

### Pagination

- New page is created when remaining space < `MARGIN + ROW_HEIGHT`
- Store headers also trigger a new page if not enough room for header + at least one row

### Image Fetching (`fetchImage`)

- HTTP GET with 1s connect / 2s read timeouts
- Follows redirects
- Converts to PNG via `ImageIO` (required for PDFBox standard font compatibility)
- On failure: draws a placeholder box with "[No Image]" text
- Each failed product row is caught individually and skipped (does not break the PDF)

### Category Filter (`applyCategoryFilter`)

- Looks up `Category` entities by name (case-insensitive)
- Filters items where `product.categoryId` matches any found category ID
- Removes store groups that become empty after filtering

### Text Sanitization (`truncateText`)

- Strips non-ASCII characters (PDFBox standard fonts only support `\x20-\x7E`)
- Truncates with "..." if exceeding max length

---

## Key Data Models

### DiscountedItemDetail (inner record of PriceAnalysisService)

| Field | Type | Description |
|-------|------|-------------|
| product | Product | The product entity |
| regularPrice | BigDecimal | Original price |
| salePrice | BigDecimal | Discounted price |
| discountAmount | BigDecimal | regularPrice - salePrice |
| discountPercentage | double | (discountAmount / regularPrice) * 100 |
| promoDescription | String | Promo text from scraper (e.g., "SAVE $2.00") |
| scrapedAt | LocalDateTime | When the price was scraped |

### StoreDiscountGroup (inner record of PriceAnalysisService)

| Field | Type | Description |
|-------|------|-------------|
| store | Store | The store entity |
| itemCount | int | Number of discounted items |
| items | List\<DiscountedItemDetail\> | Sorted by discount % descending |

---

## Error Handling

- `ClientAbortException` (browser timeout): logged as warning, no error response attempted
- Individual product row failures: caught and skipped, PDF continues with remaining items
- Empty results: renders a "No discounted items found" message in the PDF
- Image fetch failures: silently falls back to placeholder box
