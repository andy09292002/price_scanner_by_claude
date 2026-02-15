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

### 2. Implement Global Exception Handler
- [ ] Create `@ControllerAdvice` class (`GlobalExceptionHandler`)
- [ ] Define standard error response DTO (`ErrorResponse` with timestamp, status, message, path)
- [ ] Handle common exceptions:
  - `ResourceNotFoundException` (404)
  - `MethodArgumentNotValidException` (400)
  - `IllegalArgumentException` (400)
  - `HttpRequestMethodNotSupportedException` (405)
  - Generic `Exception` fallback (500)
- [ ] Ensure consistent JSON error format across all endpoints
- [ ] Log exceptions properly with stack traces at appropriate levels

**Why:** No centralized error handling exists. Controllers return inconsistent error responses or raw Spring error pages.

### 3. Add Input Validation
- [ ] Add Bean Validation annotations to `User` model (`@NotBlank`, `@Email`, `@Size`)
- [ ] Add validation to `TelegramSubscription` model (`@NotNull` on chatId, `@Min`/`@Max` on thresholds)
- [ ] Add `@Valid` annotation on all `@RequestBody` parameters across controllers
- [ ] Validate path variables and request parameters (`@Positive`, `@NotBlank`)
- [ ] Add validation to `ProductController` search parameters (page size limits, sort field whitelist)
- [ ] Add validation to `ReportController` parameters (minDiscountPercentage range)

**Why:** Only `UserController.createUser()` uses `@Valid`, and even the `User` model has no validation annotations. Invalid data can enter the system freely.

---

## P1 - High Priority

### 4. Increase Unit Test Coverage to 80%
Current coverage: ~30%. Target: 80% per CLAUDE.md.

**Missing controller tests:**
- [ ] `ProductControllerTest` - search products, get by ID, list categories, products by category
- [ ] `TelegramControllerTest` - subscribe, update settings, unsubscribe, test message

**Missing service tests:**
- [ ] `ScrapeOrchestrationServiceTest` - job lifecycle, concurrent job prevention, notification triggering
- [ ] `TelegramNotificationServiceTest` - bot commands, message sending, subscription management, message splitting

**Missing scraper tests:**
- [ ] `WalmartScraperTest` - JSON extraction, HTML fallback, price parsing (dollar + cents formats), pagination
- [ ] `TntScraperTest` - GraphQL response parsing, category mapping, pagination
- [ ] `SuperstoreScraperTest` - API response parsing, component filtering, unit price extraction
- [ ] `AbstractStoreScraperTest` - size/unit extraction, price parsing regex, non-breaking space handling

**Improve existing tests:**
- [ ] `PriceAnalysisServiceTest` - add edge cases for discount calculation, empty results, date boundaries
- [ ] `ProductMatchingServiceTest` - add multi-store matching, deduplication, category creation edge cases
- [ ] `PriceSmartScraperTest` - add more DOM parsing scenarios, error handling cases

**Other:**
- [ ] `DataInitializerTest` - store seeding, category auto-fix, product auto-fix
- [ ] `RateLimiterConfigurationTest` - rate limiter bean creation

### 5. Add Scheduled Scraping
- [ ] Add `@EnableScheduling` to application config
- [ ] Create `ScrapingScheduler` service with `@Scheduled` methods
- [ ] Configure cron expressions per store (e.g., daily at 6 AM)
- [ ] Make schedules configurable via `application.properties`
- [ ] Add lock mechanism to prevent overlapping scheduled runs
- [ ] Log scheduling events (start, complete, skip)

**Why:** Scraping is manual-only via HTTP endpoints. No automated background jobs exist.

### 6. Fix Telegram `/deals` Command
- [ ] Inject `PriceAnalysisService` into `TelegramNotificationService`
- [ ] Fetch actual current deals/price drops when `/deals` is invoked
- [ ] Format deals as a readable Telegram message (store, product, old price, new price, % off)
- [ ] Respect subscriber's store and category filters
- [ ] Handle empty results gracefully
- [ ] Apply the 4096 character message splitting logic

**Why:** Currently returns a hardcoded string: `"Fetching current deals...\n\nVisit /api/reports/price-drops for the latest deals."` — no actual data is fetched.

