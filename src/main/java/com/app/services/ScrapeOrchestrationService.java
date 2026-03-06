package com.app.services;

import com.app.models.*;
import com.app.services.scraper.StoreScraper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeOrchestrationService {

    private final List<StoreScraper> scrapers;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final ScrapeJobRepository scrapeJobRepository;
    private final ProductMatchingService productMatchingService;
    private final PriceAnalysisService priceAnalysisService;
    private final TelegramNotificationService telegramNotificationService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ScrapeJob triggerScrape(String storeCode) {
        Store store = storeRepository.findByCode(storeCode)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeCode));

        if (!store.isActive()) {
            throw new IllegalStateException("Store is not active: " + storeCode);
        }

        // Check if there's already a running job for this store
        if (scrapeJobRepository.existsByStoreIdAndStatus(store.getId(), ScrapeJob.JobStatus.RUNNING)) {
            throw new IllegalStateException("A scrape job is already running for store: " + storeCode);
        }

        // Create job record
        ScrapeJob job = ScrapeJob.builder()
                .storeId(store.getId())
                .storeCode(storeCode)
                .status(ScrapeJob.JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .errorMessages(new ArrayList<>())
                .build();
        job = scrapeJobRepository.save(job);

        // Start scraping asynchronously
        final ScrapeJob finalJob = job;
        new Thread(() -> executeScrape(finalJob, store)).start();

        return job;
    }

    public List<ScrapeJob> triggerScrapeAll() {
        List<ScrapeJob> jobs = new ArrayList<>();
        List<Store> activeStores = storeRepository.findByActiveTrue();

        for (Store store : activeStores) {
            try {
                ScrapeJob job = triggerScrape(store.getCode());
                jobs.add(job);
            } catch (Exception e) {
                log.error("Failed to start scrape for store: {}", store.getCode(), e);
            }
        }

        return jobs;
    }

    private void executeScrape(ScrapeJob job, Store store) {
        job.setStatus(ScrapeJob.JobStatus.RUNNING);
        scrapeJobRepository.save(job);

        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        List<PriceRecord> previousPrices = new ArrayList<>();

        try {
            // Find appropriate scraper
            StoreScraper scraper = scrapers.stream()
                    .filter(s -> s.supports(store))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No scraper found for store: " + store.getCode()));

            // Get previous prices for comparison
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            previousPrices = priceRecordRepository.findByStoreIdAndScrapedAtAfter(store.getId(), oneDayAgo);

            // Scrape products — wrapped in a per-store circuit breaker
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(store.getCode());
            List<StoreScraper.ScrapedProduct> scrapedProducts;
            try {
                scrapedProducts = circuitBreaker.executeSupplier(() -> scraper.scrapeAllProducts(store));
            } catch (CallNotPermittedException e) {
                log.warn("[{}] Circuit breaker OPEN — scrape skipped. Store may be temporarily unavailable.", store.getCode());
                errors.add("Circuit breaker open: scrape skipped. Store temporarily unavailable after repeated failures.");
                job.setStatus(ScrapeJob.JobStatus.FAILED);
                job.setErrorMessages(errors);
                job.setCompletedAt(LocalDateTime.now());
                scrapeJobRepository.save(job);
                return;
            }

            job.setTotalProducts(scrapedProducts.size());
            log.info("[{}] Scraped {} products", store.getCode(), scrapedProducts.size());

            Set<String> processedProductStoreKeys = new HashSet<>();
            for (StoreScraper.ScrapedProduct scrapedProduct : scrapedProducts) {
                try {
                    processScrapedProduct(scrapedProduct, store, processedProductStoreKeys);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    String errorMsg = "Error processing product: " + scrapedProduct.name() + " - " + e.getMessage();
                    errors.add(errorMsg);
                    log.warn(errorMsg);
                }
            }

            job.setStatus(ScrapeJob.JobStatus.COMPLETED);

        } catch (Exception e) {
            log.error("[{}] Scrape job failed: {}", store.getCode(), e.getMessage(), e);
            job.setStatus(ScrapeJob.JobStatus.FAILED);
            errors.add("Job failed: " + e.getMessage());
        }

        job.setSuccessCount(successCount);
        job.setErrorCount(errorCount);
        job.setErrorMessages(errors);
        job.setCompletedAt(LocalDateTime.now());
        scrapeJobRepository.save(job);

        // After scraping, detect price drops and send notifications
        if (job.getStatus() == ScrapeJob.JobStatus.COMPLETED) {
            try {
                List<PriceAnalysisService.PriceDrop> priceDrops =
                        priceAnalysisService.detectPriceDrops(store.getId(), previousPrices);
                if (!priceDrops.isEmpty()) {
                    telegramNotificationService.sendPriceDropNotifications(priceDrops);
                }
            } catch (Exception e) {
                log.error("Error sending price drop notifications", e);
            }
        }

        log.info("[{}] Scrape job finished: {} success, {} errors", store.getCode(), successCount, errorCount);
    }

    private void processScrapedProduct(StoreScraper.ScrapedProduct scrapedProduct, Store store,
                                       Set<String> processedProductStoreKeys) {
        // Find or create product
        Product product = productMatchingService.findOrCreateProduct(scrapedProduct, store);

        // Skip duplicate price record if this product+store was already processed in this session
        String key = product.getId() + "_" + store.getId();
        if (!processedProductStoreKeys.add(key)) {
            log.debug("Skipping duplicate price record for product {} in store {}", product.getName(), store.getCode());
            return;
        }

        // Create price record
        PriceRecord priceRecord = PriceRecord.builder()
                .productId(product.getId())
                .storeId(store.getId())
                .regularPrice(scrapedProduct.regularPrice())
                .salePrice(scrapedProduct.salePrice())
                .unitPrice(scrapedProduct.unitPrice())
                .onSale(scrapedProduct.onSale())
                .promoDescription(scrapedProduct.promoDescription())
                .scrapedAt(LocalDateTime.now())
                .inStock(scrapedProduct.inStock())
                .sourceUrl(scrapedProduct.sourceUrl())
                .build();

        priceRecordRepository.save(priceRecord);
    }

    public Optional<ScrapeJob> getJob(String jobId) {
        return scrapeJobRepository.findById(jobId);
    }

    public Optional<ScrapeJob> getLatestJob(String storeCode) {
        return storeRepository.findByCode(storeCode)
                .flatMap(store -> scrapeJobRepository.findTopByStoreIdOrderByStartedAtDesc(store.getId()));
    }

    public List<ScraperMetrics> getAllScraperMetrics() {
        List<Store> activeStores = storeRepository.findByActiveTrue();
        return activeStores.stream()
                .map(store -> buildMetrics(store.getCode()))
                .toList();
    }

    public ScraperMetrics getScraperMetrics(String storeCode) {
        storeRepository.findByCode(storeCode)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeCode));
        return buildMetrics(storeCode);
    }

    private ScraperMetrics buildMetrics(String storeCode) {
        List<ScrapeJob> jobs = scrapeJobRepository.findByStoreCode(storeCode);
        int total = jobs.size();
        int successful = (int) jobs.stream()
                .filter(j -> j.getStatus() == ScrapeJob.JobStatus.COMPLETED).count();
        int failed = (int) jobs.stream()
                .filter(j -> j.getStatus() == ScrapeJob.JobStatus.FAILED).count();
        double successRate = total > 0 ? (double) successful / total * 100.0 : 0.0;

        LocalDateTime lastSuccessAt = jobs.stream()
                .filter(j -> j.getStatus() == ScrapeJob.JobStatus.COMPLETED && j.getCompletedAt() != null)
                .map(ScrapeJob::getCompletedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime lastJobAt = jobs.stream()
                .filter(j -> j.getStartedAt() != null)
                .map(ScrapeJob::getStartedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String circuitBreakerState;
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(storeCode);
            circuitBreakerState = cb.getState().name();
        } catch (Exception e) {
            circuitBreakerState = "UNKNOWN";
        }

        return new ScraperMetrics(storeCode, total, successful, failed,
                Math.round(successRate * 10.0) / 10.0, lastSuccessAt, lastJobAt, circuitBreakerState);
    }

    public record ScraperMetrics(
            String storeCode,
            int totalJobs,
            int successfulJobs,
            int failedJobs,
            double successRate,
            LocalDateTime lastSuccessAt,
            LocalDateTime lastJobAt,
            String circuitBreakerState) {}
}
