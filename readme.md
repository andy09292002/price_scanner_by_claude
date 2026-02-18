This project is an experiment of how to use Clauda code to generate runnable code for a porject.
- Project objective: Dont code any line of program to complete a price scanning programe.
- Only provide Claude.md and providing instructions for refining the program.
- Program should be runnable and deployable with quality which fulfills real world requirements, such as security, scalability, and maintainability.

## First Iteration

### Selenium Removal
- Removed Selenium and WebDriverManager dependencies for lightweight server deployment
- Replaced with JSoup HTTP client and direct API calls
- **TntScraper**: Now uses T&T's GraphQL API (`/graphql`) for fetching products
- **WalmartScraper**: Uses JSoup with embedded JSON extraction and HTML parsing fallback

### T&T Scraper Fixes
- Discovered correct GraphQL API endpoint for T&T Supermarket
- Configured correct category IDs:
  | ID | Category |
  |----|----------|
  | 2876 | Bakery |
  | 2877 | Fruits |
  | 2878 | Vegetables |
  | 2879 | Meat |
  | 2880 | Seafood |
  | 2881 | Dairy & Eggs |

### Product Size & Unit Extraction
- Fixed size extraction to return only numeric value (e.g., "440" instead of "440g")
- Added unit extraction as separate field (e.g., "g", "kg", "lb")
- Expanded supported units: g, gm, grams, kg, lb, lbs, oz, ml, l, litre, pack, pcs, each, etc.

### Data Initialization & Auto-Fix
- Added `DataInitializer` to seed store data on startup
- Auto-fixes categories: updates `name` field while preserving original `code` (e.g., code="2880", name="Seafood")
- Auto-fixes products with missing/incorrect size and unit fields

### Category Structure
- `code`: Original category ID from the store (e.g., "2880")
- `name`: Human-readable category name (e.g., "Seafood")
- TntScraper passes category as "code:name" format for proper storage
- Auto-fix handles two cases:
  - Name is numeric (name="2880") → Fixed to name="Seafood", code="2880"
  - Code is normalized name (code="seafood") → Fixed to code="2880", name="Seafood"

### Database Configuration
- MongoDB connection configured via `.env` file
- Supports authentication with `authSource` parameter

## Second Iteration

### Superstore Scraper Fixes
- Fixed API response parsing path: `layout > sections > mainContentCollection > components`
- Filter components by `componentId: "productCarouselComponent"` and extract from `productTiles` array
- Updated field mappings for new API structure:
  | Old Field | New Field |
  |-----------|-----------|
  | `name` | `title` |
  | `imageAssets` | `productImage` |
  | `prices` | `pricing` |
  | `stockStatus` | `inventoryIndicator` |
  | `packageSize` | `packageSizing` |
- Added helper methods: `getBigDecimalFromString()`, `extractUnitPrice()`

### Size & Unit Extraction Improvements
- Fixed regex to handle non-breaking spaces (`\u00A0`) between size and unit (e.g., "907 g")
- Changed unit extraction priority: extract from `packageSizing`/name first, fall back to `pricingUnits.unit` only if not found
- This ensures weight units (g, kg, lb) are preferred over generic "ea" unit

### Product Update Logic
- Modified `ProductMatchingService.updateProductIfNeeded()` to always update size/unit when new valid data differs from existing values
- Previously only updated if fields were null/empty

### Configuration
- Moved Superstore API key from hardcoded value to `.env` file
- Add `SUPERSTORE_API_KEY` to your `.env` file

## Third Iteration

### Database Schema Refactoring
- Simplified Product-Category relationship from many-to-many to one-to-many
- Replaced `Map<String, String> storeCategoryIds` with simple `String categoryId` in Product entity
- Removed helper methods `getCategoryId(storeId)` and `setCategoryId(storeId, categoryId)`

### New Table Relationships
```
stores._id      →  categories.storeId
categories._id  →  products.categoryId
products._id    →  price_records.productId
```

### Files Changed
| File | Change |
|------|--------|
| `Product.java` | `storeCategoryIds` (Map) → `categoryId` (String) |
| `ProductRepository.java` | Simplified queries: `findByCategoryId()` |
| `ProductMatchingService.java` | Updated product creation and update logic |
| `ProductController.java` | Removed store-specific category endpoint |
| `TelegramNotificationService.java` | Updated category filter logic |

