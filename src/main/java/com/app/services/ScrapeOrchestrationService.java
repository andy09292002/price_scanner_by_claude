package com.app.services;

import com.app.models.*;
import com.app.services.scraper.StoreScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

            // Scrape products
            List<StoreScraper.ScrapedProduct> scrapedProducts = scraper.scrapeAllProducts(store);
            job.setTotalProducts(scrapedProducts.size());

            log.info("Scraped {} products from {}", scrapedProducts.size(), store.getName());

            for (StoreScraper.ScrapedProduct scrapedProduct : scrapedProducts) {
                try {
                    processScrapedProduct(scrapedProduct, store);
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
            log.error("Scrape job failed for store: {}", store.getCode(), e);
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

        log.info("Scrape job completed for {}: {} success, {} errors",
                store.getCode(), successCount, errorCount);
    }

    private void processScrapedProduct(StoreScraper.ScrapedProduct scrapedProduct, Store store) {
        // Find or create product
        Product product = productMatchingService.findOrCreateProduct(scrapedProduct, store);

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
}
