This project is an experiment of how to use Claude Code to generate runnable code for a project.
- Project objective: Don't code any line of program to complete a price scanning program.
- Only provide Claude.md and providing instructions for refining the program.
- Program should be runnable and deployable with quality which fulfills
- real world requirements, such as security, scalability, and maintainability.

## Features

- **Multi-store price scraping** — Walmart Canada, T&T Supermarket, Real Canadian Superstore, PriceSmart Foods
- **Price tracking** — Historical price records with price drop detection
- **Discount reports** — Current sales grouped by store, with PDF export
- **Telegram bot** — Subscribe to deal notifications, filter by store/category
- **Price listing page** — JSP-based UI with Chart.js price history charts
- **Scheduled scraping** — Configurable daily cron schedule (default: 9 AM)
- **REST API** — Full CRUD with Swagger/OpenAPI documentation

## Tech Stack

- Java 17, Spring Boot, Spring MVC
- MongoDB with Spring Data
- Playwright (PriceSmart browser automation)
- Apache PDFBox (PDF report generation)
- Chart.js (price history charts)
- Resilience4j (rate limiting)
- Lombok, Swagger/OpenAPI
- Maven, JUnit

## Deployment (Docker)

### Prerequisites

- Docker and Docker Compose installed
- A Telegram bot token (from [@BotFather](https://t.me/BotFather))

### Quick Start

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd price_scanner_by_claude
   ```

2. Create your `.env` file from the example:
   ```bash
   cp .env.example .env
   ```

3. Edit `.env` with your actual values:
   ```env
   MONGO_USERNAME=admin
   MONGO_PASSWORD=changeme
   MONGODB_DATABASE=mango
   TELEGRAM_BOT_TOKEN=your-telegram-bot-token
   TELEGRAM_BOT_USERNAME=YourBotUsername
   SUPERSTORE_API_KEY=your-superstore-api-key
   ```

4. Build and start the containers:
   ```bash
   docker compose up -d --build
   ```

5. Access the application:
   - **App**: http://localhost:8080
   - **Swagger UI**: http://localhost:8080/swagger-ui.html
   - **Price Listing**: http://localhost:8080/prices

### Docker Architecture

| Service | Image | Purpose |
|---------|-------|---------|
| `app` | Custom (multi-stage build) | Spring Boot application with Playwright |
| `mongodb` | `mongo:7` | Database |

- The Dockerfile uses a multi-stage build: Maven builds the WAR, then it runs on Microsoft's Playwright Java image (`mcr.microsoft.com/playwright/java:v1.41.0-jammy`) which includes pre-installed browsers for PriceSmart scraping.
- The WAR is extracted at build time to avoid nested JAR issues with the Playwright driver.
- MongoDB data persists via a Docker volume (`mongo_data`).
- The app container waits for MongoDB to be healthy before starting.

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MONGO_USERNAME` | No | `admin` | MongoDB root username |
| `MONGO_PASSWORD` | No | `changeme` | MongoDB root password |
| `MONGODB_DATABASE` | No | `mango` | Database name |
| `TELEGRAM_BOT_TOKEN` | Yes | — | Telegram bot API token |
| `TELEGRAM_BOT_USERNAME` | Yes | — | Telegram bot username |
| `SUPERSTORE_API_KEY` | No | — | Superstore PC Express API key |
| `SERVER_PORT` | No | `8080` | Host-side port mapping |

### Useful Commands

```bash
# Start services
docker compose up -d --build

# View logs
docker compose logs -f app

# Stop services
docker compose down

# Stop and remove data
docker compose down -v
```

## Local Development

### Prerequisites

- Java 17+
- Maven 3.9+
- MongoDB running locally or via Docker

### Run Locally

```bash
# Start only MongoDB from Docker Compose
docker compose up -d mongodb

# Build and run
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | Search products |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products/categories` | List all categories |
| GET | `/api/products/listing` | Products grouped by store/category |
| GET | `/api/reports/sales` | Current discounted items |
| GET | `/api/reports/sales/pdf` | Download PDF discount report |
| GET | `/api/reports/price-drops` | Recent price drops |
| GET | `/api/reports/history/{id}` | Price history for a product |
| POST | `/api/scrape/{storeId}` | Trigger scrape for a store |
| POST | `/api/scrape/all` | Trigger scrape for all stores |
| POST | `/api/telegram/subscribe` | Subscribe to Telegram notifications |

See Swagger UI at `/swagger-ui.html` for the full API documentation.

## Development Changelog

### First Iteration

#### Selenium Removal
- Removed Selenium and WebDriverManager dependencies for lightweight server deployment
- Replaced with JSoup HTTP client and direct API calls
- **TntScraper**: Now uses T&T's GraphQL API (`/graphql`) for fetching products
- **WalmartScraper**: Uses JSoup with embedded JSON extraction and HTML parsing fallback

#### T&T Scraper Fixes
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

#### Product Size & Unit Extraction
- Fixed size extraction to return only numeric value (e.g., "440" instead of "440g")
- Added unit extraction as separate field (e.g., "g", "kg", "lb")
- Expanded supported units: g, gm, grams, kg, lb, lbs, oz, ml, l, litre, pack, pcs, each, etc.

#### Data Initialization & Auto-Fix
- Added `DataInitializer` to seed store data on startup
- Auto-fixes categories: updates `name` field while preserving original `code` (e.g., code="2880", name="Seafood")
- Auto-fixes products with missing/incorrect size and unit fields

#### Category Structure
- `code`: Original category ID from the store (e.g., "2880")
- `name`: Human-readable category name (e.g., "Seafood")
- TntScraper passes category as "code:name" format for proper storage
- Auto-fix handles two cases:
  - Name is numeric (name="2880") → Fixed to name="Seafood", code="2880"
  - Code is normalized name (code="seafood") → Fixed to code="2880", name="Seafood"

#### Database Configuration
- MongoDB connection configured via `.env` file
- Supports authentication with `authSource` parameter

### Second Iteration

#### Superstore Scraper Fixes
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

#### Size & Unit Extraction Improvements
- Fixed regex to handle non-breaking spaces (`\u00A0`) between size and unit (e.g., "907 g")
- Changed unit extraction priority: extract from `packageSizing`/name first, fall back to `pricingUnits.unit` only if not found
- This ensures weight units (g, kg, lb) are preferred over generic "ea" unit

#### Product Update Logic
- Modified `ProductMatchingService.updateProductIfNeeded()` to always update size/unit when new valid data differs from existing values
- Previously only updated if fields were null/empty

#### Configuration
- Moved Superstore API key from hardcoded value to `.env` file
- Add `SUPERSTORE_API_KEY` to your `.env` file

### Third Iteration

#### Database Schema Refactoring
- Simplified Product-Category relationship from many-to-many to one-to-many
- Replaced `Map<String, String> storeCategoryIds` with simple `String categoryId` in Product entity
- Removed helper methods `getCategoryId(storeId)` and `setCategoryId(storeId, categoryId)`

#### New Table Relationships
```
stores._id      →  categories.storeId
categories._id  →  products.categoryId
products._id    →  price_records.productId
```

#### Files Changed
| File | Change |
|------|--------|
| `Product.java` | `storeCategoryIds` (Map) → `categoryId` (String) |
| `ProductRepository.java` | Simplified queries: `findByCategoryId()` |
| `ProductMatchingService.java` | Updated product creation and update logic |
| `ProductController.java` | Removed store-specific category endpoint |
| `TelegramNotificationService.java` | Updated category filter logic |

#### PriceSmart Scraper
- Verified working with Playwright browser automation
- Scrapes products from configured category URLs

### Fourth Iteration

#### Walmart Scraper Overhaul
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

#### Discount Report API
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

#### Telegram Discount Report
- Added `sendDiscountReport()` to `TelegramNotificationService`
- HTML-formatted message with store headers, item details, and discount percentages
- Shows top 10 items per store with "... and X more items" overflow
- Strikethrough original price with bold sale price (e.g., ~~$5.99~~ → **$3.99** (-33%))
- Includes promo descriptions when available
- Auto-splits messages exceeding Telegram's 4096 character limit (breaks at newlines)

### Fifth Iteration

#### PDF Discount Report
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

### Sixth Iteration

#### Global Exception Handling
- Added centralized `GlobalExceptionHandler` using `@RestControllerAdvice`
- Consistent `ErrorResponse` record for all error responses with fields: `timestamp`, `status`, `error`, `message`, `path`
- Handles the following exception types:
  | Exception | HTTP Status |
  |-----------|-------------|
  | `ResourceNotFoundException` | 404 Not Found |
  | `MethodArgumentNotValidException` | 400 Bad Request |
  | `ConstraintViolationException` | 400 Bad Request |
  | `IllegalArgumentException` | 400 Bad Request |
  | `MissingServletRequestParameterException` | 400 Bad Request |
  | `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed |
  | `ClientAbortException` | (logged, no response) |
  | `Exception` (catch-all) | 500 Internal Server Error |
- Custom `ResourceNotFoundException` replaces inline 404 handling across all controllers

#### Input Validation
- Added Jakarta Bean Validation annotations to controller parameters across `ReportController`, `ProductController`, `UserController`, `ScrapeController`, `TelegramController`
- Validation constraints applied:
  - `@NotBlank` on path variables and required string params (product IDs, store IDs, chat IDs)
  - `@Min` / `@Max` on numeric params (e.g., `minDropPercentage` 0–100, `limit` 1–500, `days` 1–365)
- Controllers annotated with `@Validated` to activate method-level constraint validation

#### PriceSmart Scraper Sale Detection
- Enhanced product parsing to detect promotions and calculate original vs. sale prices
- Badge parsing: scans `[class*=Badge]` and `[class*=PromotionBadge]` elements for promo text
- Supports multiple discount formats:
  - **"SAVE $X.XX"** — back-calculates original price from displayed price + savings
  - **"X% OFF"** — derives original price from displayed price and percentage
  - **"Was" price** — detects strikethrough/multiple price elements and identifies the higher as original
  - **"View Deal"** indicator — flags item as on sale
- Promo description now populated from badge text

#### Unit Tests
- Added `GlobalExceptionHandlerTest` — 146 lines covering all exception handler paths
- Expanded `ReportControllerTest` with validation edge cases for invalid/boundary parameter values
- Expanded `UserControllerTest` with validation tests for blank and invalid input
- Updated `ScrapeControllerTest` assertions for new error response structure

### Seventh Iteration

#### Product Price Listing Page
- New JSP page `/views/price-listing.jsp` with two-level grouping: Store → Category → Products
- Collapsible store sections with product counts
- Toggle between "By Store" and "By Category" grouping modes
- Filters: store dropdown (multi-select), category dropdown (multi-select), on-sale only checkbox
- Search bar to filter products by name within the listing
- Sorting options: by name, by price (low-high / high-low), by discount %
- Historical price line chart (Chart.js) — clicking a product row opens a modal with daily price history
  - Regular price and sale price as separate lines
  - Configurable date range: 7, 30, 90 days
  - Min/max/average price stats below the chart
- New endpoint: `GET /api/products/listing` — returns products grouped by store and category with current price
- New `PriceListingController` with corresponding service methods and DTOs

#### Scheduled Scraping
- Added `@EnableScheduling` via `SchedulingConfig` configuration class
- New `ScrapeScheduler` component runs `triggerScrapeAll()` on a cron schedule
- Default schedule: daily at 9:00 AM (server timezone)
- Cron expression configurable via `scraper.schedule.cron` in `application.properties`
- Overlapping runs prevented by existing job-level checks in `ScrapeOrchestrationService`
- Logs scrape start, job count, and any failures

#### Unit Tests
- Added `ScrapeSchedulerTest` — 3 tests: normal execution, empty result, exception handling
- Added `PriceListingControllerTest` with filtering, sorting, and grouping tests
- Added `ProductMatchingServiceTest` fixes for updated API
- Added `PriceAnalysisServiceTest` edge case tests
- Added scraper tests: `AbstractStoreScraperTest`, `WalmartScraperTest`, `TntScraperTest`, `SuperstoreScraperTest`
- Added `DataInitializerTest` — 8 tests for store seeding and auto-fix logic

### Eighth Iteration

#### Docker & Deployment
- Migrated from JAR to WAR packaging for servlet container deployment
- Multi-stage Dockerfile: Maven build stage + Playwright's official Java image runtime
- WAR extracted at build time to avoid nested JAR issues with Playwright driver
- Docker Compose with MongoDB 7, health checks, and volume persistence
- Non-root user in container for security
- Added `.dockerignore` and `.env.example` for deployment configuration
