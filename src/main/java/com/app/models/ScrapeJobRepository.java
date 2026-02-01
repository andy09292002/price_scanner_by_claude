package com.app.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScrapeJobRepository extends MongoRepository<ScrapeJob, String> {

    List<ScrapeJob> findByStoreIdOrderByStartedAtDesc(String storeId);

    List<ScrapeJob> findByStatus(ScrapeJob.JobStatus status);

    Optional<ScrapeJob> findTopByStoreIdOrderByStartedAtDesc(String storeId);

    List<ScrapeJob> findByStoreIdAndStartedAtAfter(String storeId, LocalDateTime after);

    boolean existsByStoreIdAndStatus(String storeId, ScrapeJob.JobStatus status);
}