### 7. Scraper Error Resilience
- [ ] Add retry logic with exponential backoff for HTTP failures (network timeouts, 5xx responses)
- [ ] Add circuit breaker pattern per store (Resilience4j `@CircuitBreaker`)
- [ ] Handle store website structure changes gracefully (log warnings, don't crash)
- [ ] Add timeout configuration per scraper (currently hardcoded 30s)
- [ ] Add fallback responses when scraper fails (return partial results instead of nothing)
- [ ] Track and report scraper success/failure rates per store

---

## P2 - Medium Priority

### 8. Docker & Containerization
- [ ] Create `Dockerfile` (multi-stage build: Maven build + JRE runtime)
- [ ] Create `docker-compose.yml` with services:
  - Application (Spring Boot)
  - MongoDB
  - (Optional) Redis for caching
- [ ] Add `.dockerignore` file
- [ ] Document environment variable configuration for Docker
- [ ] Ensure Playwright works in container (install browser dependencies)

### 9. CI/CD Pipeline
- [ ] Create `.github/workflows/ci.yml`:
  - Run on push/PR to main
  - Build with Maven
  - Run unit tests
  - Report test coverage
  - Fail if coverage < 80%
- [ ] (Optional) Add deployment workflow for staging/production
- [ ] Add badge to readme.md for build status

### 10. Health Check & Monitoring
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

### 11. API Caching
- [ ] Add caching for frequently accessed endpoints:
  - `GET /api/products` (search results)
  - `GET /api/products/categories` (category list)
  - `GET /api/reports/sales` (current sales)
- [ ] Use Spring `@Cacheable` with TTL configuration
- [ ] Invalidate cache after scrape job completion
- [ ] Consider Redis as cache backend for multi-instance deployments

### 12. Configuration Validation
- [ ] Validate required environment variables on startup (fail fast if missing):
  - `MONGODB_URI`
  - `TELEGRAM_BOT_TOKEN`
  - `TELEGRAM_BOT_USERNAME`
  - `SUPERSTORE_API_KEY`
- [ ] Remove placeholder defaults (`your-bot-token-here`) from `application.properties`
- [ ] Add `.env.example` file documenting all required variables
- [ ] Ensure `.env` is in `.gitignore`

### 13. Product Deduplication
- [ ] Improve product name normalization (handle brand variations, size formats)
- [ ] Add deduplication job to merge duplicate products
- [ ] Handle cross-store product matching more accurately
- [ ] Add admin endpoint to manually merge/split products

### 14. Historical Data Management
- [ ] Add TTL index on `price_records.scrapedAt` for automatic pruning (e.g., keep 90 days)
- [ ] Create aggregated historical data (weekly/monthly averages) before pruning detailed records
- [ ] Add endpoint for price trend analysis (7-day, 30-day, 90-day)

---

## P3 - Nice to Have

### 15. API Enhancements
- [ ] Add advanced search filters (price range, on-sale only, specific stores)
- [ ] Add user watchlist feature (track specific products, get alerts)
- [ ] Add bulk product query endpoint
- [ ] Add category hierarchy/tree endpoint
- [ ] Add pagination metadata (total count, total pages) to all list endpoints

### 16. Telegram Bot Enhancements
- [ ] Add `/watchlist` command to manage product watchlist
- [ ] Add `/compare` command to compare prices across stores
- [ ] Add inline keyboard for settings (store/category toggles)
- [ ] Add message scheduling (daily/weekly deal summaries)
- [ ] Add timezone support for scheduled messages

### 17. Frontend / Dashboard
- [ ] Build a web dashboard (JSP or separate SPA)
- [ ] Product search and browse UI
- [ ] Price comparison charts
- [ ] Price history graphs
- [ ] Store-by-store deal listings
- [ ] Admin panel for scrape management

### 18. Performance Optimization
- [ ] Replace `new Thread()` in `ScrapeOrchestrationService` with `ExecutorService` thread pool
- [ ] Add MongoDB indexes for common queries (product search, price lookups)
- [ ] Implement batch inserts for price records during scraping
- [ ] Add connection pooling configuration for HTTP clients
- [ ] Profile and optimize slow scraper operations

### 19. Logging & Observability
- [ ] Standardize log format across all services
- [ ] Add correlation IDs for scrape jobs (trace through orchestration > scraper > matching > storage)
- [ ] Add structured logging (JSON format for log aggregation)
- [ ] Add audit logging for admin actions (trigger scrape, delete user)

### 20. Documentation
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
- [x] Basic unit tests (6 test classes)