package com.app.controllers;

import com.app.exceptions.ResourceNotFoundException;
import com.app.models.ScrapeJob;
import com.app.services.ScrapeOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
@Validated
@Tag(name = "Scrape", description = "Endpoints for triggering and monitoring price scraping jobs")
public class ScrapeController {

    private final ScrapeOrchestrationService scrapeOrchestrationService;

    @PostMapping("/trigger/{storeCode}")
    @Operation(summary = "Trigger scrape for one store",
               description = "Starts a scraping job for the specified store. Returns immediately with job info.")
    public ResponseEntity<ScrapeJob> triggerScrape(
                @Parameter(description = "Store code (RCSS, WALMART, PRICESMART, TNT)")
            @PathVariable @NotBlank(message = "Store code must not be blank") String storeCode) {

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
            @PathVariable @NotBlank(message = "Job ID must not be blank") String jobId) {

        ScrapeJob job = scrapeOrchestrationService.getJob(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Scrape job not found with id: " + jobId));
        return ResponseEntity.ok(job);
    }

    @GetMapping("/jobs/latest/{storeCode}")
    @Operation(summary = "Get latest job for store",
               description = "Returns the most recent scrape job for a store.")
    public ResponseEntity<ScrapeJob> getLatestJob(
            @Parameter(description = "Store code")
            @PathVariable @NotBlank(message = "Store code must not be blank") String storeCode) {

        ScrapeJob job = scrapeOrchestrationService.getLatestJob(storeCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("No scrape job found for store: " + storeCode.toUpperCase()));
        return ResponseEntity.ok(job);
    }
}
