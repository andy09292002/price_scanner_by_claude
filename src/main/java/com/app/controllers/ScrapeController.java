package com.app.controllers;

import com.app.models.ScrapeJob;
import com.app.services.ScrapeOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
@Tag(name = "Scrape", description = "Endpoints for triggering and monitoring price scraping jobs")
public class ScrapeController {

    private final ScrapeOrchestrationService scrapeOrchestrationService;

    @PostMapping("/trigger/{storeCode}")
    @Operation(summary = "Trigger scrape for one store",
               description = "Starts a scraping job for the specified store. Returns immediately with job info.")
    public ResponseEntity<ScrapeJob> triggerScrape(
                @Parameter(description = "Store code (RCSS, WALMART, PRICESMART, TNT)")
            @PathVariable String storeCode) {

        log.info("Triggering scrape for store: {}", storeCode);
        ScrapeJob job = scrapeOrchestrationService.triggerScrape(storeCode.toUpperCase());
        return ResponseEntity.accepted().body(job);
    }

    @PostMapping("/trigger/all")
    @Operation(summary = "Trigger scrape for all stores",
               description = "Starts scraping jobs for all active stores.")
    public ResponseEntity<List<ScrapeJob>> triggerScrapeAll() {
        log.info("Triggering scrape for all stores");
        List<ScrapeJob> jobs = scrapeOrchestrationService.triggerScrapeAll();
        return ResponseEntity.accepted().body(jobs);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get job status",
               description = "Returns the current status and details of a scrape job.")
    public ResponseEntity<ScrapeJob> getJobStatus(
            @Parameter(description = "Job ID")
            @PathVariable String jobId) {

        return scrapeOrchestrationService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/latest/{storeCode}")
    @Operation(summary = "Get latest job for store",
               description = "Returns the most recent scrape job for a store.")
    public ResponseEntity<ScrapeJob> getLatestJob(
            @Parameter(description = "Store code")
            @PathVariable String storeCode) {

        return scrapeOrchestrationService.getLatestJob(storeCode.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
