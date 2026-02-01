package com.app.models;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRecordRepository extends MongoRepository<PriceRecord, String> {

    List<PriceRecord> findByProductIdOrderByScrapedAtDesc(String productId);

    List<PriceRecord> findByProductIdAndStoreIdOrderByScrapedAtDesc(String productId, String storeId);

    Optional<PriceRecord> findTopByProductIdAndStoreIdOrderByScrapedAtDesc(String productId, String storeId);

    List<PriceRecord> findByStoreIdAndScrapedAtAfter(String storeId, LocalDateTime after);

    @Query("{'onSale': true, 'scrapedAt': {$gte: ?0}}")
    Page<PriceRecord> findCurrentSales(LocalDateTime after, Pageable pageable);

    List<PriceRecord> findByProductIdAndScrapedAtBetween(
            String productId, LocalDateTime start, LocalDateTime end);

    @Query(value = "{'productId': ?0, 'storeId': ?1}", sort = "{'scrapedAt': -1}")
    List<PriceRecord> findLatestByProductIdAndStoreId(String productId, String storeId, Pageable pageable);

    List<PriceRecord> findByScrapedAtAfter(LocalDateTime after);
}
