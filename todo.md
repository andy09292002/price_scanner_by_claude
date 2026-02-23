# Price Scanner - Pending Tasks

## Priority Legend
- **P0** - Critical / Blockers
- **P1** - High priority
- **P2** - Medium priority
- **P3** - Nice to have

---

## P0 - Critical

### 1. Add Spring Security & Authentication
- [ ] Add `spring-boot-starter-security` dependency
- [ ] Implement JWT-based authentication (token generation, validation)
- [ ] Secure all `/api/**` endpoints (require authentication)
- [ ] Keep Swagger UI and health check endpoints public
- [ ] Add role-based access control (ADMIN for scrape triggers, USER for read-only)
- [ ] Protect Telegram bot token and Superstore API key from exposure

**Why:** All endpoints are completely public with zero authentication. Anyone can trigger scrapes, delete users, or send Telegram messages.

### ~~2. Implement Global Exception Handler~~ ✅
- [x] Create `@ControllerAdvice` class (`GlobalExceptionHandler`)
- [x] Define standard error response DTO (`ErrorResponse` with timestamp, status, message, path)
- [x] Handle common exceptions:
  - `ResourceNotFoundException` (404)
  - `MethodArgumentNotValidException` (400)
  - `IllegalArgumentException` (400)
  - `HttpRequestMethodNotSupportedException` (405)
  - `MissingServletRequestParameterException` (400)
  - Generic `Exception` fallback (500)
- [x] Ensure consistent JSON error format across all endpoints
- [x] Log exceptions properly with stack traces at appropriate levels
- [x] Refactor controllers to throw `ResourceNotFoundException` instead of empty 404 responses

**Status:** Completed. All error responses now return consistent JSON with `ErrorResponse` structure.

### ~~3. Add Input Validation~~ ✅
- [x] Add Bean Validation annotations to `User` model (`@NotBlank`, `@Email`, `@Size`)
- [x] Add validation to `TelegramSubscription` model (`@NotNull` on chatId, `@Min`/`@Max` on thresholds)
- [x] Add `@Valid` annotation on all `@RequestBody` parameters across controllers
- [x] Validate path variables and request parameters (`@NotBlank`, `@Min`/`@Max`)
- [x] Add validation to `ProductController` search parameters (page size limits, sort field whitelist)
- [x] Add validation to `ReportController` parameters (minDiscountPercentage range)
- [x] Add `@Validated` to all controllers for parameter-level constraint validation
- [x] Add `ConstraintViolationException` handler to `GlobalExceptionHandler`
- [x] Add 8 new validation test cases to `UserControllerTest`

**Status:** Completed. All models have Bean Validation annotations, all controllers use `@Validated` with parameter constraints, and the global exception handler returns consistent error responses for validation failures.

---

## P1 - High Priority

### ~~4. Increase Unit Test Coverage to 80%~~ 🔄 In Progress
Current coverage: ~48% (up from ~30%). Target: 80% per CLAUDE.md.

**Controller tests:** ✅
- [x] `ProductControllerTest` - 12 tests: search, get by ID, list categories, products by category, sort/pagination
- [x] `TelegramControllerTest` - 9 tests: subscribe, update settings, unsubscribe, test message

**Service tests:** ✅
- [x] `ScrapeOrchestrationServiceTest` - 11 tests: job lifecycle, concurrent job prevention, triggerScrapeAll
- [x] `TelegramNotificationServiceTest` - 19 tests: bot commands, message sending, subscription management, filtering, message splitting
- [x] `PriceAnalysisServiceTest` - added 4 edge case tests (empty records, no records, empty discounts)
- [x] `ProductMatchingServiceTest` - fixed 9 broken tests (updated for `findByStoreIdAndCode` API change)

**Scraper tests:** ✅
- [x] `AbstractStoreScraperTest` - 18 tests: size/unit extraction, price parsing, name normalization
- [x] `WalmartScraperTest` - 11 tests: store code, supports, JSON parsing, price formats
- [x] `TntScraperTest` - 9 tests: store code, supports, product parsing, was_price, out-of-stock
- [x] `SuperstoreScraperTest` - 10 tests: store code, supports, product parsing, sale detection, stock status

