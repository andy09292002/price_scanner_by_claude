package com.app.controllers;

import com.app.models.ScrapeJob;
import com.app.services.ScrapeOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapeControllerTest {

    @Mock
    private ScrapeOrchestrationService scrapeOrchestrationService;

    @InjectMocks
    private ScrapeController scrapeController;

    private ScrapeJob testJob;

    @BeforeEach
    void setUp() {
        testJob = ScrapeJob.builder()
                .storeId("store-123")
                .storeCode("RCSS")
                .status(ScrapeJob.JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        testJob.setId("job-123");
    }

    @Test
    void triggerScrape_Success() {
        when(scrapeOrchestrationService.triggerScrape("RCSS")).thenReturn(testJob);

        ResponseEntity<ScrapeJob> response = scrapeController.triggerScrape("RCSS");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RCSS", response.getBody().getStoreCode());
        verify(scrapeOrchestrationService).triggerScrape("RCSS");
    }

    @Test
    void triggerScrape_LowercaseStoreCode_ConvertsToUppercase() {
        when(scrapeOrchestrationService.triggerScrape("RCSS")).thenReturn(testJob);

        ResponseEntity<ScrapeJob> response = scrapeController.triggerScrape("rcss");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(scrapeOrchestrationService).triggerScrape("RCSS");
    }

    @Test
    void triggerScrape_StoreNotFound_ThrowsException() {
        when(scrapeOrchestrationService.triggerScrape("INVALID"))
                .thenThrow(new IllegalArgumentException("Store not found: INVALID"));

        assertThrows(IllegalArgumentException.class,
                () -> scrapeController.triggerScrape("INVALID"));
    }

    @Test
    void triggerScrape_JobAlreadyRunning_ThrowsException() {
        when(scrapeOrchestrationService.triggerScrape("RCSS"))
                .thenThrow(new IllegalStateException("A scrape job is already running"));

        assertThrows(IllegalStateException.class,
                () -> scrapeController.triggerScrape("RCSS"));
    }

    @Test
    void triggerScrapeAll_Success() {
        ScrapeJob job2 = ScrapeJob.builder()
                .storeCode("WALMART")
                .status(ScrapeJob.JobStatus.PENDING)
                .build();
        List<ScrapeJob> jobs = Arrays.asList(testJob, job2);

        when(scrapeOrchestrationService.triggerScrapeAll()).thenReturn(jobs);

        ResponseEntity<List<ScrapeJob>> response = scrapeController.triggerScrapeAll();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void triggerScrapeAll_NoActiveStores_ReturnsEmptyList() {
        when(scrapeOrchestrationService.triggerScrapeAll()).thenReturn(List.of());

        ResponseEntity<List<ScrapeJob>> response = scrapeController.triggerScrapeAll();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getJobStatus_Found() {
        when(scrapeOrchestrationService.getJob("job-123")).thenReturn(Optional.of(testJob));

        ResponseEntity<ScrapeJob> response = scrapeController.getJobStatus("job-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().getId());
    }

    @Test
    void getJobStatus_NotFound() {
        when(scrapeOrchestrationService.getJob("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<ScrapeJob> response = scrapeController.getJobStatus("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getLatestJob_Found() {
        testJob.setStatus(ScrapeJob.JobStatus.COMPLETED);
        when(scrapeOrchestrationService.getLatestJob("RCSS")).thenReturn(Optional.of(testJob));

        ResponseEntity<ScrapeJob> response = scrapeController.getLatestJob("RCSS");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ScrapeJob.JobStatus.COMPLETED, response.getBody().getStatus());
    }

    @Test
    void getLatestJob_NotFound() {
        when(scrapeOrchestrationService.getLatestJob("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<ScrapeJob> response = scrapeController.getLatestJob("UNKNOWN");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getLatestJob_LowercaseStoreCode_ConvertsToUppercase() {
        when(scrapeOrchestrationService.getLatestJob("WALMART")).thenReturn(Optional.of(testJob));

        scrapeController.getLatestJob("walmart");

        verify(scrapeOrchestrationService).getLatestJob("WALMART");
    }
}
