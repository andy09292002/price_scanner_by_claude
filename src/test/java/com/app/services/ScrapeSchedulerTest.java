package com.app.services;

import com.app.models.ScrapeJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapeSchedulerTest {

    @Mock
    private ScrapeOrchestrationService scrapeOrchestrationService;

    @InjectMocks
    private ScrapeScheduler scrapeScheduler;

    @Test
    void scheduledScrapeAll_callsTriggerScrapeAll() {
        List<ScrapeJob> jobs = new ArrayList<>();
        jobs.add(ScrapeJob.builder().storeCode("test").build());
        when(scrapeOrchestrationService.triggerScrapeAll()).thenReturn(jobs);

        scrapeScheduler.scheduledScrapeAll();

        verify(scrapeOrchestrationService, times(1)).triggerScrapeAll();
    }

    @Test
    void scheduledScrapeAll_handlesEmptyResult() {
        when(scrapeOrchestrationService.triggerScrapeAll()).thenReturn(new ArrayList<>());

        scrapeScheduler.scheduledScrapeAll();

        verify(scrapeOrchestrationService, times(1)).triggerScrapeAll();
    }

    @Test
    void scheduledScrapeAll_handlesException_doesNotPropagate() {
        when(scrapeOrchestrationService.triggerScrapeAll()).thenThrow(new RuntimeException("DB connection failed"));

        // Should not throw — scheduler must not crash
        scrapeScheduler.scheduledScrapeAll();

        verify(scrapeOrchestrationService, times(1)).triggerScrapeAll();
    }
}
