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