### PriceSmart Scraper
- Verified working with Playwright browser automation
- Scrapes products from configured category URLs

## Fourth Iteration

### Walmart Scraper Overhaul
- Rewrote URL structure to use Walmart Canada's new `/en/browse/` format with 13-digit category IDs
- Added 45+ granular category URLs covering:
  - Fruits & Vegetables (Fresh Fruits, Fresh Vegetables)
  - Meat & Seafood (Chicken & Turkey, Beef, Pork, Fish & Seafood)
  - Dairy & Eggs (Milk, Yogurt, Butter & Margarine, Eggs)
  - Frozen (Meals & Sides, Vegetables)
  - Pantry (Cereal & Breakfast, Canned Food)
  - Bakery (Sliced Bread)
- Added `CATEGORY_NAMES` static map — maps 13-digit Walmart category IDs to human-readable names
- Added `CATEGORY_ID_PATTERN` regex to extract category ID from URL path
- Improved price parsing with dedicated patterns:
  - `WAS_PRICE_PATTERN` — detects "Was $X.XX" strikethrough prices
  - `NOW_PRICE_PATTERN` — detects "Now $X.XX" sale prices
  - `CENTS_PRICE_PATTERN` — handles cent-format prices (e.g., "97¢") and converts to dollar value
- Added randomized request delays (`ThreadLocalRandom`) to avoid rate limiting
- Enhanced `__NEXT_DATA__` JSON extraction with deeper nested product parsing
- Added `parseProductFromHtml()` with improved CSS selectors for product cards
- Handles both `$X.XX` and `XX¢` price display formats

### Discount Report API
- New DTOs in `PriceAnalysisService`:
  - `DiscountedItem` — full discount info with product, store, prices, and promo description
  - `DiscountedItemDetail` — discount info without store (used within store groups)
  - `StoreDiscountGroup` — groups items by store with item count
- New service methods:
  - `getAllDiscountedItemsGroupedByStore()` — fetches on-sale items from last 24hrs, filters by min discount %, groups by store
  - `getAllDiscountedItems()` — flat list sorted by discount %, with limit
  - `getDiscountReportGroupedByStore()` — optimized grouping for report generation
- New endpoints in `ReportController`:
  - `GET /api/reports/discounts` — returns all discounted items grouped by store
  - `POST /api/reports/discounts/telegram` — generates report and sends to all active Telegram subscribers
- Parameters:
  - `minDiscountPercentage` (default: 10) — minimum discount % to include
- Response DTO: `DiscountReportResponse` (success, message, totalItems, storeCount, subscribersSent)

### Telegram Discount Report
- Added `sendDiscountReport()` to `TelegramNotificationService`
- HTML-formatted message with store headers, item details, and discount percentages
- Shows top 10 items per store with "... and X more items" overflow
- Strikethrough original price with bold sale price (e.g., ~~$5.99~~ → **$3.99** (-33%))
- Includes promo descriptions when available
- Auto-splits messages exceeding Telegram's 4096 character limit (breaks at newlines)

## Fifth Iteration

### PDF Discount Report
- Added Apache PDFBox 3.0.1 dependency for PDF generation
- New `ReportGenerationService` builds downloadable PDF reports from discount data
- New endpoint: `GET /api/reports/sales/pdf`
  - Query parameters: `store`, `category`, `minDiscountPercentage` (default: 10)
  - Returns `application/pdf` with `Content-Disposition: attachment` header
- PDF report contents:
  - **Header**: report title, generation date, applied filters, summary stats (total items, store count, total potential savings)
  - **Store sections**: store name with item count, followed by product rows
  - **Product rows**: thumbnail image (40x40pt), product name/brand/size, regular price, sale price, discount %, savings amount, promo description
- Image handling: fetches product images via HTTP with 3s connect / 5s read timeouts; renders `[No Image]` placeholder on failure
- Automatic page breaks when content overflows US Letter page
- Category filtering via `CategoryRepository.findByNameIgnoreCase()`
- Non-ASCII character sanitization for Helvetica font compatibility
- Unit tests: 8 service tests + 3 controller tests covering happy path, empty data, filters, null images, and multi-page generation