**Other:** ✅
- [x] `DataInitializerTest` - 8 tests: store seeding, category auto-fix, product size/unit fix

**Still needed to reach 80%:**
- [ ] Add more tests for `PriceAnalysisService` (largest coverage gap: ~926 instructions missed)
- [ ] Add more tests for `ReportGenerationService` (218 instructions missed)
- [ ] Add more tests for `ScrapeOrchestrationService.executeScrape` private method (134 instructions missed)
- [ ] Add more tests for `TelegramNotificationService` bot commands (229 instructions missed)
- [ ] Add `RateLimiterConfigurationTest`

### ~~5. Add Scheduled Scraping~~ ✅
- [x] Add `@EnableScheduling` to application config (`SchedulingConfig.java`)
- [x] Create `ScrapeScheduler` service with `@Scheduled` method
- [x] Configure cron expression (daily at 9 AM, configurable via `scraper.schedule.cron`)
- [x] Make schedule configurable via `application.properties`
- [x] Overlapping runs already prevented by `ScrapeOrchestrationService.triggerScrape()` (checks for running jobs)
- [x] Log scheduling events (start, completion, failure)

**Status:** Completed. Scheduled scraping runs daily at 9 AM (configurable). Uses existing `triggerScrapeAll()` logic.

### 6. Fix Telegram `/deals` Command
- [ ] Inject `PriceAnalysisService` into `TelegramNotificationService`
- [ ] Fetch actual current deals/price drops when `/deals` is invoked
- [ ] Format deals as a readable Telegram message (store, product, old price, new price, % off)
- [ ] Respect subscriber's store and category filters
- [ ] Handle empty results gracefully
- [ ] Apply the 4096 character message splitting logic

**Why:** Currently returns a hardcoded string: `"Fetching current deals...\n\nVisit /api/reports/price-drops for the latest deals."` — no actual data is fetched.

### ~~7. Fix PriceSmart Data Not Showing in Discount API~~ ✅
- [x] Investigate why PriceSmart scraped data does not appear in `/api/reports/sales` (discount API)
- [x] Check if PriceSmart price records are being stored correctly with proper `scrapedAt` timestamps
- [x] Verify PriceSmart products have correct store association and category mappings
- [x] Ensure discount calculation logic in `PriceAnalysisService` is compatible with PriceSmart data format
- [x] Confirm PriceSmart products have multiple price records (needed for price drop detection)
- [x] Add integration test to verify PriceSmart data flows through to discount API

**Status:** Completed. PriceSmart data now correctly appears in the discount API.

### ~~8. Generate PDF Discount Report~~ ✅
- [x] Add PDF generation library dependency (e.g., iText or Apache PDFBox)
- [x] Create `ReportGenerationService` to build PDF from discount API data
- [x] Include product images in the report (fetch from stored image URLs)
- [x] Display calculated discount rate (percentage off) for each product
- [x] Show original price, sale price, and savings per product
- [x] Group products by store or category for readability
- [x] Add report header with generation date, filters applied, and summary stats
- [x] Create `GET /api/reports/sales/pdf` endpoint to download the PDF report
- [x] Support query parameters (store filter, category filter, minimum discount %)
- [x] Handle missing product images gracefully (placeholder or skip)

**Status:** Completed. PDF report generated using Apache PDFBox 3.0.1 with product images, pricing details, discount percentages, and summary stats. Endpoint: `GET /api/reports/sales/pdf?store=&category=&minDiscountPercentage=10`.

