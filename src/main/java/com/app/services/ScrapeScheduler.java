package com.app.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapeScheduler {

    private final ScrapeOrchestrationService scrapeOrchestrationService;

    @Scheduled(cron = "${scraper.schedule.cron}")
    public void scheduledScrapeAll() {
        log.info("Starting scheduled scrape for all stores");
        try {
            var jobs = scrapeOrchestrationService.triggerScrapeAll();
            log.info("Scheduled scrape triggered {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("Scheduled scrape failed", e);
        }
    }
}
