package com.app.services;

import com.app.models.*;
import com.app.services.scraper.StoreScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapeOrchestrationServiceTest {

    @Mock
    private List<StoreScraper> scrapers;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PriceRecordRepository priceRecordRepository;

    @Mock
    private ScrapeJobRepository scrapeJobRepository;

    @Mock
    private ProductMatchingService productMatchingService;

    @Mock
    private PriceAnalysisService priceAnalysisService;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @InjectMocks
    private ScrapeOrchestrationService scrapeOrchestrationService;

    private Store testStore;
    private ScrapeJob testJob;

    @BeforeEach
    void setUp() {
        testStore = Store.builder()
                .name("Test Store")
                .code("TEST")
                .baseUrl("https://example.com")
                .active(true)
                .build();
        testStore.setId("store-123");

        testJob = ScrapeJob.builder()
                .storeId("store-123")
                .storeCode("TEST")
                .status(ScrapeJob.JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .errorMessages(new ArrayList<>())
                .build();
        testJob.setId("job-123");
    }

    @Test
    void triggerScrape_Success_CreatesJobAndStartsScraping() {
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(scrapeJobRepository.existsByStoreIdAndStatus("store-123", ScrapeJob.JobStatus.RUNNING))
                .thenReturn(false);
        when(scrapeJobRepository.save(any(ScrapeJob.class))).thenReturn(testJob);

        ScrapeJob result = scrapeOrchestrationService.triggerScrape("TEST");

        assertNotNull(result);
        assertEquals("TEST", result.getStoreCode());
        verify(scrapeJobRepository).save(any(ScrapeJob.class));
    }

    @Test
    void triggerScrape_StoreNotFound_ThrowsIllegalArgument() {
        when(storeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> scrapeOrchestrationService.triggerScrape("INVALID"));
    }

    @Test
    void triggerScrape_StoreInactive_ThrowsIllegalState() {
        testStore.setActive(false);
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));

        assertThrows(IllegalStateException.class,
                () -> scrapeOrchestrationService.triggerScrape("TEST"));
    }

    @Test
    void triggerScrape_JobAlreadyRunning_ThrowsIllegalState() {
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(scrapeJobRepository.existsByStoreIdAndStatus("store-123", ScrapeJob.JobStatus.RUNNING))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> scrapeOrchestrationService.triggerScrape("TEST"));
    }

    @Test
    void triggerScrapeAll_MultipleActiveStores_ReturnsJobs() {
        Store store2 = Store.builder()
                .name("Store 2")
                .code("S2")
                .active(true)
                .build();
        store2.setId("store-456");

        ScrapeJob job2 = ScrapeJob.builder()
                .storeId("store-456")
                .storeCode("S2")
                .status(ScrapeJob.JobStatus.PENDING)
                .errorMessages(new ArrayList<>())
                .build();
        job2.setId("job-456");

        when(storeRepository.findByActiveTrue()).thenReturn(List.of(testStore, store2));
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(storeRepository.findByCode("S2")).thenReturn(Optional.of(store2));
        when(scrapeJobRepository.existsByStoreIdAndStatus(anyString(), eq(ScrapeJob.JobStatus.RUNNING)))
                .thenReturn(false);
        when(scrapeJobRepository.save(any(ScrapeJob.class)))
                .thenReturn(testJob)
                .thenReturn(job2);

        List<ScrapeJob> result = scrapeOrchestrationService.triggerScrapeAll();

        assertEquals(2, result.size());
    }

    @Test
    void triggerScrapeAll_OneStoreFails_ContinuesOthers() {
        Store store2 = Store.builder()
                .name("Store 2")
                .code("S2")
                .active(true)
                .build();
        store2.setId("store-456");

        when(storeRepository.findByActiveTrue()).thenReturn(List.of(testStore, store2));
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(storeRepository.findByCode("S2")).thenReturn(Optional.of(store2));

        // First store has a running job (fails), second succeeds
        when(scrapeJobRepository.existsByStoreIdAndStatus("store-123", ScrapeJob.JobStatus.RUNNING))
                .thenReturn(true);
        when(scrapeJobRepository.existsByStoreIdAndStatus("store-456", ScrapeJob.JobStatus.RUNNING))
                .thenReturn(false);

        ScrapeJob job2 = ScrapeJob.builder()
                .storeId("store-456")
                .storeCode("S2")
                .status(ScrapeJob.JobStatus.PENDING)
                .errorMessages(new ArrayList<>())
                .build();
        when(scrapeJobRepository.save(any(ScrapeJob.class))).thenReturn(job2);

        List<ScrapeJob> result = scrapeOrchestrationService.triggerScrapeAll();

        assertEquals(1, result.size());
    }

    @Test
    void triggerScrapeAll_NoActiveStores_ReturnsEmpty() {
        when(storeRepository.findByActiveTrue()).thenReturn(List.of());

        List<ScrapeJob> result = scrapeOrchestrationService.triggerScrapeAll();

        assertTrue(result.isEmpty());
    }

    @Test
    void getJob_Found_ReturnsJob() {
        when(scrapeJobRepository.findById("job-123")).thenReturn(Optional.of(testJob));

        Optional<ScrapeJob> result = scrapeOrchestrationService.getJob("job-123");

        assertTrue(result.isPresent());
        assertEquals("job-123", result.get().getId());
    }

    @Test
    void getJob_NotFound_ReturnsEmpty() {
        when(scrapeJobRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<ScrapeJob> result = scrapeOrchestrationService.getJob("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void getLatestJob_Found_ReturnsJob() {
        when(storeRepository.findByCode("TEST")).thenReturn(Optional.of(testStore));
        when(scrapeJobRepository.findTopByStoreIdOrderByStartedAtDesc("store-123"))
                .thenReturn(Optional.of(testJob));

        Optional<ScrapeJob> result = scrapeOrchestrationService.getLatestJob("TEST");

        assertTrue(result.isPresent());
        assertEquals("TEST", result.get().getStoreCode());
    }

    @Test
    void getLatestJob_StoreNotFound_ReturnsEmpty() {
        when(storeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        Optional<ScrapeJob> result = scrapeOrchestrationService.getLatestJob("INVALID");

        assertTrue(result.isEmpty());
    }
}