### 9. Scraper Error Resilience
- [ ] Add retry logic with exponential backoff for HTTP failures (network timeouts, 5xx responses)
- [ ] Add circuit breaker pattern per store (Resilience4j `@CircuitBreaker`)
- [ ] Handle store website structure changes gracefully (log warnings, don't crash)
- [ ] Add timeout configuration per scraper (currently hardcoded 30s)
- [ ] Add fallback responses when scraper fails (return partial results instead of nothing)
- [ ] Track and report scraper success/failure rates per store

---

## P2 - Medium Priority

### 10. Docker & Containerization
- [ ] Create `Dockerfile` (multi-stage build: Maven build + JRE runtime)
- [ ] Create `docker-compose.yml` with services:
  - Application (Spring Boot)
  - MongoDB
  - (Optional) Redis for caching
- [ ] Add `.dockerignore` file
- [ ] Document environment variable configuration for Docker
- [ ] Ensure Playwright works in container (install browser dependencies)

### 11. CI/CD Pipeline
- [ ] Create `.github/workflows/ci.yml`:
  - Run on push/PR to main
  - Build with Maven
  - Run unit tests
  - Report test coverage
  - Fail if coverage < 80%
- [ ] (Optional) Add deployment workflow for staging/production
- [ ] Add badge to readme.md for build status

### 12. Health Check & Monitoring
- [ ] Add `spring-boot-starter-actuator` dependency
- [ ] Expose `/actuator/health` endpoint
- [ ] Add custom health indicators:
  - MongoDB connectivity
  - Telegram bot connectivity
  - Last successful scrape per store
- [ ] Add Micrometer metrics for:
  - Scrape job duration and success rate
  - API request latency
  - Product count per store

### 13. API Caching
- [ ] Add caching for frequently accessed endpoints:
  - `GET /api/products` (search results)
  - `GET /api/products/categories` (category list)
  - `GET /api/reports/sales` (current sales)
- [ ] Use Spring `@Cacheable` with TTL configuration
- [ ] Invalidate cache after scrape job completion
- [ ] Consider Redis as cache backend for multi-instance deployments

### 14. Configuration Validation
- [ ] Validate required environment variables on startup (fail fast if missing):
  - `MONGODB_URI`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_BOT_USERNAME`
  - `SUPERSTORE_API_KEY`
- [ ] Remove placeholder defaults (`your-bot-token-here`) from `application.properties`
- [ ] Add `.env.example` file documenting all required variables
- [ ] Ensure `.env` is in `.gitignore`

### 15. Product Deduplication
- [ ] Improve product name normalization (handle brand variations, size formats)
- [ ] Add deduplication job to merge duplicate products
- [ ] Handle cross-store product matching more accurately
- [ ] Add admin endpoint to manually merge/split products

### 16. Historical Data Management
- [ ] Add TTL index on `price_records.scrapedAt` for automatic pruning (e.g., keep 90 days)
- [ ] Create aggregated historical data (weekly/monthly averages) before pruning detailed records
- [ ] Add endpoint for price trend analysis (7-day, 30-day, 90-day)

---

## P3 - Nice to Have

### 17. API Enhancements
- [ ] Add advanced search filters (price range, on-sale only, specific stores)
- [ ] Add user watchlist feature (track specific products, get alerts)
- [ ] Add bulk product query endpoint
- [ ] Add category hierarchy/tree endpoint
- [ ] Add pagination metadata (total count, total pages) to all list endpoints

### 18. Telegram Bot Enhancements
- [ ] Add `/watchlist` command to manage product watchlist
- [ ] Add `/compare` command to compare prices across stores
- [ ] Add inline keyboard for settings (store/category toggles)
- [ ] Add message scheduling (daily/weekly deal summaries)
- [ ] Add timezone support for scheduled messages

### 19. Product Price Listing Page (Grouped by Store/Category with Historical Chart)
Build a web page that displays product prices grouped by store and category, with a line chart showing historical price trends. Prices are loaded daily and do not change within the same day.

~~**Page Layout & Grouping:** ✅~~
- [x] Create JSP page `/views/price-listing.jsp` with two-level grouping: Store → Category → Products
- [x] Add collapsible store sections showing store name and product count
- [x] Within each store, group products by category with category headers
- [x] Display product table per category: product name, brand, size/unit, current price, sale price (if on sale), discount %
- [x] Add toggle to switch grouping mode: "By Store" (Store → Category) vs "By Category" (Category → Store)
- [x] Add filters: store dropdown (multi-select), category dropdown (multi-select), on-sale only checkbox

~~**Historical Price Line Chart:**~~ ✅
- [x] Integrate a charting library (Chart.js via CDN) for price history visualization
- [x] Clicking a product row opens a modal/panel with a line chart of its daily price over time
- [x] X-axis: date (one point per day), Y-axis: price ($)
- [x] Show both regular price and sale price as separate lines when applicable
- [x] Support configurable date range: 7 days, 30 days, 90 days (default: 30)
- [x] Highlight sale periods on the chart (shaded regions or markers)
- [x] Display min/max/average price stats below the chart

**Backend API Endpoints:**
- [x] `GET /api/products/listing` — returns products grouped by store and category with current price
- [x] `GET /api/products/{id}/price-history?days=30` — implemented as `GET /api/reports/history/{productId}` in ReportController
- [ ] Ensure price history query uses daily aggregation (since price doesn't change within a day, return one record per day)
- [ ] Add MongoDB aggregation or query to group price records by date (`$dateToString` on `scrapedAt`)

**Controller & Service:**
- [x] Create `PriceListingController` or add endpoints to existing `ProductController`
- [x] Create service method to fetch all products with latest price, grouped by store → category
- [ ] Create service method to fetch daily price history for a single product (deduplicated by day)
- [x] Add DTO: `ProductListingResponse` (store groups → category groups → product + price details)
- [ ] Add DTO: `DailyPricePoint` (date, regularPrice, salePrice, onSale)

**Frontend Enhancements:**
- [x] Add search bar to filter products by name within the listing
- [x] Add sorting options: by name, by price (low-high / high-low), by discount %
- [ ] Responsive layout for mobile viewing
- [x] Lazy-load chart data only when a product is clicked (avoid loading all history upfront)

### 20. Performance Optimization
- [ ] Replace `new Thread()` in `ScrapeOrchestrationService` with `ExecutorService` thread pool
- [ ] Add MongoDB indexes for common queries (product search, price lookups)
- [ ] Implement batch inserts for price records during scraping
- [ ] Add connection pooling configuration for HTTP clients
- [ ] Profile and optimize slow scraper operations

### 21. Logging & Observability
- [ ] Standardize log format across all services
- [ ] Add correlation IDs for scrape jobs (trace through orchestration > scraper > matching > storage)
- [ ] Add structured logging (JSON format for log aggregation)
- [ ] Add audit logging for admin actions (trigger scrape, delete user)

### 22. Documentation
- [ ] Generate comprehensive API documentation with Swagger annotations (`@Operation`, `@ApiResponse`)
- [ ] Add request/response examples to Swagger
- [ ] Write deployment guide (manual + Docker)
- [ ] Document environment setup for contributors
- [ ] Add architecture diagram to readme

---

## Completed Features

- [x] T&T Supermarket scraper (GraphQL API, 6 categories)
- [x] Walmart Canada scraper (JSoup + JSON extraction, 45+ categories)
- [x] Real Canadian Superstore scraper (PC Express API, 5 categories)
- [x] PriceSmart Foods scraper (Playwright browser automation, 50+ categories)
- [x] Product CRUD endpoints
- [x] Category management
- [x] Price record storage
- [x] Price drop detection
- [x] Cross-store price comparison
- [x] Price history tracking
- [x] Current sales / discount report
- [x] Telegram bot integration (subscribe, notify, settings)
- [x] Data initializer with auto-fix logic
- [x] Rate limiting (Resilience4j)
- [x] Swagger/OpenAPI setup
- [x] MongoDB integration
- [x] Basic unit tests (6 test classes → expanded to 19 test classes, 230 tests passing)
- [x] Fix PriceSmart data not showing in discount API
- [x] Global exception handler with consistent JSON error responses